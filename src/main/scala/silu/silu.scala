package silu
import chisel3._

/**
  * This is a Chisel implementation of an approximation of the SiLU activation function, which is defined as:
  * silu(x) ~ x * relu6(x+3) / 6
  * The implementation only supports BF16 floating point representation
  */
class silu extends Module {
  val io = IO(new Bundle {
    val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
    val out_a = Output(Bits(16.W))
  })

  // we could also bypass all this if by just returning 0 if input < -3 or returning input itself if input > 3
  // This could be done in 0 clock cycles instead of 5 clock cycles, however the latency of silu would be unpredictable then.

  val a = io.in_a // a must be a BigInt
  // a + 3
  val fpadd = Module(new FPAdd16) // 3 clock cycles latency
  fpadd.io.a := a
  fpadd.io.b := "b0_10000000_1000000".U(16.W) // 3.0 = 1.5 * 2^1 
  val res1 = fpadd.io.res 

  // relu6(a + 3)
  val relu6 = Module(new relu6) // 0 clock cycles latency
  relu6.io.in_a := res1
  val relu6out = relu6.io.out_a

  // a * relu6(a + 3)
  val fpmult1 = Module(new FPMult16) // 1 clock cycle latency
  fpmult1.io.a := a
  fpmult1.io.b := relu6out
  val res2 = fpmult1.io.res 

  // (a * relu6(a + 3)) / 6
  val magic_val = "b0_01111100_0101010".U(16.W) // 1/6 ~ 0.16601563
  val fpmult2 = Module(new FPMult16) // 1 clock cycle latency
  fpmult2.io.a := res2
  fpmult2.io.b := magic_val
  // val output = fpmult2.io.res
  io.out_a := fpmult2.io.res
}