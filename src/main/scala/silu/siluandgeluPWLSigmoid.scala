package silu
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage // needed for ChiselStage.emitSystemVerilogFile

/**
  * Chisel implementation to calculate silu(x) = x * sigmoid(x), and gelu(x) ~ x * sigmoid(1.702x)
  * sigmoid is approximated using 24 segments each linearly interpolated as y = m*x + q, using the local slope m and y-intercept q.
  * first 8 segments: equally spaced y-values between 0.500000 and 0.982014
  * second 4 segments: equally spaced x-values between 4 and 8
  *
  * For negative numbers, sigmoid(x) = 1 - sigmoid(-x) is exploited, halving the needed segments.
  * if abs(numbers) > 8, sigmoid = 0 or 1 depending on sign, so the silu and gelu just return 0 or the input itself.
  * The implementation only supports BF16 floating point representation
  */
class siluandgeluPWLSigmoid extends Module {
    val io = IO(new Bundle {
        val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
        val in_select = Input(UInt(1.W)) // 0 for SiLU, 1 for GELU
        val out_a = Output(Bits(16.W))
    })
    val a = io.in_a // a must be a BigInt
    val sign = a(15).asUInt
    val exp = a(14,7).asUInt
    val actual_exp = (exp.asSInt - 127.S(8.W)).asSInt // actual_exp can be negative!

    val bf16tofp = Module(new BF16toFP(3, 7)) // BF16 to Fixed Point converter, 3 bits for integer part and 7 bits for fractional part

    val fpmult0 = Module(new FPMult16ALT) // 1cc for x*1 or x*1.703125
    fpmult0.io.a := a
    val sigmoidInput = Cat(0.U(1.W), fpmult0.io.res(14,0)) // the sigmoid gets the absolute value as input

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

    val fpmult1 = Module(new FPMult16ALT) // 1cc for slope * x
    fpmult1.io.a := sigmoidInput
    fpmult1.io.b := slopeReg
    val fpadd1 = Module(new FPAdd16ALT) // 3cc for slope*x + intercept
    fpadd1.io.a := fpmult1.io.res 
    fpadd1.io.b := interceptReg
    val sigmoidOut = fpadd1.io.res // no reg, just a wire

    val fpmult2 = Module(new FPMult16ALT) // 1cc for final multiplication
    fpmult2.io.a := a
    fpmult2.io.b := fullRangeSigmoidReg
    io.out_a := fpmult2.io.res // SiLU(x) = x * sigmoid(x), GELU(x) = x * sigmoid(1.702x)

    when (a(14,0) === "b00000000_0000000".U ) { // in_a = +-0
        fullRangeSigmoidReg := 0.U
    }.elsewhen (actual_exp >= 3.S) { // in_a <= -8 or >= +8
        when (sign === 1.U) { // in_a <= -8
            fullRangeSigmoidReg := 0.U // sigmoid(a) = 0
        }.otherwise { // in_a >= +8
            fullRangeSigmoidReg := "b0_01111111_0000000".U(16.W) // sigmoid(a) = 1
        }

    }.otherwise { // x*sigmoid(x)
        when (a_int >= "b100".U) { // one of four segments
            when (a_int >= "b110".U) {
                when (a_int >= "b111".U) { // 8 > a >= 7
                    slopeReg := "b0_01110100_0010111".U // 0.000576
                    interceptReg := "b0_01111110_1111111".U // 0.996094
                }.otherwise { // 7 > a >= 6
                    slopeReg := "b0_01110101_1001101".U // 0.001564
                    interceptReg := "b0_01111110_1111101".U // 0.988281
                }
            }.otherwise { 
                when (a_int >= "b101".U) { // 6 > a >= 5
                    slopeReg := "b0_01110111_0001010".U // 0.004211
                    interceptReg := "b0_01111110_1111001".U // 0.972656
                }.otherwise { // 5 > a >= 4
                    slopeReg := "b0_01111000_0111001".U // 0.011292
                    interceptReg := "b0_01111110_1110000".U // 0.937500
                }
            }

        }.otherwise { // one of eight segments 
            when (in_a_fp >= "b001_0000110".U) {
                when (in_a_fp >= "b001_1101001".U) {
                    when (in_a_fp >= "b010_0111011".U) { // 4 > a >= 2.468750
                        slopeReg := "b0_01111010_0100001".U // 0.039307
                        interceptReg := "b0_01111110_1010011".U // 0.824219
                    }.otherwise { // 2.468750 > a >= 1.828125
                        slopeReg := "b0_01111011_1000001".U // 0.094238
                        interceptReg := "b0_01111110_0110000".U // 0.687500
                    }
                }
                .otherwise {
                    when (in_a_fp >= "b001_0110010".U) { // 1.828125 > a >= 1.390625
                        slopeReg := "b0_01111100_0001110".U // 0.138672
                        interceptReg := "b0_01111110_0011011".U // 0.605469
                    }.otherwise { // 1.390625 > a >= 1.054688
                        slopeReg := "b0_01111100_0110100".U // 0.175781
                        interceptReg := "b0_01111110_0001110".U // 0.554688
                    }
                }
            }.otherwise {
                when (in_a_fp >= "b000_0111110".U) {
                    when (in_a_fp >= "b000_1100000".U) { // 1.054688 > a >= 0.757812
                        slopeReg := "b0_01111100_1010010".U // 0.205078
                        interceptReg := "b0_01111110_0000111".U // 0.527344
                    }.otherwise { // 0.757812 > a >= 0.492188
                        slopeReg := "b0_01111100_1101000".U // 0.226562
                        interceptReg := "b0_01111110_0000010".U // 0.507812
                    }
                }.otherwise {
                    when (in_a_fp >= "b000_0011110".U) { // 0.492188 > a >= 0.242188
                        slopeReg := "b0_01111100_1110111".U // 0.241211
                        interceptReg := "b0_01111110_0000000".U // 0.500000
                    }.otherwise { // 0.242188 > a >= 0.000000
                        slopeReg := "b0_01111100_1111111".U // 0.249023
                        interceptReg := "b0_01111110_0000000".U // 0.500000
                    }
                }
            }
        }
        
        when (sign === 0.U) { // 8 > in_a >= 0
            fullRangeSigmoidReg := sigmoidOut
        }.otherwise { // -8 < in_a <= -0
            val fpadd2 = Module(new FPAdd16ALT) // 3cc for f(x) = 1-f(-x)
            fpadd2.io.a := "b0_01111111_0000000".U(16.W) // 1.0
            fpadd2.io.b := Cat("b1".U, sigmoidOut(14,0)) // -sigmoidOut
            fullRangeSigmoidReg := fpadd2.io.res
        }
    }
}

/**
 * Generate Verilog sources and save it in generated/siluandgeluPWLSigmoid.sv
 * Uncomment to generate the SystemVerilog file when using 'sbt run'
 * Change log2lutsize to generate for other LUT depths.
 */
object siluandgeluPWLSigmoidMain extends App {
    ChiselStage.emitSystemVerilogFile(
        new siluandgeluPWLSigmoid,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated2")
    )
}
