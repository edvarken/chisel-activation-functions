package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
import math.exp

class siluTest extends AnyFreeSpec with Matchers {
    "siluTest should correctly apply approximate SiLU function on BF16 numbers" in {
        simulate(new silu) { c =>
            var tolerance = 0.15f // silu approximation's error never gets bigger than 0.15f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            for (_ <- 0 until 30) {
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1] * 14 - 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(5) // 3 clock cycles for the adder, 2 clock cycles for the 2 multipliers

                val expected = (a / (1 + math.exp(-a))).toFloat // SiLU formula

                println(f"out_a: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected: ${expected}")
                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${floatToBigIntBF16(expected).U(16.W)} but got ${c.io.out_a.peek().litValue.toInt}")
                println("###########")
            }
        }
    }
}