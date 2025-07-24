package silu
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage // needed for ChiselStage.emitSystemVerilogFile

/**
  * Chisel implementation to calculate silu(x) = x * sigmoid(x), and gelu(x) ~ x * sigmoid(1.702x)
  * sigmoid is approximated using 20 segments each linearly interpolated as y = m*abs(x) + q, using the local slope m and y-intercept q.
  * first 16 segments: equally spaced y-values between 0.500000 and 0.982014
  * second 4 segments: equally spaced x-values between 4 and 6
  *
  * For negative numbers, sigmoid(x) = 1 - sigmoid(-x) is exploited, halving the needed segments.
  * if abs(numbers) > 6, sigmoid = 0 or 1 depending on sign, so the silu and gelu just return 0 or the input itself.
  * The implementation only supports BF16 floating point representation
  */
class siluandgeluPWLSigmoid20NonUniformSegmentsUsingExtraAdder extends Module {
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
    val sigmoidInput = Cat(0.U(1.W), fpmult0.io.res(14,0)) // the sigmoid gets the absolute value as input
    val exp = sigmoidInput(14,7).asUInt
    val actual_exp = (exp.asSInt - 127.S(8.W)).asSInt // actual_exp can be negative!

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
    // val sigmoidReg = RegInit(0.U(16.W)) // register for the sigmoid value for x > 0
    val fullRangeSigmoidReg = RegInit(0.U(16.W)) // register for the sigmoid value for all x

    val fpmult1 = Module(new FPMult16ALT) // 1cc for slope * abs(x)
    fpmult1.io.a := sigmoidInput
    fpmult1.io.b := slopeReg
    val fpadd1 = Module(new FPAdd16ALT) // 3cc for slope*abs(x) + intercept
    fpadd1.io.a := fpmult1.io.res 
    fpadd1.io.b := interceptReg
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

    }.otherwise { // x*sigmoid(sigmoidInput) with 6.0 > sigmoidInput >= 0.0
        when (in_a_fp < "b010_0000000".U) { // sigmoidInput < 2.0: first four segments
            when (a_int >= "b001".U) { // 2.0 > sigmoidInput >= 1.0
                when (a_frac >= "b1000000".U) { // 2.0 > sigmoidInput >= 1.5
                    slopeReg := "b0011111000000001".U // 0.125977
                    interceptReg := "b0011111100100001".U // 0.628906
                }.otherwise { // 1.5 > sigmoidInput >= 1.0
                    slopeReg := "b0011111000110001".U // 0.172852
                    interceptReg := "b0011111100001111".U // 0.558594
                }
            }.otherwise { 
                when (a_frac >= "b1000000".U) { // 1.0 > sigmoidInput >= 0.5
                    slopeReg := "b0011111001011110".U // 0.216797
                    interceptReg := "b0011111100000100".U // 0.515625
                }.otherwise { // 0.5 > sigmoidInput >= 0.0
                    slopeReg := "b0011111001111011".U // 0.245117
                    interceptReg := "b0011111100000000".U // 0.500000 and q' = 1 - q = 0.500000
                }
            }
        }.otherwise { // 2.0 <= sigmoidInput < 6.0: middle 16 segments 
            when (a_int >= "b100".U) { // sigmoidInput >= 4
                when (a_int >= "b101".U) { // sigmoidInput > 5
                    when (in_a_fp >= "b101_1000000".U) { // sigmoidInput > 5.5
                        when (in_a_fp >= "b101_1100000".U) { // sigmoidInput > 5.75
                            slopeReg := "b0011101100111000".U // 0.002808
                            interceptReg := "b0011111101111011".U // 0.980469
                        }.otherwise { // 5.75 > sigmoidInput >= 5.5
                            slopeReg := "b0011101101101011".U // 0.003586
                            interceptReg := "b0011111101111010".U // 0.976562
                        }
                    }.otherwise {  // 5.5 > sigmoidInput >= 5.0
                        when (in_a_fp >= "b101_0100000".U) { // 5.5 > sigmoidInput > 5.25
                            slopeReg := "b0011101110010111".U // 0.004608
                            interceptReg := "b0011111101111000".U // 0.968750
                        }.otherwise { // 5.25 > sigmoidInput >= 5.0
                            slopeReg := "b0011101111000001".U // 0.005890
                            interceptReg := "b0011111101110111".U // 0.964844
                        }
                    }
                }
                .otherwise { // 5.0 > sigmoidInput >= 4.0
                    when (in_a_fp >= "b100_1000000".U) { // sigmoidInput >= 4.5
                        when (in_a_fp >= "b100_1100000".U) { // 5.0 > sigmoidInput >= 4.75
                            slopeReg := "b0011101111110111".U // 0.007538 // index 12
                            interceptReg := "b0011111101110101".U // 0.957031
                        }.otherwise { // 4.75 > sigmoidInput >= 4.5
                            slopeReg := "b0011110000011110".U // 0.009644
                            interceptReg := "b0011111101110010".U // 0.945312
                        }
                    }.otherwise {  // 4.5 > sigmoidInput >= 4.0
                        when (in_a_fp >= "b100_0100000".U) { // >= 4.25
                            slopeReg := "b0011110001001010".U // 0.012329
                            interceptReg := "b0011111101101111".U // 0.933594
                        }.otherwise { // index 9
                            slopeReg := "b0011110010000001".U // 0.015747
                            interceptReg := "b0011111101101011".U // 0.917969
                        }
                    }
                }
            }.otherwise { // 4.0 > sigmoidInput >= 2.0
                when (a_int >= "b011".U) { // sigmoidInput > 3.0
                    when (in_a_fp >= "b011_1000000".U) { // sigmoidInput > 3.5
                        when (in_a_fp >= "b011_1100000".U) { // 4.0 > sigmoidInput >= 3.75
                            slopeReg := "b0011110010100100".U // 0.020020
                            interceptReg := "b0011111101100111".U // 0.902344
                        }.otherwise { // 3.75 > sigmoidInput >= 3.5 // index 7
                            slopeReg := "b0011110011010000".U // 0.025391
                            interceptReg := "b0011111101100010".U // 0.882812
                        }
                    }.otherwise { 
                        when (in_a_fp >= "b011_0100000".U) { // 3.5 > sigmoidInput >= 3.25
                            slopeReg := "b0011110100000011".U // 0.031982
                            interceptReg := "b0011111101011100".U // 0.859375
                        }.otherwise { // 3.25 > sigmoidInput >= 3.0 // index 5
                            slopeReg := "b0011110100100101".U // 0.040283
                            interceptReg := "b0011111101010101".U // 0.832031
                        }
                    }
                }.otherwise { // 3.0 > sigmoidInput >= 2.0
                    when (a_frac >= "b1000000".U) { // sigmoidInput >= 2.5
                        when (a_frac >= "b1100000".U) { // 3.0 > sigmoidInput >= 2.75
                            slopeReg := "b0011110101001111".U // 0.050537
                            interceptReg := "b0011111101001101".U // 0.800781
                        }.otherwise { // 2.75 > sigmoidInput >= 2.5
                            slopeReg := "b0011110110000001".U // 0.062988
                            interceptReg := "b0011111101000100".U // 0.765625
                        }
                    }.otherwise {
                        when (a_frac >= "b0100000".U) { // 2.5 > sigmoidInput >= 2.25
                            slopeReg := "b0011110110100000".U // 0.078125
                            interceptReg := "b0011111100111011".U // 0.730469
                        }.otherwise { // 2.25 > sigmoidInput >= 2.0
                            slopeReg := "b0011110111000011".U // 0.095215
                            interceptReg := "b0011111100110001".U // 0.691406
                        }
                    }
                }
            }
        }
        when (sign === 0.U) { // 6 > sigmoidInput >= 0
            fullRangeSigmoidReg := sigmoidOut
        }.otherwise { // -6 < sigmoidInput <= -0
            val fpadd2 = Module(new FPAdd16ALT) // 3cc for f(x) = 1-f(-x)
            fpadd2.io.a := "b0_01111111_0000000".U(16.W) // 1.0
            fpadd2.io.b := Cat("b1".U, sigmoidOut(14,0)) // -sigmoidOut
            fullRangeSigmoidReg := fpadd2.io.res
        }
    }
}

/**
 * Generate Verilog sources and save it in generated/siluandgeluPWLSigmoid20NonUniformSegmentsUsingExtraAdder.sv
 * Uncomment to generate the SystemVerilog file when using 'sbt run'
 */
object siluandgeluPWLSigmoid20NonUniformSegmentsUsingExtraAdderMain extends App {
    ChiselStage.emitSystemVerilogFile(
        new siluandgeluPWLSigmoid20NonUniformSegmentsUsingExtraAdder,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated2")
    )
}
