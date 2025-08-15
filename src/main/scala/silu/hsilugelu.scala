package silu
import chisel3._
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage

/**
  * This is a Chisel implementation of an approximation of the SiLU activation function, which is defined as:
  * silu(x) ~ x * relu6(x+3) / 6
  * and the GELU activation function, which is defined as:
  * gelu(x) ~ x * relu6(1.702*x+3) / 6
  * The implementation only supports BF16 numbers
  */
class hsilugelu extends Module {
  val io = IO(new Bundle {
    val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
    val in_select = Input(UInt(1.W)) // 0 for SiLU, 1 for GELU
    val out_a = Output(Bits(16.W))
  })

  // we could also bypass all this by just returning 0 if input < -3 or returning input itself if input > 3
  // This could be done in 0 clock cycles instead of 5 clock cycles, however the latency of silu would be unpredictable then since latency then depends on the input value.
  // This implementation has a fixed latency of 5 clock cycles.
  val a = io.in_a
  val fpmult0 = Module(new FPMult16ALT) // 1cc for product = a*1 or a*1.703125
  fpmult0.io.a := a
  val product = fpmult0.io.res
  when (io.in_select === 0.U) { // SiLU
      fpmult0.io.b := "b0_01111111_0000000".U(16.W) // 1.0
  }.otherwise { // GELU
      fpmult0.io.b := "b0_01111111_1011010".U(16.W) // 1.703125
  }

  // product + 3
  val fpadd = Module(new FPAdd16ALT) // 3 clock cycles latency
  fpadd.io.a := product
  fpadd.io.b := "b0_10000000_1000000".U(16.W) // 3.0 = 1.5 * 2^1 
  val res1 = fpadd.io.res 

  // relu6(product + 3)
  val relu6 = Module(new relu6) // 0 clock cycles latency
  relu6.io.in_a := res1
  val relu6out = relu6.io.out_a

  // a * relu6(product + 3)
  val fpmult1 = Module(new FPMult16ALT) // 1 clock cycle latency
  fpmult1.io.a := a // this is the original input a, NOT the product from first multiplication!
  fpmult1.io.b := relu6out
  val res2 = fpmult1.io.res 

  // (a * relu6(product + 3)) / 6
  val magic_val = "b0_01111100_0101010".U(16.W) // 1/6 ~ 0.166015625
  val fpmult2 = Module(new FPMult16ALT) // 1 clock cycle latency
  fpmult2.io.a := res2
  fpmult2.io.b := magic_val
  // val output = fpmult2.io.res

  // val outputReg = RegInit(0.U(16.W)) // 16-bit register for the output
  // outputReg := fpmult2.io.res
  // io.out_a := outputReg
  io.out_a := fpmult2.io.res
}

/**
 * Generate Verilog sources and save it in generated/hsilugelu.v
 * Uncomment to generate the SystemVerilog file when using 'sbt run'
 */
object hsilugeluMain extends App {
    ChiselStage.emitSystemVerilogFile(
        new hsilugelu,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated2")
    )
}