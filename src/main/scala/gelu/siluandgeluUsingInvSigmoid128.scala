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
  * the inverted LUT is configurable to contain the input values that correspond to 128 equally spaced output values between [0.5 and 1.0]
  * effectively storing the inverse function: x = f^-1(y), for equally spaced y values.
  * For smaller and large numbers outside the range, the function returns 0 or the input itself respectively.
  * The implementation only supports BF16 floating point representation
  */
class siluandgeluUsingInvSigmoid128 extends Module {
    val io = IO(new Bundle {
        val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
        val in_select = Input(UInt(1.W)) // 0 for SiLU, 1 for GELU
        val out_a = Output(Bits(16.W))
    })
    val log2edgerange = 3 // log2edgerange always 3, this means the range is always [-8, 8] for BF16
    val log2lutsize = 7 // log2lutsize is 7, this means the LUT has 128 entries
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
        fpmult1.io.b := "b0_01111111_1011010".U // 1.703125
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
                            when (in_a_fp >= "b100_1101100".U) {
                                when (in_a_fp >= "b101_1000101".U) {
                                    index := 127.U(7.W) // 127
                                }.otherwise {
                                    index := 126.U(7.W) // 126
                                }
                            }.otherwise {
                                when (in_a_fp >= "b100_0110111".U) {
                                    index := 125.U(7.W) // 125
                                }.otherwise {
                                    index := 124.U(7.W) // 124
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b011_1011101".U) {
                                when (in_a_fp >= "b011_1110101".U) {
                                    index := 123.U(7.W) // 123
                                }.otherwise {
                                    index := 122.U(7.W) // 122
                                }
                            }.otherwise {
                                when (in_a_fp >= "b011_1001001".U) {
                                    index := 121.U(7.W) // 121
                                }.otherwise {
                                    index := 120.U(7.W) // 120
                                }
                            }
                        }
                    }.otherwise {
                        when (in_a_fp >= "b011_0000001".U) {
                            when (in_a_fp >= "b011_0011001".U) {
                                when (in_a_fp >= "b011_0100111".U) {
                                    index := 119.U(7.W) // 119
                                }.otherwise {
                                    index := 118.U(7.W) // 118
                                }
                            }.otherwise {
                                when (in_a_fp >= "b011_0001101".U) {
                                    index := 117.U(7.W) // 117
                                }.otherwise {
                                    index := 116.U(7.W) // 116
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b010_1101100".U) {
                                when (in_a_fp >= "b010_1110110".U) {
                                    index := 115.U(7.W) // 115
                                }.otherwise {
                                    index := 114.U(7.W) // 114
                                }
                            }.otherwise {
                                when (in_a_fp >= "b010_1100011".U) {
                                    index := 113.U(7.W) // 113
                                }.otherwise {
                                    index := 112.U(7.W) // 112
                                }
                            }
                        }
                    }
                }.otherwise {
                    when (in_a_fp >= "b010_0100010".U) {
                        when (in_a_fp >= "b010_0111011".U) {
                            when (in_a_fp >= "b010_1001010".U) {
                                when (in_a_fp >= "b010_1010010".U) {
                                    index := 111.U(7.W) // 111
                                }.otherwise {
                                    index := 110.U(7.W) // 110
                                }
                            }.otherwise {
                                when (in_a_fp >= "b010_1000011".U) {
                                    index := 109.U(7.W) // 109
                                }.otherwise {
                                    index := 108.U(7.W) // 108
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b010_0101110".U) {
                                when (in_a_fp >= "b010_0110101".U) {
                                    index := 107.U(7.W) // 107
                                }.otherwise {
                                    index := 106.U(7.W) // 106
                                }
                            }.otherwise {
                                when (in_a_fp >= "b010_0101000".U) {
                                    index := 105.U(7.W) // 105
                                }.otherwise {
                                    index := 104.U(7.W) // 104
                                }
                            }
                        }
                    }.otherwise {
                        when (in_a_fp >= "b010_0001100".U) {
                            when (in_a_fp >= "b010_0010111".U) {
                                when (in_a_fp >= "b010_0011100".U) {
                                    index := 103.U(7.W) // 103
                                }.otherwise {
                                    index := 102.U(7.W) // 102
                                }
                            }.otherwise {
                                when (in_a_fp >= "b010_0010001".U) {
                                    index := 101.U(7.W) // 101
                                }.otherwise {
                                    index := 100.U(7.W) // 100
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b010_0000010".U) {
                                when (in_a_fp >= "b010_0000111".U) {
                                    index := 99.U(7.W) // 99
                                }.otherwise {
                                    index := 98.U(7.W) // 98
                                }
                            }.otherwise {
                                when (in_a_fp >= "b001_1111101".U) {
                                    index := 97.U(7.W) // 97
                                }.otherwise {
                                    index := 96.U(7.W) // 96
                                }
                            }
                        }
                    }
                }
            }.otherwise {
                when (in_a_fp >= "b001_0111011".U) {
                    when (in_a_fp >= "b001_1010111".U) {
                        when (in_a_fp >= "b001_1100111".U) {
                            when (in_a_fp >= "b001_1110000".U) {
                                when (in_a_fp >= "b001_1110100".U) {
                                    index := 95.U(7.W)
                                }.otherwise {
                                    index := 94.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b001_1101011".U) {
                                    index := 93.U(7.W)
                                }.otherwise {
                                    index := 92.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b001_1011111".U) {
                                when (in_a_fp >= "b001_1100011".U) {
                                    index := 91.U(7.W)
                                }.otherwise {
                                    index := 90.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b001_1011011".U) {
                                    index := 89.U(7.W)
                                }.otherwise {
                                    index := 88.U(7.W)
                                }
                            }
                        }
                    }.otherwise {
                        when (in_a_fp >= "b001_1001001".U) {
                            when (in_a_fp >= "b001_1010000".U) {
                                when (in_a_fp >= "b001_1010100".U) {
                                    index := 87.U(7.W)
                                }.otherwise {
                                    index := 86.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b001_1001100".U) {
                                    index := 85.U(7.W)
                                }.otherwise {
                                    index := 84.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b001_1000010".U) {
                                when (in_a_fp >= "b001_1000101".U) {
                                    index := 83.U(7.W)
                                }.otherwise {
                                    index := 82.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b001_0111110".U) {
                                    index := 81.U(7.W)
                                }.otherwise {
                                    index := 80.U(7.W)
                                }
                            }
                        }
                    }
                }.otherwise{
                    when (in_a_fp >= "b001_0100010".U) {
                        when (in_a_fp >= "b001_0101110".U) {
                            when (in_a_fp >= "b001_0110101".U) {
                                when (in_a_fp >= "b001_0111000".U) {
                                    index := 79.U(7.W)
                                }.otherwise {
                                    index := 78.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b001_0110010".U) {
                                    index := 77.U(7.W)
                                }.otherwise {
                                    index := 76.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b001_0101000".U) {
                                when (in_a_fp >= "b001_0101011".U) {
                                    index := 75.U(7.W)
                                }.otherwise {
                                    index := 74.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b001_0100101".U) {
                                    index := 73.U(7.W)
                                }.otherwise {
                                    index := 72.U(7.W)
                                }
                            }
                        }
                    }.otherwise {
                        when (in_a_fp >= "b001_0010111".U) {
                            when (in_a_fp >= "b001_0011101".U) {
                                when (in_a_fp >= "b001_0100000".U) {
                                    index := 71.U(7.W)
                                }.otherwise {
                                    index := 70.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b001_0011010".U) {
                                    index := 69.U(7.W)
                                }.otherwise {
                                    index := 68.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b001_0010010".U) {
                                when (in_a_fp >= "b001_0010100".U) {
                                    index := 67.U(7.W)
                                }.otherwise {
                                    index := 66.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b001_0001111".U) {
                                    index := 65.U(7.W)
                                }.otherwise {
                                    index := 64.U(7.W)
                                }
                            }
                        }
                    }
                }
            }
        }.otherwise {
            when (in_a_fp >= "b000_1000001".U) {
                when (in_a_fp >= "b000_1100100".U) {
                    when (in_a_fp >= "b000_1111000".U) {
                        when (in_a_fp >= "b001_0000010".U) {
                            when (in_a_fp >= "b001_0000111".U) {
                                when (in_a_fp >= "b001_0001001".U) {
                                    index := 63.U(7.W)
                                }.otherwise {
                                    index := 62.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b001_0000100".U) {
                                    index := 61.U(7.W)
                                }.otherwise {
                                    index := 60.U(7.W)
                                }
                            } 
                        }.otherwise {
                            when (in_a_fp >= "b000_1111101".U) {
                                when (in_a_fp >= "b000_1111111".U) {
                                    index := 59.U(7.W)
                                }.otherwise {
                                    index := 58.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_1111010".U) {
                                    index := 57.U(7.W)
                                }.otherwise {
                                    index := 56.U(7.W)
                                }
                            }
                        }
                    }.otherwise {
                        when (in_a_fp >= "b000_1101110".U) {
                            when (in_a_fp >= "b000_1110011".U) {
                                when (in_a_fp >= "b000_1110101".U) {
                                    index := 55.U(7.W)
                                }.otherwise {
                                    index := 54.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_1110000".U) {
                                    index := 53.U(7.W)
                                }.otherwise {
                                    index := 52.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b000_1101001".U) {
                                when (in_a_fp >= "b000_1101011".U) {
                                    index := 51.U(7.W)
                                }.otherwise {
                                    index := 50.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_1100111".U) {
                                    index := 49.U(7.W)
                                }.otherwise {
                                    index := 48.U(7.W)
                                }
                            }
                        }
                    }
                }.otherwise {
                    when (in_a_fp >= "b000_1010010".U) {
                        when (in_a_fp >= "b000_1011011".U) {
                            when (in_a_fp >= "b000_1100000".U) {
                                when (in_a_fp >= "b000_1100010".U) {
                                    index := 47.U(7.W)
                                }.otherwise {
                                    index := 46.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_1011110".U) {
                                    index := 45.U(7.W)
                                }.otherwise {
                                    index := 44.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b000_1010111".U) {
                                when (in_a_fp >= "b000_1011001".U) {
                                    index := 43.U(7.W)
                                }.otherwise {
                                    index := 42.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_1010100".U) {
                                    index := 41.U(7.W)
                                }.otherwise {
                                    index := 40.U(7.W)
                                }
                            }
                        }
                    }.otherwise {
                        when (in_a_fp >= "b000_1001001".U) {
                            when (in_a_fp >= "b000_1001110".U) {
                                when (in_a_fp >= "b000_1010000".U) {
                                    index := 39.U(7.W)
                                }.otherwise {
                                    index := 38.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_1001100".U) {
                                    index := 37.U(7.W)
                                }.otherwise {
                                    index := 36.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b000_1000101".U) {
                                when (in_a_fp >= "b000_1000111".U) {
                                    index := 35.U(7.W)
                                }.otherwise {
                                    index := 34.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_1000011".U) {
                                    index := 33.U(7.W)
                                }.otherwise {
                                    index := 32.U(7.W)
                                }
                            }
                        }
                    }
                }
            }.otherwise {
                when (in_a_fp >= "b000_0100000".U) {
                    when (in_a_fp >= "b000_0110000".U) {
                        when (in_a_fp >= "b000_0111000".U) {
                            when (in_a_fp >= "b000_0111101".U) {
                                when (in_a_fp >= "b000_0111111".U) {
                                    index := 31.U(7.W)
                                }.otherwise {
                                    index := 30.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_0111011".U) {
                                    index := 29.U(7.W)
                                }.otherwise {
                                    index := 28.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b000_0110100".U) {
                                when (in_a_fp >= "b000_0110110".U) {
                                    index := 27.U(7.W)
                                }.otherwise {
                                    index := 26.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_0110010".U) {
                                    index := 25.U(7.W)
                                }.otherwise {
                                    index := 24.U(7.W)
                                }
                            }
                        }
                    }.otherwise {
                        when (in_a_fp >= "b000_0101000".U) {
                            when (in_a_fp >= "b000_0101100".U) {
                                when (in_a_fp >= "b000_0101110".U) {
                                    index := 23.U(7.W)
                                }.otherwise {
                                    index := 22.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_0101010".U) {
                                    index := 21.U(7.W)
                                }.otherwise {
                                    index := 20.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b000_0100100".U) {
                                when (in_a_fp >= "b000_0100110".U) {
                                    index := 19.U(7.W)
                                }.otherwise {
                                    index := 18.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_0100010".U) {
                                    index := 17.U(7.W)
                                }.otherwise {
                                    index := 16.U(7.W)
                                }
                            }
                        }
                    }
                }.otherwise{
                    when (in_a_fp >= "b000_0010000".U) {
                        when (in_a_fp >= "b000_0011000".U) {
                            when (in_a_fp >= "b000_0011100".U) {
                                when (in_a_fp >= "b000_0011110".U) {
                                    index := 15.U(7.W)
                                }.otherwise {
                                    index := 14.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_0011010".U) {
                                    index := 13.U(7.W)
                                }.otherwise {
                                    index := 12.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b000_0010100".U) {
                                when (in_a_fp >= "b000_0010110".U) {
                                    index := 11.U(7.W)
                                }.otherwise {
                                    index := 10.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_0010010".U) {
                                    index := 9.U(7.W)
                                }.otherwise {
                                    index := 8.U(7.W)
                                }
                            }
                        }
                    }.otherwise {
                        when (in_a_fp >= "b000_0001000".U) {
                            when (in_a_fp >= "b000_0001100".U) {
                                when (in_a_fp >= "b000_0001110".U) {
                                    index := 7.U(7.W)
                                }.otherwise {
                                    index := 6.U(7.W)
                                }
                            }.otherwise {
                                when (in_a_fp >= "b000_0001010".U) {
                                    index := 5.U(7.W)
                                }.otherwise {
                                    index := 4.U(7.W)
                                }
                            }
                        }.otherwise {
                            when (in_a_fp >= "b000_0000100".U) {
                                when (in_a_fp >= "b000_0000110".U) {
                                    index := 3.U(7.W)
                                }.otherwise {
                                    index := 2.U(7.W)
                                }
                            }.otherwise {
                                index := 1.U(7.W)
                            }
                        }
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
 * Generate Verilog sources and save it in generated/siluandgeluUsingInvSigmoid128.sv
 * Uncomment to generate the SystemVerilog file when using 'sbt run'
 */
object siluandgeluUsingInvSigmoid128Main extends App {
    ChiselStage.emitSystemVerilogFile(
        new siluandgeluUsingInvSigmoid128,
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
        args = Array("--target-dir", "generated2")
    )
}
