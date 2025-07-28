package silu
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage // needed for ChiselStage.emitSystemVerilogFile

/**
  * Chisel implementation to calculate silu(x)
  * SiLU is approximated using 28 segments each linearly interpolated as y = m*x + q, using the local slope m and y-intercept q.
  * 28 segments: non-equally spaced x-values between -6 and 6
  * if abs(numbers) > 6, silu(x) = 0 or x depending on sign
  * The implementation only supports BF16 inputs and outputs, but uses fixed point representation internally.
  */
class siluPWL28Segments extends Module {
    val io = IO(new Bundle {
        val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
        val out_a = Output(Bits(16.W))
    })
    val a = io.in_a // a must be a BigInt
    val sign = a(15).asUInt
    val bf16tofp = Module(new BF16toFP(3, 2)) // BF16 to Fixed Point converter, 3 bits for integer part and 2 bits for fractional part
    val exp = a(14,7).asUInt
    val actual_exp = (exp.asSInt - 127.S(8.W)).asSInt

    bf16tofp.io.bf16in := a
    val a_int = bf16tofp.io.intout 
    val a_frac = bf16tofp.io.fracout
    val in_a_fp = Cat(a_int, a_frac) // create the input fixed point

    val slopeReg = RegInit(0.U(16.W)) // register for the slope
    val interceptReg = RegInit(0.U(16.W)) // register for the y-intercept
    val outputReg = RegInit(0.U(16.W)) // register for the output

    val fpmult1 = Module(new FPMult16ALT) // 1cc for slope * x
    fpmult1.io.a := a
    fpmult1.io.b := slopeReg
    val fpadd1 = Module(new FPAdd16ALT) // 3cc for slope*x + intercept
    fpadd1.io.a := fpmult1.io.res 
    fpadd1.io.b := interceptReg
    io.out_a := outputReg // the out_a is always the outputReg, now set the outputReg depending on input a

    when (a(14,0) === "b00000000_0000000".U ) { // a = +-0
        outputReg := 0.U
    }.elsewhen (actual_exp >= 3.S || a_int >= "b110".U) { // a <= -6 or a >= +6
        when (sign === 1.U) { // sigmoidInput <= -6
            outputReg := 0.U // sigmoid(sigmoidInput) = 0
        }.otherwise { // in_a >= +6
            outputReg := a // silu(a) = a
        }
    }.otherwise { // 6.0 > a >= -6.0
        when (in_a_fp >= "b011_00".U) { // -abs(a) <= -3.0:
            when (in_a_fp >= "b100_10".U) { // -abs(a) <= -4.5:
                when (in_a_fp >= "b101_10".U) { // 6.0 < -abs(a) <= -5.5: index 1&24
                    when (sign === 1.U) {
                        slopeReg := "b1011110001110111".U // -0.015076
                        interceptReg := "b1011110111011000".U // -0.105469
                    }.otherwise {
                        when (in_a_fp >= "b101_11".U) { // 6 > a > 5.75
                            slopeReg := "b0011111110000010".U // 1.015625
                            interceptReg := "b1011110111000110".U // -0.096680
                        }.otherwise { // 5.75 > a >= 5.5
                            slopeReg := "b0011111110000010".U // 1.015625
                            interceptReg := "b1011110111101001".U // -0.113770
                        }
                    }
                }.otherwise { 
                    when (in_a_fp >= "b101_00".U) { // -5.5 < -abs(a) <= -5.0: index 2&23
                        when (sign === 1.U) {
                            slopeReg := "b1011110010110110".U // -0.022217
                            interceptReg := "b1011111000010100".U // -0.144531
                        }.otherwise {
                            when (in_a_fp >= "b101_01".U) { // 5.5 > a >= 5.25
                                slopeReg := "b0011111110000011".U // 1.023438
                                interceptReg := "b1011111000001000".U // -0.132812
                            }.otherwise { // 5.25 > a >= 5.0
                                slopeReg := "b0011111110000011".U // 1.023438
                                interceptReg := "b1011111000011110".U // -0.154297
                            }
                        }
                    }.otherwise { // -5.0 < -abs(a) <= -4.5: index 3&22
                        when (sign === 1.U) {
                            slopeReg := "b1011110100000011".U // -0.031982
                            interceptReg := "b1011111001000110".U // -0.193359
                        }.otherwise {
                            when (in_a_fp >= "b100_11".U) { // 5.0 > a >= 4.75
                                slopeReg := "b0011111110000100".U // 1.031250
                                interceptReg := "b1011111000110111".U // -0.178711
                            }.otherwise { // 4.74 > a >= 4.5
                                slopeReg := "b0011111110000100".U // 1.031250
                                interceptReg := "b1011111001010011".U // -0.206055
                            }
                            
                        }
                    }
                }
            }.otherwise { 
                when (in_a_fp >= "b100_00".U) { // -4.5 < -abs(a) <= -4.0: index 4&21
                    when (sign === 1.U) {
                        slopeReg := "b1011110100111000".U // -0.044922
                        interceptReg := "b1011111010000001".U // -0.251953
                    }.otherwise {
                        when (in_a_fp >= "b100_01".U) { // 4.5 > a >= 4.25
                            slopeReg := "b0011111110000101".U // 1.039062
                            interceptReg := "b1011111001110001".U // -0.235352
                        }.otherwise { // 4.25 > a >= 4.0
                            slopeReg := "b0011111110000110".U // 1.046875
                            interceptReg := "b1011111010001001".U // -0.267578
                        }
                    }
                }.otherwise { 
                    when (in_a_fp >= "b011_10".U) { // -4.0 < -abs(a) <= -3.5: index 5&20
                        when (sign === 1.U) {
                            slopeReg := "b1011110101111011".U // -0.061279
                            interceptReg := "b1011111010100010".U // -0.316406
                        }.otherwise {
                            slopeReg := "b0011111110001000".U // 1.062500
                            interceptReg := "b1011111010100010".U // -0.316406
                        }
                    }.otherwise { // -3.5 < -abs(a) <= -3.0: index 6&19
                        when (sign === 1.U) {
                            slopeReg := "b1011110110100011".U // -0.079590
                            interceptReg := "b1011111011000011".U // -0.380859
                        }.otherwise{
                            slopeReg := "b0011111110001010".U // 1.078125
                            interceptReg := "b1011111011000011".U // -0.380859
                        }
                    }
                }
            }
        }.otherwise { // -3.0 < -abs(a) <= 0.0
            when (in_a_fp >= "b001_10".U) { // -abs(a) <= -1.5
                when (in_a_fp >= "b010_10".U) { // -3 < -abs(a) <= -2.5: index 7&18
                    when (sign === 1.U) {
                        slopeReg := "b1011110111000010".U // -0.094727
                        interceptReg := "b1011111011011010".U // -0.425781
                    }.otherwise {
                        slopeReg := "b0011111110001100".U // 1.093750
                        interceptReg := "b1011111011011010".U // -0.425781
                    }
                }.otherwise { 
                    when (in_a_fp >= "b010_00".U) { // -2.5 < -abs(a) <= -2.0: index 8&17
                        when (sign === 1.U) {
                            slopeReg := "b1011110111001000".U // -0.097656
                            interceptReg := "b1011111011011110".U // -0.433594
                        }.otherwise {
                            slopeReg := "b0011111110001100".U // 1.093750
                            interceptReg := "b1011111011011110".U // -0.433594
                        }
                    }.otherwise { // -2.0 < -abs(a) <= -1.5: index 9&16
                        when (sign === 1.U) {
                            slopeReg := "b1011110110010000".U // -0.070312
                            interceptReg := "b1011111011000010".U // -0.378906
                        }.otherwise {
                            slopeReg := "b0011111110001001".U // 1.070312
                            interceptReg := "b1011111011000010".U // -0.378906
                        }
                    }
                }
            }.otherwise {  // -1.5 < -abs(a) <= 0
                when (in_a_fp >= "b001_00".U) { // -1.5 < -abs(a) <= -1.0: index 10&15
                    when (sign === 1.U) {
                        slopeReg := "b0011110000011010".U // 0.009399
                        interceptReg := "b1011111010000101".U // -0.259766
                    }.otherwise {
                        slopeReg := "b0011111101111110".U // 0.992188
                        interceptReg := "b1011111010000101".U // -0.259766
                    }
                }.otherwise { 
                    when (in_a_fp >= "b000_10".U) { // -1.0 < -abs(a) <= -0.5: index 11&14
                        when (sign === 1.U) {
                            slopeReg := "b0011111000100100".U // 0.160156
                            interceptReg := "b1011110111011110".U // -0.108398
                        }.otherwise {
                            slopeReg := "b0011111101010111".U // 0.839844
                            interceptReg := "b1011110111011110".U // -0.108398
                        }
                    }.otherwise { // -0.5 < -abs(a) <= 0.0: index 12&13
                        when (sign === 1.U) {
                            slopeReg := "b0011111011000001".U // 0.376953
                            interceptReg := "b0000000000000000".U // 0.000000
                        }.otherwise {
                            slopeReg := "b0011111100011111".U // 0.621094
                            interceptReg := "b0000000000000000".U // 0.000000
                        }
                    }
                }
            }    
        }
        // only when -6 < a < 6 do we connect m*x + q to the output
        outputReg := fpadd1.io.res
    }
    
}

/**
 * Generate Verilog sources and save it in generated/siluPWL28Segments.sv
 * Generate the SystemVerilog file when using 'sbt run'
 */
object siluPWL28Segments extends App {
    ChiselStage.emitSystemVerilogFile(
        new siluPWL28Segments,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated2")
    )
}
