package silu
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage // needed for ChiselStage.emitSystemVerilogFile

/**
  * Chisel implementation to calculate silu(x)
  * SiLU is approximated using 30 segments each linearly interpolated as y = m*x + q, using the local slope m and y-intercept q.
  * 30 segments: non-uniformly spaced x-values between -6 and 6
  * if abs(numbers) > 6, silu(x) = 0 or x depending on sign
  * The implementation only supports BF16 inputs and outputs, but uses fixed point representation internally.
  */
class siluPWL30SegmentsDetailAroundZero extends Module {
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
    }.otherwise { // 6.0 > -abs(a) >= -6.0
        when (in_a_fp >= "b011_00".U) { // -abs(a) <= -3.0:
            when (in_a_fp >= "b100_10".U) { // -abs(a) <= -4.5:
                when (in_a_fp >= "b101_10".U) { // 6.0 < -abs(a) <= -5.5: index 1&24
                    when (sign === 1.U) {
                        slopeReg := "b1011110001110111".U // -0.015076
                        interceptReg := "b1011110111011000".U // -0.105469
                    }.otherwise{
                        slopeReg := "b0011111110000010".U // 1.015625
                        interceptReg := "b1011110111011000".U // -0.105469
                    }
                }.otherwise { 
                    when (in_a_fp >= "b101_00".U) { // -5.5 < -abs(a) <= -5.0: index 2&23
                        when (sign === 1.U) {
                            slopeReg := "b1011110010110110".U // -0.022217
                            interceptReg := "b1011111000010100".U // -0.144531
                        }.otherwise {
                            slopeReg := "b0011111110000011".U // 1.023438
                            interceptReg := "b1011111000010100".U // -0.144531
                        }
                    }.otherwise { // -5.0 < -abs(a) <= -4.5: index 3&22
                        when (sign === 1.U) {
                            slopeReg := "b1011110100000011".U // -0.031982
                            interceptReg := "b1011111001000110".U // -0.193359
                        }.otherwise {
                            slopeReg := "b0011111110000100".U // 1.031250
                            interceptReg := "b1011111001000110".U // -0.193359
                        }
                    }
                }
            }.otherwise { 
                when (in_a_fp >= "b100_00".U) { // -4.5 < -abs(a) <= -4.0: index 4&21
                    when (sign === 1.U) {
                        slopeReg := "b1011110100111000".U // -0.044922
                        interceptReg := "b1011111010000001".U // -0.251953
                    }.otherwise {
                        slopeReg := "b0011111110000110".U // 1.046875
                        interceptReg := "b1011111010000001".U // -0.251953
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
                        }.otherwise { // 2.0 > a >= 1.5
                            when (in_a_fp >= "b001_11".U) { // 2.0 > a >= 1.75
                                slopeReg := "b0011111110001011".U // 1.085938
                                interceptReg := "b1011111011001111".U // -0.404297
                            }.otherwise { // 1.75 > a >= 1.5
                                slopeReg := "b0011111110000111".U // 1.054688
                                interceptReg := "b1011111010111001".U // -0.361328
                            }
                        }
                    }
                }
            }.otherwise {  // -1.5 < -abs(a) <= 0
                when (in_a_fp >= "b001_00".U) { // -1.5 < -abs(a) <= -1.0: index 10&15
                    when (sign === 1.U) {
                        slopeReg := "b0011110000011010".U // 0.009399
                        interceptReg := "b1011111010000101".U // -0.259766
                    }.otherwise { // 1.5 > a >= 1.0
                        when (in_a_fp >= "b001_01".U) { // 1.5 > a >= 1.25
                            slopeReg := "b0011111110000010".U // 1.015625
                            interceptReg := "b1011111010011011".U // -0.302734
                        }.otherwise { // 1.25 > a >= 1.0
                            slopeReg := "b0011111101110110".U // 0.960938
                            interceptReg := "b1011111001101101".U // -0.231445
                        }
                    } 
                }.otherwise { 
                    when (in_a_fp >= "b000_10".U) { // -1.0 < -abs(a) <= -0.5: index 11&14
                        when (in_a_fp >= "b000_11".U) { // abs(a) >= 0.75
                            when (sign === 1.U) {
                                slopeReg := "b0011110111101000".U // 0.113281
                                interceptReg := "b1011111000011111".U // -0.155273
                            }.otherwise {
                                slopeReg := "b0011111101100011".U // 0.886719
                                interceptReg := "b1011111000011111".U // -0.155273
                            }
                        }.otherwise{ // 0.75 > abs(a) >= 0.5
                            when (sign === 1.U) {
                                slopeReg := "b0011111001010100".U // 0.207031
                                interceptReg := "b1011110110101110".U // -0.084961
                            }.otherwise {
                                slopeReg := "b0011111101001011".U // 0.792969
                                interceptReg := "b1011110110101110".U // -0.084961
                            }
                        }
                        
                    }.otherwise { // -0.5 < -abs(a) <= 0.0: index 12&13
                        when (in_a_fp >= "b000_01".U) { // 0.5 > abs(a) >= 0.25
                            when (sign === 1.U) {
                                slopeReg := "b0011111010100010".U // 0.316406
                                interceptReg := "b1011110011110111".U // -0.030151
                            }.otherwise {
                                slopeReg := "b0011111100101111".U // 0.683594
                                interceptReg := "b1011110011110111".U // -0.030151
                            }
                        }.otherwise { // 0.25 > abs(a) >= 0.0
                            when (sign === 1.U) {
                                slopeReg := "b0011111011100000".U // 0.437500
                                interceptReg := "b0000000000000000".U // 0.000000
                            }.otherwise {
                                slopeReg := "b0011111100010000".U // 0.562500
                                interceptReg := "b0000000000000000".U // 0.000000
                            }
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
 * Generate Verilog sources and save it in generated/siluPWL24Segments.sv
 * Generate the SystemVerilog file when using 'sbt run'
 */
object siluPWL30DetailAroundZeroSegmentsMain extends App {
    ChiselStage.emitSystemVerilogFile(
        new siluPWL30SegmentsDetailAroundZero,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated2")
    )
}
