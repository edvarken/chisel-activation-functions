package DyT
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage
import silu.FPMult16ALT
import silu.BF16toFP

/**
  * This is a Chisel implementation that uses a Lookup Table to approximate the DyT activation function within a certain range
  * If intBits=2 and fracBits=4, the range is -4 to +4
  * If intBits=3 and fracBits=4, the range is -8 to +8
  * Below and above the range, the function returns -1 and 1
  * The implementation only supports BF16 floating point representation
  */
class DyTUsingLUT(val intBits: Int = 2, val fracBits: Int = 4) extends Module {
    val io = IO(new Bundle {
        val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
        val in_alpha = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
        val out_a = Output(Bits(16.W))
        // debugging outputs
        val debug_out_tanh_input = Output(Bits(16.W))
    })

    val a = io.in_a // a must be a BigInt
    val alpha = io.in_alpha // trainable 'weight' value, different for each pixel of a 'layernorm replacement'
    // multiply alpha * a
    val fpmult1 = Module(new FPMult16ALT) // 1 clock cycle latency
    fpmult1.io.a := a
    fpmult1.io.b := alpha
    val tanh_input = fpmult1.io.res // a mantissa_rounder is used in the FPMult16ALT module
    io.debug_out_tanh_input := tanh_input // debugging output

    val sign = tanh_input(15).asUInt
    val exp = tanh_input(14,7).asUInt
    val actual_exp = (exp.asSInt - 127.S(8.W)).asSInt // actual_exp can be negative!
    val mantissa = tanh_input(6,0).asUInt

    val lut = Module(new DyTLUT(intBits, fracBits)) // LUT for the values between -4 and 4 (or -8 and 8)
    val bf16tofp = Module(new BF16toFP(intBits, fracBits)) // BF16 to FP converter, (e.g. 2 bits for integer part and 4 bits for fractional part)

    bf16tofp.io.bf16in := tanh_input // bf16tofp is purely combinatorial
    val fixedpointIntReg = RegInit(0.U(intBits.W)) // register for the Int part of the FixedPoint representation
    val fixedpointFracReg = RegInit(0.U(fracBits.W)) // register for the Frac part of the FixedPoint representation
    val fixedpointSignReg = RegInit(0.U(1.W)) // register for the Sign part of the FixedPoint representation
    fixedpointIntReg := bf16tofp.io.intout 
    fixedpointFracReg := bf16tofp.io.fracout
    fixedpointSignReg := bf16tofp.io.signout
    val tanh_input_int = fixedpointIntReg
    val tanh_input_frac = fixedpointFracReg
    val tanh_input_sign = fixedpointSignReg
    val index = Cat(tanh_input_sign, tanh_input_int, tanh_input_frac) // create the index
    lut.io.indexIn := index // LUT input
    val lutValue = lut.io.valueOut
    val outputReg = RegInit(0.U(16.W)) // register for the output

    when (sign === 1.U) { // tanh_input <= -0
        when (tanh_input === "b1_00000000_0000000".U) { // tanh_input = -0
            outputReg := 0.U
        }.elsewhen (actual_exp >= intBits.S) { // if intBits=2: tanh_input <= -4; if intBits=3: tanh_input <= -8
            outputReg := "b1_01111111_0000000".U // -1
        }.otherwise { // if intBits=2: -4 < tanh_input <= -0; if intBits=3: -8 < tanh_input <= -0
            outputReg := lutValue
        }

    }.otherwise { // tanh_input > 0
        when (tanh_input === "b0_00000000_0000000".U) { // tanh_input = +0
            outputReg := 0.U
        }.elsewhen (actual_exp >= intBits.S) { // tanh_input >= 4
            outputReg := "b0_01111111_0000000".U // +1
        }.otherwise { // if intBits=2: 0 < tanh_input < 4; if intBits=3: 0 < tanh_input < 8
            outputReg := lutValue
        }
    }
    io.out_a := outputReg // output the result
}

/**
 * Generate Verilog sources and save it in generated/DyTUsingLUT.v
 */
object DyTUsingLUT extends App {
    ChiselStage.emitSystemVerilogFile(
        new DyTUsingLUT(intBits = 2, fracBits = 4),
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated")
    )
}