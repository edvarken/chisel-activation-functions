package silu
import chisel3._

/**
  * ReLU6 non-linear activation function. assumes BF16 input: 1 sign bit, 8 exponent bits, 7 mantissa bits
  */
class relu6 extends Module {
  val io = IO(new Bundle {
    val in_a = Input(Bits(16.W)) // define just as plain 16 bits
    val out_a = Output(Bits(16.W))
    // val out_debug = Output(Bool())
  })
  val a  = io.in_a
  // io.out_debug := false.B // normally

  when (a(15).asUInt === 1.U || a === "b0_00000000_0000000".U) { // in_a negative or 0
    io.out_a := 0.U // 0_00000000_0000000

  }.elsewhen ((a(14,7)).asUInt >= 130.U(8.W)) { // 6 < in_a
    io.out_a := "b0_10000001_1000000".asUInt(16.W) // 6 = 1.5 * 2^3
    // io.out_debug := true.B

  }.elsewhen (a(14,7).asUInt < 129.U(8.W)){ // 0 < in_a < 6
    io.out_a := a // out = in

  }.otherwise { // exponent is 2^2, look at mantissa: if msb is 1, then a >= 6, and thus out=6
    when (a(6).asUInt === 1.U) {
      io.out_a := "b0_10000001_1000000".asUInt(16.W) // 6 = 1.5 * 2^3

    }.otherwise {
      io.out_a := a // out = in
    }
  }
}