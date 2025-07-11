package gelu
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage // needed for ChiselStage.emitSystemVerilogFile
import silu.FPMult16ALT
import silu.BF16toFP
import silu.FPAdd16ALT

/**
  * Chisel implementation using an inverted LUT containing inputs to the Sigmoid function in the range [-8, 8],
  * and calculates silu(x) = x * sigmoid(x), or gelu(x) ~ x * sigmoid(1.703125*x)
  * the inverted LUT is configurable to contain the input values that correspond to 32 equally spaced output values between [0.5 and 1.0]
  * effectively storing the inverse function: x = f^-1(y), for equally spaced y values.
  * For smaller and large numbers outside the range, the function returns 0 or the input itself respectively.
  * The implementation only supports BF16 floating point representation
  */
class siluandgeluUsingInvSigmoid32 extends Module {
    val io = IO(new Bundle {
        val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
        val in_select = Input(UInt(1.W)) // 0 for SiLU, 1 for GELU
        val out_a = Output(Bits(16.W))
    })
    val log2edgerange = 3 // log2edgerange always 3, this means the range is always [-8, 8] for BF16
    val log2lutsize = 5 // log2lutsize is 5, this means the LUT has 32 entries
    val a = io.in_a // a must be a BigInt
    val sign = a(15).asUInt
    
    val bf16tofp = Module(new BF16toFP(3, 7)) // BF16 to Fixed Point converter, 3 bits for integer part and 7 bits for fractional part
    val fpmult1 = Module(new FPMult16ALT) 
    fpmult1.io.a := a // input x
    val sigmoidInput = fpmult1.io.res
    bf16tofp.io.bf16in := sigmoidInput

    // Multiplexer
    when (io.in_select === 0.U) { // SiLU
        fpmult1.io.b := "b0_01111111_0000000".U(16.W) // 1.0
    }.otherwise { // GELU
        fpmult1.io.b := "b0_01111111_1011010".U(16.W) // 1.703125
    }

    val exp = sigmoidInput(14,7).asUInt
    val actual_exp = (exp.asSInt - 127.S(8.W)).asSInt // actual_exp can be negative!

    val a_int = bf16tofp.io.intout 
    val a_frac = bf16tofp.io.fracout
    val in_a_fp = Cat(a_int, a_frac) // create the input fixed point

    val index = RegInit(0.U(log2lutsize.W)) // index is log2lutsize bits wide, index will be directly used in output
    val sigmoidReg = RegInit(0.U(16.W)) // register for the sigmoid

    val fpmult2 = Module(new FPMult16ALT)
    fpmult2.io.a := a // input x
    fpmult2.io.b := sigmoidReg // sigmoid value calculated from input x
    io.out_a := fpmult2.io.res // SILU = x * sigmoid(x)

    when (a(14,0) === "b00000000_0000000".U ) { // in_a = +-0
        sigmoidReg := 0.U
    }.elsewhen (actual_exp >= log2edgerange.S) { // in_a <= -8 or >= +8
        when (sign === 1.U) { // in_a <= -8
            sigmoidReg := 0.U // sigmoid(a) = 0
        }.otherwise { // in_a >= +8
            sigmoidReg := "b0_01111111_0000000".U(16.W) // sigmoid(a) = 1
        }
    }.otherwise { // x*sigmoid(x)
        when (in_a_fp >= "b001_0001100".U) { // integer comparisons
            when (in_a_fp >= "b001_1111001".U) {
                when (in_a_fp >= "b010_1011010".U) {
                    when (in_a_fp >= "b011_0110111".U) {
                        when (in_a_fp >= "b100_0010010".U) {
                            index := "b11111".U // 31
                        }.otherwise {
                            index := "b11110".U // 30
                        }
                    }.otherwise {
                        when (in_a_fp >= "b011_0000001".U) {
                            index := "b11101".U // 29
                        }.otherwise {
                            index := "b11100".U // 28
                        }
                    }
                }.otherwise {
                    when (in_a_fp >= "b010_0100010".U) {
                        when (in_a_fp >= "b010_0111011".U) {
                            index := "b11011".U // 27
                        }.otherwise {
                            index := "b11010".U // 26
                        }
                    }.otherwise {
                        when (in_a_fp >= "b010_0001100".U) {
                            index := "b11001".U // 25
                        }.otherwise {
                            index := "b11000".U // 24
                        }
                    }
                }
            }.otherwise {
                when (in_a_fp >= "b001_0111011".U) {
                    when (in_a_fp >= "b001_1010111".U) {
                        when (in_a_fp >= "b001_1100111".U) {
                            index := "b10111".U // 23
                        }.otherwise {
                            index := "b10110".U // 22
                        }
                    }.otherwise {
                        when (in_a_fp >= "b001_1001001".U) {
                            index := "b10101".U // 21
                        }.otherwise {
                            index := "b10100".U // 20
                        }
                    }
                }.otherwise{
                    when (in_a_fp >= "b001_0100010".U) {
                        when (in_a_fp >= "b001_0101110".U) {
                            index := "b10011".U // 19
                        }.otherwise {
                            index := "b10010".U // 18
                        }
                    }.otherwise {
                        when (in_a_fp >= "b001_0010111".U) {
                            index := "b10001".U // 17
                        }.otherwise {
                            index := "b10000".U // 16
                        }
                    }
                }
            }
        }.otherwise {
            when (in_a_fp >= "b000_1000001".U) {
                when (in_a_fp >= "b000_1100100".U) {
                    when (in_a_fp >= "b000_1111000".U) {
                        when (in_a_fp >= "b001_0000010".U) {
                            index := "b01111".U // 15
                        }.otherwise {
                            index := "b01110".U // 14
                        }
                    }.otherwise {
                        when (in_a_fp >= "b000_1101110".U) {
                            index := "b01101".U // 13
                        }.otherwise {
                            index := "b01100".U // 12
                        }
                    }
                }.otherwise {
                    when (in_a_fp >= "b000_1010010".U) {
                        when (in_a_fp >= "b000_1011011".U) {
                            index := "b01011".U // 11
                        }.otherwise {
                            index := "b01010".U // 10
                        }
                    }.otherwise {
                        when (in_a_fp >= "b000_1001001".U) {
                            index := "b01001".U // 9
                        }.otherwise {
                            index := "b01000".U // 8
                        }
                    }
                }
            }.otherwise {
                when (in_a_fp >= "b000_0100000".U) {
                    when (in_a_fp >= "b000_0110000".U) {
                        when (in_a_fp >= "b000_0111000".U) {
                            index := "b00111".U // 7
                        }.otherwise {
                            index := "b00110".U // 6
                        }
                    }.otherwise {
                        when (in_a_fp >= "b000_0101000".U) {
                            index := "b00101".U // 5
                        }.otherwise {
                            index := "b00100".U // 4
                        }
                    }
                }.otherwise{
                    when (in_a_fp >= "b000_0010000".U) {
                        when (in_a_fp >= "b000_0011000".U) {
                            index := "b00011".U // 3
                        }.otherwise {
                            index := "b00010".U // 2
                        }
                    }.otherwise {
                        index := "b00001".U // 1
                    }
                }
            }
        }
        when (sign === 0.U) { // in_a > 0
            sigmoidReg := Cat("b0_01111110".U, index, 0.U((7 - log2lutsize).W)) // output f(x)
        }.otherwise { // -8 < in_a <= -0
            val fpadd = Module(new FPAdd16ALT) // 3 clock cycles latency
            fpadd.io.a := "b0_01111111_0000000".U(16.W) // 1.0
            fpadd.io.b := Cat("b1_01111110".U, index, 0.U((7 - log2lutsize).W)) // subtracts f(-x) from 1.0, since the sign bit is set to 1
            sigmoidReg := fpadd.io.res // output f(x) = 1-f(-x)
        }
    }
}

/**
 * Generate Verilog sources and save it in generated/siluandgeluUsingInvSigmoid32.sv
 * Uncomment to generate the SystemVerilog file when using 'sbt run'
 */
object siluandgeluUsingInvSigmoid32Main extends App {
    ChiselStage.emitSystemVerilogFile(
        new siluandgeluUsingInvSigmoid32,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated")
    )
}
