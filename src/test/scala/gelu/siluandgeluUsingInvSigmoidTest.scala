package gelu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import silu.FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
// for gelu
import math.tanh 
import math.Pi
import math.sqrt
// for silu
import math.exp 

class siluandgeluUsingInvSigmoidTest extends AnyFreeSpec with Matchers {
    // #########################################################################################################
    // ##############################        SiLU         ######################################################
    // #########################################################################################################
    "siluandgeluUsingInvSigmoidTest should correctly apply an approximate SiLU value (in_select=0) for a BF16 input, using 32 non-uniformly spaced inverse Sigmoid values in [-8, 8]" in {
        simulate(new siluandgeluUsingInvSigmoid(log2lutsize = 5)) { c =>
            var tolerance = 0.125f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(3) // latency: 5 comparators with 1 mult 1.0*x (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x>0
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(4) // latency: 5 comparators with 1 mult 1.0*x (1cc), 1 indexReg (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x>0
            c.io.out_a.expect("b0_01111111_0011100".U(16.W)) // 1.22636171429 
            // 0011111101000110
            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(3) // latency: 5 comparators with 1 mult 1.0*x (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x=-0
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(7) // latency: 5 comparators with 1 mult 1.0*x (1cc), 1 indexReg (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), 1 Add since x<0: (3cc)
            c.io.out_a.expect("b1_01111101_0010000".U(16.W)) // -0.27363828571 ~ -0.28125

            for (_ <- 0 until 50) {
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1]*14-7: -7 to 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b0".U(1.W))
                c.clock.step(7) // must assume 6cc since inputs can be negative meaning an Adder is needed with 3cc latency
                val expected = (a / (1 + math.exp(-a))).toFloat // SiLU formula
                println(f"input x-value: ${a}")
                println(f"output silu-using-invSigmoid-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact SiLU value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println("###########")
            }
        }
    }

    // #########################################################################################################
    // ##############################        GELU        #######################################################
    // #########################################################################################################
    "siluandgeluUsingInvSigmoidTest should correctly apply an approximate GELU value (in_select=1) for a BF16 input, using 32 non-uniformly spaced inverse Sigmoid values in [-8, 8]" in {
        simulate(new siluandgeluUsingInvSigmoid(log2lutsize = 5)) { c =>
            var tolerance = 0.125f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(3) // latency: 1 mult(1cc), 5 comparators with 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x>0
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(4) // latency: 1 mult(1cc), 5 comparators with 1 indexReg (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x>0
            c.io.out_a.expect("b0_01111111_0110001".U(16.W)) // 1.39978919809671290 ~ 1.3828125

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(3) // latency: 1 mult(1cc), 5 comparators with 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x=-0
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(7) // latency: 1 mult(1cc), 5 comparators with 1 indexReg (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), 1 Add since x<0: (3cc)
            c.io.out_a.expect("b1_01111011_1110000".U(16.W)) // -0.100210801903287099 ~ -0.1171875

            for (_ <- 0 until 50) {
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1]*14-7: -7 to 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b1".U(1.W))
                c.clock.step(7) // must assume 6cc since inputs can be negative meaning an Adder is needed with 3cc latency
                val expected = (a * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a + 0.044715 * math.pow(a,3)))))).toFloat // GELU formula
                println(f"input x-value: ${a}")
                println(f"output gelu-using-invSigmoid-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact GELU value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println("###########")
            }
        }
    }
}