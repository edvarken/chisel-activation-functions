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
class siluandgeluPWLSigmoid20Segments extends Module {
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

    }.otherwise { // x*sigmoid(sigmoidInput)
        when (a_int >= "b100".U) { // one of four segments
            when (a_int >= "b101".U) { // 6 > sigmoidInput >= 5
                when (a_frac >= "b1000000".U) { // 6.0 > sigmoidInput >= 5.5
                    slopeReg := "b0011101101010001".U // 0.003189
                    interceptReg := "b0011111101111010".U // 0.976562
                }.otherwise { // 5.5 > sigmoidInput >= 5.0
                    slopeReg := "b0011101110101100".U // 0.005249
                    interceptReg := "b0011111101111000".U // 0.968750
                }
            }.otherwise { // 5 > sigmoidInput >= 4
                when (a_frac >= "b1000000".U) { // 5.0 > sigmoidInput >= 4.5
                    slopeReg := "b0011110000001101".U // 0.008606
                    interceptReg := "b0011111101110011".U // 0.949219
                }.otherwise { // 4.5 > sigmoidInput >= 4.0
                    slopeReg := "b0011110001100101".U // 0.013977
                    interceptReg := "b0011111101101101".U // 0.925781
                }
            }
        }.otherwise { // one of 16 segments 
            when (in_a_fp >= "b001_0000110".U) {
                when (in_a_fp >= "b010_0001101".U) {
                    when (in_a_fp >= "b010_1111110".U) { // 4 > sigmoidInput >= 2.984375
                        slopeReg := "b0011110011110011".U 
                        interceptReg := "b0011111101011101".U 
                    }.otherwise {
                        when (in_a_fp >= "b010_0111011".U) { // 2.984375 > sigmoidInput >= 2.468750
                            slopeReg := "b0011110101101110".U 
                            interceptReg := "b0011111101000111".U 
                        }.otherwise { // 2.468750 > sigmoidInput >= 2.109375
                            slopeReg := "b0011110110101100".U 
                            interceptReg := "b0011111100110111".U
                        }
                    }
                }
                .otherwise {
                    when (in_a_fp >= "b001_1001100".U) {
                        when (in_a_fp >= "b001_1101001".U) { // 2.109375 > sigmoidInput >= 1.828125
                            slopeReg := "b0011110111011100".U 
                            interceptReg := "b0011111100101010".U
                        }.otherwise { // 1.828125 > sigmoidInput >= 1.593750
                            slopeReg := "b0011111000000101".U 
                            interceptReg := "b0011111100100000".U 
                        }
                    }.otherwise { 
                        when (in_a_fp >= "b001_0110010".U) { // 1.593750 > sigmoidInput >= 1.390625
                            slopeReg := "b0011111000011010".U 
                            interceptReg := "b0011111100011000".U 
                        }.otherwise {
                            when (in_a_fp >= "b001_0011011".U) { // 1.390625 > sigmoidInput >= 1.210938
                                slopeReg := "b0011111000101100".U 
                                interceptReg := "b0011111100010001".U 
                            }.otherwise { // 1.210938 > sigmoidInput >= 1.054688
                                slopeReg := "b0011111000111101".U
                                interceptReg := "b0011111100001100".U
                            }
                        }
                    }
                }
            }.otherwise {
                when (in_a_fp >= "b000_0111110".U) {
                    when (in_a_fp >= "b000_1100000".U) {
                        when (in_a_fp >= "b000_1110011".U) { // 1.054688 > sigmoidInput >= 0.898438
                            slopeReg := "b0011111001001011".U 
                            interceptReg := "b0011111100001000".U 
                        }.otherwise { // 0.898438 > sigmoidInput >= 0.757812
                            slopeReg := "b0011111001011001".U 
                            interceptReg := "b0011111100000101".U
                        }
                    }.otherwise { 
                        when (in_a_fp >= "b000_100111".U) { // 0.757812 > sigmoidInput >= 0.621094
                            slopeReg := "b0011111001100100".U 
                            interceptReg := "b0011111100000011".U 
                        }.otherwise { // 0.621094 > sigmoidInput >= 0.492188
                            slopeReg := "b0011111001101101".U
                            interceptReg := "b0011111100000010".U
                        }
                    }
                }.otherwise {
                    when (in_a_fp >= "b000_0011110".U) { 
                        when (in_a_fp >= "b000_0101110".U) { // 0.492188 > sigmoidInput >= 0.365234
                            slopeReg := "b0011111001110101".U 
                            interceptReg := "b0011111100000001".U 
                        }.otherwise { // 0.365234 > sigmoidInput >= 0.242188
                            slopeReg := "b0011111001111010".U 
                            interceptReg := "b0011111100000000".U
                        }
                    }.otherwise {
                        when (in_a_fp >= "b000_0001111".U) { // 0.242188 > sigmoidInput >= 0.120605
                            slopeReg := "b0011111001111110".U 
                            interceptReg := "b0011111100000000".U 
                        }.otherwise { // 0.120605 > sigmoidInput >= 0.000000
                            slopeReg := "b0_011111010000000".U 
                            interceptReg := "b0_011111100000000".U
                        }
                    }
                }
            }
        }
        when (sign === 0.U) { // 6 > sigmoidInput >= 0
            fullRangeSigmoidReg := sigmoidOut
        }.otherwise { // -8 < sigmoidInput <= -0
            val fpadd2 = Module(new FPAdd16ALT) // 3cc for f(x) = 1-f(-x)
            fpadd2.io.a := "b0_01111111_0000000".U(16.W) // 1.0
            fpadd2.io.b := Cat("b1".U, sigmoidOut(14,0)) // -sigmoidOut
            fullRangeSigmoidReg := fpadd2.io.res
        }
    }
}

/**
 * Generate Verilog sources and save it in generated/siluandgeluPWLSigmoid20Segments.sv
 * Uncomment to generate the SystemVerilog file when using 'sbt run'
 */
object siluandgeluPWLSigmoid20SegmentsMain extends App {
    ChiselStage.emitSystemVerilogFile(
        new siluandgeluPWLSigmoid20Segments,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated2")
    )
}
