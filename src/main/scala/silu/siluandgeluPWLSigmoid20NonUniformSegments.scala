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

    val bf16tofp = Module(new BF16toFP(3, 2)) // BF16 to Fixed Point converter, 3 bits for integer part and 2 bits for fractional part

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
        when (in_a_fp < "b010_00".U) { // sigmoidInput < 2.0: first four segments
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
        }.otherwise { // 2.0 <= sigmoidInput < 6.0: middle '16' segments 
            when (a_int >= "b100".U) { // sigmoidInput >= 4
                when (a_int >= "b101".U) { // sigmoidInput > 5
                    when (in_a_fp >= "b101_10".U) { // sigmoidInput > 5.5
                        when (in_a_fp >= "b101_11".U) { // sigmoidInput > 5.75
                            slopeReg := "b0011101100111000".U // 0.002808
                            when (sign === 0.U) {
                                interceptReg := "b0011111101111011".U // 0.980469
                            }.otherwise {
                                interceptReg := "b0011110010011110".U // 0.019287
                            }
                        }.otherwise { // 5.75 > sigmoidInput >= 5.5
                            slopeReg := "b0011101101101011".U // 0.003586
                            when (sign === 0.U) {
                                interceptReg := "b0011111101111010".U // 0.976562
                            }.otherwise {
                                interceptReg := "b0011110011000011".U // 0.023804
                            }
                        }
                    }.otherwise {  // 5.5 > sigmoidInput >= 5.0
                        when (in_a_fp >= "b101_01".U) { // 5.5 > sigmoidInput > 5.25
                            slopeReg := "b0011101110010111".U // 0.004608
                            when (sign === 0.U) {
                                interceptReg := "b0011111101111000".U // 0.968750
                            }.otherwise {
                                interceptReg := "b0011110011110001".U //0.029419
                            }
                        }.otherwise { // 5.25 > sigmoidInput >= 5.0
                            slopeReg := "b0011101111000001".U // 0.005890
                            when (sign === 0.U) {
                                interceptReg := "b0011111101110111".U // 0.964844
                            }.otherwise {
                                interceptReg := "b0011110100010100".U // 0.036133   
                            }
                        }
                    }
                }
                .otherwise { // 5.0 > sigmoidInput >= 4.0
                    when (in_a_fp >= "b100_10".U) { // sigmoidInput >= 4.5
                        when (in_a_fp >= "b100_11".U) { // 5.0 > sigmoidInput >= 4.75
                            slopeReg := "b0011101111110111".U // 0.007538 // index 12
                            when (sign === 0.U) {
                                interceptReg := "b0011111101110101".U // 0.957031
                            }.otherwise {
                                interceptReg := "b0011110100110110".U // 0.044434
                            }
                        }.otherwise { // 4.75 > sigmoidInput >= 4.5
                            slopeReg := "b0011110000011110".U // 0.009644
                            when (sign === 0.U) {
                                interceptReg := "b0011111101110010".U // 0.945312
                            }.otherwise {
                                interceptReg := "b0011110101011111".U // 0.054443
                            }
                        }
                    }.otherwise {  // 4.5 > sigmoidInput >= 4.0
                        when (in_a_fp >= "b100_01".U) { // >= 4.25
                            slopeReg := "b0011110001001010".U // 0.012329
                            when (sign === 0.U) {
                                interceptReg := "b0011111101101111".U // 0.933594
                            }.otherwise {
                                interceptReg := "b0011110110001000".U //0.066406
                            }
                        }.otherwise { // index 9
                            slopeReg := "b0011110010000001".U // 0.015747
                            when (sign === 0.U) {
                                interceptReg := "b0011111101101011".U // 0.917969
                            }.otherwise {
                                interceptReg := "b0011110110100101".U // 0.080566   
                            }
                        }
                    }
                }
            }.otherwise { // 4.0 > sigmoidInput >= 2.0
                when (a_int >= "b011".U) { // sigmoidInput > 3.0
                    when (in_a_fp >= "b011_10".U) { // sigmoidInput > 3.5
                        when (in_a_fp >= "b011_11".U) { // 4.0 > sigmoidInput >= 3.75
                            slopeReg := "b0011110010100100".U // 0.020020
                            when (sign === 0.U) {
                                interceptReg := "b0011111101100111".U // 0.902344
                            }.otherwise {
                                interceptReg := "b0011110111001000".U // 0.097656
                            }
                        }.otherwise { // 3.75 > sigmoidInput >= 3.5 // index 7
                            slopeReg := "b0011110011010000".U // 0.025391
                            when (sign === 0.U) {
                                interceptReg := "b0011111101100010".U // 0.882812
                            }.otherwise {
                                interceptReg := "b0011110111110010".U // 0.118164
                            }
                        }
                    }.otherwise { 
                        when (in_a_fp >= "b011_01".U) { // 3.5 > sigmoidInput >= 3.25
                            slopeReg := "b0011110100000011".U // 0.031982
                            when (sign === 0.U) {
                                interceptReg := "b0011111101011100".U // 0.859375
                            }.otherwise {
                                interceptReg := "b0011111000010001".U // 0.141602
                            }
                        }.otherwise { // 3.25 > sigmoidInput >= 3.0 // index 5
                            slopeReg := "b0011110100100101".U // 0.040283
                            when (sign === 0.U) {
                                interceptReg := "b0011111101010101".U // 0.832031
                            }.otherwise {
                                interceptReg := "b0011111000101101".U // 0.168945
                            }
                        }
                    }
                }.otherwise { // 3.0 > sigmoidInput >= 2.0
                    when (a_frac >= "b10".U) { // sigmoidInput >= 2.5
                        when (a_frac >= "b11".U) { // 3.0 > sigmoidInput >= 2.75
                            slopeReg := "b0011110101001111".U // 0.050537
                            when (sign === 0.U) {
                                interceptReg := "b0011111101001101".U // 0.800781
                            }.otherwise {
                                interceptReg := "b0011111001001100".U // 0.199219
                            }
                        }.otherwise { // 2.75 > sigmoidInput >= 2.5
                            slopeReg := "b0011110110000001".U // 0.062988
                            when (sign === 0.U) {
                                interceptReg := "b0011111101000100".U // 0.765625
                            }.otherwise {
                                interceptReg := "b0011111001101111".U // 0.233398
                            }
                        }
                    }.otherwise {
                        when (a_frac >= "b01".U) { // 2.5 > sigmoidInput >= 2.25
                            slopeReg := "b0011110110100000".U // 0.078125
                            when (sign === 0.U) {
                                interceptReg := "b0011111100111011".U // 0.730469
                            }.otherwise {
                                interceptReg := "b0011111010001011".U // 0.271484
                            }
                        }.otherwise { // 2.25 > sigmoidInput >= 2.0
                            slopeReg := "b0011110111000011".U // 0.095215
                            when (sign === 0.U) {
                                interceptReg := "b0011111100110001".U // 0.691406
                            }.otherwise {
                                interceptReg := "b0011111010011111".U // 0.310547
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
