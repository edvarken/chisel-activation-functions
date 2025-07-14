package silu
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage // needed for ChiselStage.emitSystemVerilogFile

/**
  * This is a Chisel implementation that uses a Lookup Table to approximate the SiLU activation function within a certain range
  * If intBits=2, the range is -4 to +4.
  * If intBits=3, the range is -8 to +8.
  * the amount of fracBits determines the precision within that range.
  * For smaller and large numbers outside the range, the function returns 0 or the input itself respectively.
  * The implementation only supports BF16 floating point representation
  */
class siluUsingLUT(val intBits: Int = 2, val fracBits: Int = 4) extends Module {
    val io = IO(new Bundle {
        val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
        val out_a = Output(Bits(16.W))
    })

    val a = io.in_a // a must be a BigInt
    val sign = a(15).asUInt
    val exp = a(14,7).asUInt
    val actual_exp = (exp.asSInt - 127.S(8.W)).asSInt // actual_exp can be negative!

    val lut = Module(new siluLUT(intBits, fracBits)) // LUT for the values between -4 and 4 (or -8 and 8)
    val bf16tofp = Module(new BF16toFP(intBits, fracBits)) // BF16 to Fixed Point converter, e.g., 2 bits for integer part and 4 bits for fractional part

    bf16tofp.io.bf16in := a
    val a_int = bf16tofp.io.intout 
    val a_frac = bf16tofp.io.fracout
    val a_sign = bf16tofp.io.signout
    val index = Cat(a_sign, a_int, a_frac) // create the index
    lut.io.indexIn := index // LUT input
    val lutValue = lut.io.valueOut
    val outputReg = RegInit(0.U(16.W)) // register for the output

    when (sign === 1.U) { // in_a <= -0
        when (a === "b1_00000000_0000000".U) { // in_a = -0
            outputReg := 0.U
        }.elsewhen (actual_exp >= intBits.S) { // if intBits=2: in_a <= -4; if intBits=3: in_a <= -8
            outputReg := 0.U
        }.otherwise { // if intBits=2: -4 < in_a <= -0; if intBits=3: -8 < in_a <= -0
            outputReg := lutValue
        }

    }.otherwise { // in_a > 0
        when (a === "b0_00000000_0000000".U) { // in_a = +0
            outputReg := 0.U
        }.elsewhen (actual_exp >= intBits.S) { // if intBits=2: in_a >= 4; if intBits=3: in_a >= 8
            outputReg := a // SiLU(a) = a
        }.otherwise { // if intBits=2: 0 < in_a < 4; if intBits=3: 0 < in_a < 8
            outputReg := lutValue
        }
    }
    io.out_a := outputReg // output the result
}

/**
 * Generate Verilog sources and save it in generated/siluUsingLUT.v
 * Uncomment to generate the SystemVerilog file when using 'sbt run'
 * Change intBits,fracBits to generate for other configurations
 */
object siluUsingLUTMain extends App {
    ChiselStage.emitSystemVerilogFile(
        new siluUsingLUT(intBits = 3, fracBits = 6), // Change intBits and fracBits as needed
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated2")
    )
}
