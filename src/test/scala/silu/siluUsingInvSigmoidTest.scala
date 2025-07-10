package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
import math.exp

class siluUsingInvSigmoid32Test extends AnyFreeSpec with Matchers {
    "siluUsingInvSigmoid32Test should correctly apply an approximate SiLU value for a BF16 input, using 32 non-uniformly spaced inverse Sigmoid values in [-8, 8]" in {
        simulate(new siluUsingInvSigmoid32) { c =>
            var tolerance = 0.125f // approximation+quantization errors together
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(2) // latency: 5 comparators with 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x>0
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.clock.step(3) // latency: 5 comparators with 1 indexReg (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x>0
            c.io.out_a.expect("b0_01111111_0011100".U(16.W)) // 1.22636171429

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(2) // latency: 5 comparators with 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x=-0
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.clock.step(6) // latency: 5 comparators with 1 indexReg (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), 1 Add since x<0: (3cc)
            c.io.out_a.expect("b1_01111101_0010000".U(16.W)) // -0.27363828571 ~ -0.28125

            for (_ <- 0 until 50) {
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1]*14-7: -7 to 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(6) // must assume 6cc since inputs can be negative meaning so Adder is needed with 3cc latency
                val expected = (a / (1 + math.exp(-a))).toFloat // SiLU formula
                println(f"input x-value: ${a}")
                println(f"output silu-using-invSigmoid-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact silu value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println("###########")
            }
        }
    }
}