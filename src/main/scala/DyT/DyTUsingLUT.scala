package DyT
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage
import silu.FPMult16
import silu.BF16toFP

/**
  * This is a Chisel implementation that uses a Lookup Table to approximate the DyT activation function between -4 and +4
  * Below -4 and above +4, the function returns -1 and 1 respectively.
  * The implementation only supports BF16 floating point representation
  */
class DyTUsingLUT extends Module {
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
    val fpmult1 = Module(new FPMult16) // 1 clock cycle latency
    fpmult1.io.a := a
    fpmult1.io.b := alpha
    val tanh_input = fpmult1.io.res // a mantissa_rounder is used in the FPMult16 module
    io.debug_out_tanh_input := tanh_input // debugging output

    val sign = tanh_input(15).asUInt
    val exp = tanh_input(14,7).asUInt
    val actual_exp = (exp.asSInt - 127.S(8.W)).asSInt // actual_exp can be negative!
    val mantissa = tanh_input(6,0).asUInt

    val lut = Module(new DyTLUT) // LUT for the values between -4 and 4
    val bf16tofp = Module(new BF16toFP(2, 4)) // BF16 to FP converter, 2 bits for integer part and 4 bits for fractional part

    bf16tofp.io.bf16in := tanh_input // bf16tofp is purely combinatorial
    val fixedpointIntReg = RegInit(0.U(2.W)) // register for the Int part of the FixedPoint representation
    val fixedpointFracReg = RegInit(0.U(4.W)) // register for the Frac part of the FixedPoint representation
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
        }.elsewhen (actual_exp >= 2.S) { // tanh_input <= -4
            outputReg := "b1_01111111_0000000".U // -1
        }.otherwise { // -4 < tanh_input <= -0
            outputReg := lutValue
        }

    }.otherwise { // tanh_input > 0
        when (tanh_input === "b0_00000000_0000000".U) { // tanh_input = +0
            outputReg := 0.U
        }.elsewhen (actual_exp >= 2.S) { // tanh_input >= 4
            outputReg := "b0_01111111_0000000".U // +1
        }.otherwise { // 0 < tanh_input < 4
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
        new DyTUsingLUT,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated")
    )
}