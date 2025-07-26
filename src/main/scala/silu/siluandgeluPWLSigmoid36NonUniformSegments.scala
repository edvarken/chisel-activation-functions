package silu
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage // needed for ChiselStage.emitSystemVerilogFile

/**
  * Chisel implementation to calculate silu(x) = x * sigmoid(x), and gelu(x) ~ x * sigmoid(1.702x)
  * sigmoid is approximated using 36 segments each linearly interpolated as y = m*x + q, using the local slope m and y-intercept q.
  * first 4 segments: equally spaced x-values between 0 and 2
  * second 32 segments: equally spaced x-values between 2 and 6
  *
  * For negative numbers, q' = 1 - q is exploited, halving the needed slopes, but doubling the needed intercepts.
  * if abs(numbers) > 6, sigmoid = 0 or 1 depending on sign, so the silu and gelu just return 0 or the input itself.
  * The implementation only supports BF16 inputs and outputs, but uses fixed point representation internally.
  */
class siluandgeluPWLSigmoid36NonUniformSegments extends Module {
    val io = IO(new Bundle {
        val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
        val in_select = Input(UInt(1.W)) // 0 for SiLU, 1 for GELU
        val out_a = Output(Bits(16.W))
    })
    val a = io.in_a // a must be a BigInt
    val sign = a(15).asUInt

    val bf16tofp = Module(new BF16toFP(3, 3)) // BF16 to Fixed Point converter, 3 bits for integer part and 3 bits for fractional part

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
    }.elsewhen (actual_exp >= 3.S || a_int >= "b110".U) { // sigmoidInput <= -6 or sigmoidInput >= +6
        when (sign === 1.U) { // sigmoidInput <= -6
            fullRangeSigmoidReg := 0.U // sigmoid(sigmoidInput) = 0
        }.otherwise { // in_a >= +6
            fullRangeSigmoidReg := "b0_01111111_0000000".U(16.W) // sigmoid(sigmoidInput) = 1
        }
    }.otherwise { // x*sigmoid(sigmoidInput) with 6.0 > sigmoidInput >= 0.0
        when (in_a_fp < "b010_000".U) { // sigmoidInput < 2.0: first four segments
            when (a_int >= "b001".U) { // 2.0 > sigmoidInput >= 1.0
                when (a_frac >= "b10".U) { // 2.0 > sigmoidInput >= 1.5
                    slopeReg := "b0011111000000001".U // 0.125977
                    when (sign === 0.U) {
                        interceptReg := "b0011111100100001".U // 0.628906
                    }.otherwise {
                        interceptReg := "b0011111010111111".U // q' = 1 - q = 0.373047
                    }
                }.otherwise { // 1.5 > sigmoidInput >= 1.0
                    slopeReg := "b0011111000110001".U // 0.172852
                    when (sign === 0.U) {
                        interceptReg := "b0011111100001111".U // 0.558594
                    }.otherwise {
                        interceptReg := "b0011111011100010".U // q' = 1 - q = 0.441406
                    }
                }
            }.otherwise { 
                when (a_frac >= "b10".U) { // 1.0 > sigmoidInput >= 0.5
                    slopeReg := "b0011111001011110".U // 0.216797
                    when (sign === 0.U) {
                        interceptReg := "b0011111100000100".U // 0.515625
                    }.otherwise {
                        interceptReg := "b0011111011111001".U // q' = 1 - q = 0.486328
                    }
                }.otherwise { // 0.5 > sigmoidInput >= 0.0
                    slopeReg := "b0011111001111011".U // 0.245117
                    interceptReg := "b0011111100000000".U // 0.500000 and q' = 1 - q = 0.500000
                }
            }
        }.otherwise { // 2.0 <= sigmoidInput < 6.0: middle '32' segments 
            when (a_int >= "b100".U) { // sigmoidInput >= 4
                when (a_int >= "b101".U) { // sigmoidInput > 5
                    when (in_a_fp >= "b101_100".U) { // sigmoidInput > 5.5
                        when (in_a_fp >= "b101_110".U) { // sigmoidInput > 5.75
                            when (in_a_fp >= "b101_111".U) { // 6.0 > sigmoidInput >= 5.875000
                                slopeReg := "b0011101100101100".U // 0.002625 // index 32
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101111011".U // 0.980469
                                }.otherwise {
                                    interceptReg := "b0011110010010101".U // 0.018188
                                }
                            }.otherwise { // 5.875000 > sigmoidInput >= 5.75
                                slopeReg := "b0011101101000011".U // 0.002975 // index 31
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101111011".U // 0.980469
                                }.otherwise {
                                    interceptReg := "b0011110010100110".U // 0.020264
                                }
                            }
                        }.otherwise { // 5.75 > sigmoidInput >= 5.5
                            when (in_a_fp >= "b101_101".U) { // 5.75 > sigmoidInput >= 5.625000
                                slopeReg := "b0011101101011101".U // 0.003372 // index 30
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101111010".U // 0.976562
                                }.otherwise {
                                    interceptReg := "b0011110010111001".U // 0.022583
                                }
                            }.otherwise { // 5.625000 > sigmoidInput >= 5.5
                                slopeReg := "b0011101101111010".U // 0.003815 // index 29
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101111010".U // 0.976562
                                }.otherwise {
                                    interceptReg := "b0011110011001101".U // 0.025024
                                }   
                            }
                        }
                    }.otherwise {  // 5.5 > sigmoidInput >= 5.0
                        when (in_a_fp >= "b101_010".U) { // 5.5 > sigmoidInput > 5.25
                            when (in_a_fp >= "b101_011".U) { // 5.5 > sigmoidInput > 5.375
                                slopeReg := "b0011101110001101".U // 0.004303 // index 28
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101111001".U // 0.972656
                                }.otherwise {
                                    interceptReg := "b0011110011100100".U // 0.027832
                                }
                            }.otherwise { // 5.375 > sigmoidInput >= 5.25
                                slopeReg := "b0011101110100000".U // 0.004883 // index 27
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101111000".U // 0.968750
                                }.otherwise {
                                    interceptReg := "b0011110011111101".U // 0.030884   
                                }
                            }
                        }.otherwise { // 5.25 > sigmoidInput >= 5.0
                            when (in_a_fp >= "b101_001".U) { // 5.25 > sigmoidInput > 5.125
                                slopeReg := "b0011101110110101".U // 0.005524 // index 26
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101110111".U // 0.964844
                                }.otherwise {
                                    interceptReg := "b0011110100001100".U // 0.034180
                                }
                            }.otherwise { // 5.125 > sigmoidInput >= 5.0
                                slopeReg := "b0011101111001101".U // 0.006256 // index 25
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101110110".U // 0.960938
                                }.otherwise {
                                    interceptReg := "b0011110100011100".U // 0.038086   
                                }
                            }
                        }
                    }
                }.otherwise { // 5.0 > sigmoidInput >= 4.0
                    when (in_a_fp >= "b100_100".U) { // sigmoidInput >= 4.5
                        when (in_a_fp >= "b100_110".U) { // 5.0 > sigmoidInput >= 4.75
                            when (in_a_fp >= "b100_111".U) { // 5.0 > sigmoidInput >= 4.875
                                slopeReg := "b0011101111101000".U // 0.007080 // index 24
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101110101".U // 0.957031
                                }.otherwise {
                                    interceptReg := "b0011110100101100".U // 0.041992
                                }
                            }.otherwise { // 4.875 > sigmoidInput >= 4.75
                                slopeReg := "b0011110000000011".U // 0.007996 // index 23
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101110100".U // 0.953125
                                }.otherwise {
                                    interceptReg := "b0011110100111111".U // 0.046631
                                }
                            }
                        }.otherwise { // 4.75 > sigmoidInput >= 4.5
                            when (in_a_fp >= "b100_101".U) { // 4.75 > sigmoidInput >= 4.625000
                                slopeReg := "b0011110000010100".U // 0.009033 // index 22
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101110011".U // 0.949219
                                }.otherwise {
                                    interceptReg := "b0011110101010011".U // 0.051514
                                }
                            }.otherwise { // 4.625000 > sigmoidInput >= 4.5
                                slopeReg := "b0011110000101000".U // 0.010254 // index 21
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101110001".U // 0.941406
                                }.otherwise {
                                    interceptReg := "b0011110101101010".U // 0.057129
                                }
                            }
                        }
                    }.otherwise {  // 4.5 > sigmoidInput >= 4.0
                        when (in_a_fp >= "b100_010".U) { // >= 4.25
                            when (in_a_fp >= "b100_011".U) { // 4.5 > sigmoidInput >= 4.375
                                slopeReg := "b0011110000111101".U // 0.011536 // index 20
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101110000".U // 0.937500
                                }.otherwise {
                                    interceptReg := "b0011110110000001".U // 0.062988
                                }
                            }.otherwise { // 4.375 > sigmoidInput >= 4.25
                                slopeReg := "b0011110001010110".U // 0.013062 // index 19
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101101110".U // 0.929688
                                }.otherwise {
                                    interceptReg := "b0011110110001110".U // 0.069336
                                }
                            }
                        }.otherwise { // index 9: 4.25 > sigmoidInput >= 4.0
                            when (in_a_fp >= "b100_001".U) { // 4.25 > sigmoidInput >= 4.125
                                slopeReg := "b0011110001110010".U // 0.014771 // index 18
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101101100".U // 0.921875
                                }.otherwise {
                                    interceptReg := "b0011110110011101".U // 0.076660
                                }
                            }.otherwise { // 4.125 > sigmoidInput >= 4.0
                                slopeReg := "b0011110010001000".U // 0.016602 // index 17
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101101010".U // 0.914062
                                }.otherwise {
                                    interceptReg := "b0011110110101101".U // 0.084473
                                }
                            }
                        }
                    }
                }
            }.otherwise { // 4.0 > sigmoidInput >= 2.0
                when (a_int >= "b011".U) { // sigmoidInput > 3.0
                    when (in_a_fp >= "b011_100".U) { // sigmoidInput > 3.5
                        when (in_a_fp >= "b011_110".U) { // 4.0 > sigmoidInput >= 3.75
                            when (in_a_fp >= "b011_111".U) { // 4.0 > sigmoidInput >= 3.875
                                slopeReg := "b0011110010011010".U // 0.018799 // index 16
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101101000".U // 0.906250
                                }.otherwise {
                                    interceptReg := "b0011110110111111".U // 0.093262
                                }
                            }.otherwise { // 3.875 > sigmoidInput >= 3.75 // index 15
                                slopeReg := "b0011110010101101".U // 0.021118
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101100110".U // 0.898438
                                }.otherwise {
                                    interceptReg := "b0011110111010010".U // 0.102539
                                }
                            }
                        }.otherwise { // 3.75 > sigmoidInput >= 3.5
                            when (in_a_fp >= "b011_101".U) { // 3.75 > sigmoidInput >= 3.625
                                slopeReg := "b0011110011000011".U // 0.023804 // index 14
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101100011".U // 0.886719
                                }.otherwise {
                                    interceptReg := "b0011110111100110".U // 0.112305
                                }
                            }.otherwise { // 3.625 > sigmoidInput >= 3.5 // index 13
                                slopeReg := "b0011110011011100".U // 0.026855
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101100000".U // 0.875000
                                }.otherwise {
                                    interceptReg := "b0011110111111100".U // 0.123047
                                }
                            }
                        }
                    }.otherwise { // 3.5 > sigmoidInput > 3.0
                        when (in_a_fp >= "b011_010".U) { // 3.5 > sigmoidInput >= 3.25
                            when (in_a_fp >= "b011_011".U) { // 3.5 > sigmoidInput >= 3.375
                                slopeReg := "b0011110011110111".U // 0.030151 // index 12
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101011101".U // 0.863281
                                }.otherwise {
                                    interceptReg := "b0011111000001010".U // 0.134766
                                }
                            }.otherwise { // 3.375 > sigmoidInput >= 3.25 // index 11
                                slopeReg := "b0011110100001011".U // 0.033936
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101011010".U // 0.851562
                                }.otherwise {
                                    interceptReg := "b0011111000010111".U // 0.147461
                                }
                            }
                        }.otherwise { // 3.25 > sigmoidInput >= 3.0
                            when (in_a_fp >= "b011_001".U) { // 3.25 > sigmoidInput >= 3.125
                                slopeReg := "b0011110100011100".U // 0.038086 // index 10
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101010111".U // 0.839844
                                }.otherwise {
                                    interceptReg := "b0011111000100101".U // 0.161133
                                }
                            }.otherwise { // 3.125 > sigmoidInput >= 3.0 // index 9
                                slopeReg := "b0011110100101111".U // 0.042725
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101010011".U // 0.824219
                                }.otherwise {
                                    interceptReg := "b0011111000110100".U // 0.175781
                                }
                            }
                        }
                    }
                }.otherwise { // 3.0 > sigmoidInput >= 2.0
                    when (in_a_fp >= "b010_100".U) { // sigmoidInput >= 2.5
                        when (a_frac >= "b010_110".U) { // 3.0 > sigmoidInput >= 2.75
                            when (a_frac >= "b010_111".U) { // 3.0 > sigmoidInput >= 2.875
                                slopeReg := "b0011110101000100".U // 0.047852 // index 8
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101001111".U // 0.808594
                                }.otherwise {
                                    interceptReg := "b0011111001000011".U // 0.190430
                                }
                            }.otherwise { // 2.875 > sigmoidInput >= 2.75
                                slopeReg := "b0011110101011011".U // 0.053467 // index 7
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101001011".U // 0.792969
                                }.otherwise {
                                    interceptReg := "b0011111001010100".U // 0.207031
                                }
                            }
                        }.otherwise { // 2.75 > sigmoidInput >= 2.5
                            when (a_frac >= "b010_101".U) { // 2.75 > sigmoidInput >= 2.625
                                slopeReg := "b0011110101110100".U // 0.059570 // index 6
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101000111".U // 0.777344
                                }.otherwise {
                                    interceptReg := "b0011111001100110".U // 0.224609
                                }
                            }.otherwise { // 2.625 > sigmoidInput >= 2.5
                                slopeReg := "b0011110110001000".U // 0.066406 // index 5
                                when (sign === 0.U) {
                                    interceptReg := "b0011111101000010".U // 0.757812
                                }.otherwise {
                                    interceptReg := "b0011111001111000".U // 0.242188
                                }
                            }
                        }
                    }.otherwise { // 2.5 > sigmoidInput >= 2.0
                        when (in_a_fp >= "b010_010".U) { // 2.5 > sigmoidInput >= 2.25
                            when (a_frac >= "b010_011".U) { // 2.5 > sigmoidInput >= 2.375
                                slopeReg := "b0011110110010111".U // 0.073730 // index 4
                                when (sign === 0.U) {
                                    interceptReg := "b0011111100111101".U // 0.738281
                                }.otherwise {
                                    interceptReg := "b0011111010000101".U // 0.259766
                                }
                            }.otherwise { // 2.375 > sigmoidInput >= 2.25
                                slopeReg := "b0011110110101000".U // 0.082031 // index 3
                                when (sign === 0.U) {
                                    interceptReg := "b0011111100111000".U // 0.718750
                                }.otherwise {
                                    interceptReg := "b0011111010001111".U // 0.279297
                                }
                            }
                        }.otherwise { // 2.25 > sigmoidInput >= 2.0
                            when (a_frac >= "b010_001".U) { // 2.25 > sigmoidInput >= 2.125
                                slopeReg := "b0011110110111010".U // 0.090820 // index 2
                                when (sign === 0.U) {
                                    interceptReg := "b0011111100110011".U // 0.699219
                                }.otherwise {
                                    interceptReg := "b0011111010011001".U // 0.298828
                                }
                            }.otherwise { // 2.125 > sigmoidInput >= 2.0
                                slopeReg := "b0011110111001101".U // 0.100098 // index 1
                                when (sign === 0.U) {
                                    interceptReg := "b0011111100101110".U // 0.679688
                                }.otherwise {
                                    interceptReg := "b0011111010100100".U // 0.320312
                                }
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
 * Generate Verilog sources and save it in generated/siluandgeluPWLSigmoid36NonUniformSegments.sv
 * Generate the SystemVerilog file when using 'sbt run'
 */
object siluandgeluPWLSigmoid36NonUniformSegmentsMain extends App {
    ChiselStage.emitSystemVerilogFile(
        new siluandgeluPWLSigmoid36NonUniformSegments,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated2")
    )
}
