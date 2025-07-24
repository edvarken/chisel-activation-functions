package silu
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage // needed for ChiselStage.emitSystemVerilogFile

/**
  * Chisel implementation to calculate silu(x) = x * sigmoid(x), and gelu(x) ~ x * sigmoid(1.702x)
  * sigmoid is approximated using 20 segments each linearly interpolated as y = m*x + q, using the local slope m and y-intercept q.
  * first 4 segments: equally spaced x-values between 0 and 2
  * second 16 segments: equally spaced x-values between 2 and 6
  *
  * For negative numbers, q' = 1 - q is exploited, halving the needed slopes, but doubling the needed intercepts.
  * if abs(numbers) > 6, sigmoid = 0 or 1 depending on sign, so the silu and gelu just return 0 or the input itself.
  * The implementation only supports BF16 inputs and outputs, but uses fixed point representation internally.
  */
class siluandgeluPWLSigmoid20NonUniformSegments extends Module {
    val io = IO(new Bundle {
        val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
        val in_select = Input(UInt(1.W)) // 0 for SiLU, 1 for GELU
        val out_a = Output(Bits(16.W))
    })
    val a = io.in_a // a must be a BigInt
    val sign = a(15).asUInt

    val bf16tofp = Module(new BF16toFP(3, 7)) // BF16 to Fixed Point converter, 3 bits for integer part and 7 bits for fractional part

    val fpmult0 = Module(new FPMult16ALT) // 1cc for x*1 or x*1.703125
    fpmult0.io.a := a
    val sigmoidInput = fpmult0.io.res // the sigmoid gets the real value of the input (can be positive or negative)
    val exp = sigmoidInput(14,7).asUInt
    val actual_exp = (exp.asSInt - 127.S(8.W)).asSInt

    when (io.in_select === 0.U) { // SiLU
        fpmult0.io.b := "b0_01111111_0000000".U(16.W) // 1.0
    }.otherwise { // GELU
        fpmult0.io.b := "b0_01111111_1011010".U(16.W) // 1.703125
    }

    bf16tofp.io.bf16in := sigmoidInput
    val a_int = bf16tofp.io.intout 
    val a_frac = bf16tofp.io.fracout
    val in_a_fp = Cat(a_int, a_frac) // create the input fixed point

    val slopeReg = RegInit(0.U(16.W)) // register for the slope
    val interceptReg = RegInit(0.U(16.W)) // register for the y-intercept
    val fullRangeSigmoidReg = RegInit(0.U(16.W)) // register for the sigmoid value for all x

    val fpmult1 = Module(new FPMult16ALT) // 1cc for slope * x
    fpmult1.io.a := sigmoidInput
    fpmult1.io.b := slopeReg
    val fpadd1 = Module(new FPAdd16ALT) // 3cc for slope*x + intercept
    fpadd1.io.a := fpmult1.io.res 
    fpadd1.io.b := interceptReg // can be < 0.5 now, different technique using more intercepts is used for the negative inputs instead of 1-f(-x)
    val sigmoidOut = fpadd1.io.res // no reg, just a wire

    val fpmult2 = Module(new FPMult16ALT) // 1cc for final multiplication
    fpmult2.io.a := a
    fpmult2.io.b := fullRangeSigmoidReg
    io.out_a := fpmult2.io.res // SiLU(x) = x * sigmoid(x), GELU(x) = x * sigmoid(1.702x)

    when (a(14,0) === "b00000000_0000000".U ) { // in_a = +-0
        fullRangeSigmoidReg := 0.U
    }.elsewhen (a_int >= "b110".U || actual_exp >= 3.S) { // sigmoidInput <= -6 or sigmoidInput >= +6
        when (sign === 1.U) { // sigmoidInput <= -6
            fullRangeSigmoidReg := 0.U // sigmoid(sigmoidInput) = 0
        }.otherwise { // in_a >= +6
            fullRangeSigmoidReg := "b0_01111111_0000000".U(16.W) // sigmoid(sigmoidInput) = 1
        }

    }.otherwise { // x*sigmoid(sigmoidInput)
        when (in_a_fp < "b010_0000000".U) { // sigmoidInput < 2.0: first four segments
            when (a_int >= "b001".U) { // 2.0 > sigmoidInput >= 1.0
                when (a_frac >= "b1000000".U) { // 2.0 > sigmoidInput >= 1.5
                    slopeReg := "b0011111000000000".U // 0.125000
                    when (sign === 0.U) {
                        interceptReg := "b0011111100100001".U // 0.628906
                    }.otherwise {
                        interceptReg := "b0011111010111110".U // q' = 1 - q = 0.371094
                    }
                }.otherwise { // 1.5 > sigmoidInput >= 1.0
                    slopeReg := "b0011111000110000".U // 0.171875
                    when (sign === 0.U) {
                        interceptReg := "b0011111100001111".U // 0.558594
                    }.otherwise {
                        interceptReg := "b0011111011100010".U // q' = 1 - q = 0.441406
                    }
                }
            }.otherwise { 
                when (a_frac >= "b1000000".U) { // 1.0 > sigmoidInput >= 0.5
                    slopeReg := "b0011111001100000".U // 0.218750
                    when (sign === 0.U) {
                        interceptReg := "b0011111100000011".U // 0.511719
                    }.otherwise {
                        interceptReg := "b0011111011111010".U // q' = 1 - q = 0.488281
                    }
                }.otherwise { // 0.5 > sigmoidInput >= 0.0
                    slopeReg := "b0011111001111000".U // 0.242188
                    interceptReg := "b0011111100000000".U // 0.500000 and q' = 1 - q = 0.500000
                }
            }
        }.otherwise { // 2.0 <= sigmoidInput < 6.0: last '16' segments 
            when (a_int >= "b100".U) { // sigmoidInput >= 4
                when (a_int >= "b101".U) { // sigmoidInput > 5
                    when (in_a_fp >= "b101_0100000".U) { // sigmoidInput > 5.25: 3 segments with same slope and intercept
                        slopeReg := "b0000000000000000".U // 0.0
                        when (sign === 0.U) {
                            interceptReg := "b0011111101111111".U // 0.996094
                        }.otherwise {
                            interceptReg := "b0011101110000000".U // 0.003906
                        }
                    }.otherwise {  // 5.25 > sigmoidInput >= 5.0
                        slopeReg := "b0011110010000000".U // 0.015625
                        when (sign === 0.U) {
                            interceptReg := "b0011111101101010".U // 0.914062
                        }.otherwise {
                            interceptReg := "b0011110110110000".U //0.085938
                        }
                    }
                }
                .otherwise { // 5.0 > sigmoidInput >= 4.0
                    when (in_a_fp >= "b100_1100000".U) { // 5.0 > sigmoidInput >= 4.75
                        slopeReg := "b0000000000000000".U // 0.0
                        when (sign === 0.U) {
                            interceptReg := "b0011111101111110".U // 992188
                        }.otherwise {
                            interceptReg := "b0011110000000000".U // 0.007812
                        }
                    }.otherwise { // 4.75 > sigmoidInput >= 4.0: 3 segments with same slope and intercept
                        slopeReg := "b0011110010000000".U // 0.015625
                        when (sign === 0.U) {
                            interceptReg := "b0011111101101011".U // 0.917969
                        }.otherwise {
                            interceptReg := "b0011110110101000".U // 0.082031
                        }
                    }
                }
            }.otherwise { // 4.0 > sigmoidInput >= 2.0
                when (a_int >= "b011".U) { // sigmoidInput > 3.0
                    when (in_a_fp >= "b011_1000000".U) { // sigmoidInput > 3.5
                        when (in_a_fp >= "b011_1100000".U) { // 4.0 > sigmoidInput >= 3.75
                            slopeReg := "b0011110010000000".U // 0.015625
                            when (sign === 0.U) {
                                interceptReg := "b0011111101101011".U // 0.917969
                            }.otherwise {
                                interceptReg := "b0011110110101000".U // 0.082031
                            }
                        }.otherwise { // 3.75 > sigmoidInput >= 3.5 // index 7
                            slopeReg := "b0011110100000000".U // 0.031250
                            when (sign === 0.U) {
                                interceptReg := "b0011111101011100".U // 0.859375
                            }.otherwise {
                                interceptReg := "b0011111000010000".U // 0.140625
                            }
                        }
                    }.otherwise { 
                        when (in_a_fp >= "b011_0100000".U) { // 3.5 > sigmoidInput >= 3.25
                            slopeReg := "b0011110100000000".U // 0.031250
                            when (sign === 0.U) {
                                interceptReg := "b0011111101011100".U // 0.859375
                            }.otherwise {
                                interceptReg := "b0011111000010000".U // 0.140625
                            }
                        }.otherwise { // 3.25 > sigmoidInput >= 3.0 // index 5
                            slopeReg := "b0011110100000000".U // 0.031250
                            when (sign === 0.U) {
                                interceptReg := "b0011111101011100".U // 0.859375
                            }.otherwise {
                                interceptReg := "b0011111000010000".U // 0.140625
                            }
                        }
                    }
                }.otherwise { // 3.0 > sigmoidInput >= 2.0
                    when (a_frac >= "b1000000".U) { // sigmoidInput >= 2.5
                        when (a_frac >= "b1100000".U) { // 3.0 > sigmoidInput >= 2.75
                            slopeReg := "b0011110101000000".U // 0.046875
                            when (sign === 0.U) {
                                interceptReg := "b0011111101010000".U // 0.812500
                            }.otherwise {
                                interceptReg := "b0011111001000000".U // 0.187500
                            }
                        }.otherwise { // 2.75 > sigmoidInput >= 2.5
                            slopeReg := "b0011110110000000".U // 0.062500
                            when (sign === 0.U) {
                                interceptReg := "b0011111101000101".U // 0.769531
                            }.otherwise {
                                interceptReg := "b0011111001101100".U // 0.230469
                            }
                        }
                    }.otherwise {
                        when (a_frac >= "b0100000".U) { // 2.5 > sigmoidInput >= 2.25
                            slopeReg := "b0011110110100000".U // 0.078125
                            when (sign === 0.U) {
                                interceptReg := "b0011111100111011".U // 0.730469
                            }.otherwise {
                                interceptReg := "b0011111010001010".U // 0.269531
                            }
                        }.otherwise { // 2.25 > sigmoidInput >= 2.0
                            slopeReg := "b0011110111100000".U // 0.109375
                             when (sign === 0.U) {
                                interceptReg := "b0011111100101001".U // 0.660156
                            }.otherwise {
                                interceptReg := "b0011111010101110".U // 0.339844
                            }
                        }
                    }
                }
            }
        }
        fullRangeSigmoidReg := sigmoidOut
    }
}

/**
 * Generate Verilog sources and save it in generated/siluandgeluPWLSigmoid20NonUniformSegments.sv
 * Generate the SystemVerilog file when using 'sbt run'
 */
object siluandgeluPWLSigmoid20NonUniformSegmentsMain extends App {
    ChiselStage.emitSystemVerilogFile(
        new siluandgeluPWLSigmoid20NonUniformSegments,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated2")
    )
}
