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

  when (a(15).asUInt === 1.U || a === "b0_00000000_0000000".U) { // in_a <= 0
    io.out_a := 0.U // 0_00000000_0000000

  }.elsewhen ((a(14,7)).asUInt >= 130.U(8.W)) { // 8 <= in_a
    io.out_a := "b0_10000001_1000000".asUInt(16.W) // 6 = 1.5 * 2^3
    // io.out_debug := true.B

  }.elsewhen (a(14,7).asUInt < 129.U(8.W)){ // 0 < in_a < 4
    io.out_a := a // out = in

  }.otherwise { // 4 <= in_a < 8 (the exponent is 2^2 here) 
    when (a(6).asUInt === 1.U) { // look at mantissa: if msb is 1, then a >= 6, and thus out=6
      io.out_a := "b0_10000001_1000000".asUInt(16.W) // 6 = 1.5 * 2^3

    }.otherwise { // 4 <= in_a < 6
      io.out_a := a // out = in
    }
  }
}