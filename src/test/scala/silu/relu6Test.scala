package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}

class relu6Test extends AnyFreeSpec with Matchers {
    "relu6Test should correctly apply relu6 on BF16 numbers" in {
        simulate(new relu6) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')

            c.io.in_a.poke("b0_00000000_0000000".asUInt(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".asUInt(16.W))

            for (_ <- 0 until 30) {
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1] * 14 - 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                if (a < 0) {
                    println(f"Negative float, a_upper16bits: ${toBinary(a_upper16bits.litValue.toInt, 16)}")
                }
                else {
                    println(f"Positive float, a_upper16bits: ${toBinary(a_upper16bits.litValue.toInt, 16)}")
                }
                c.clock.step(1)
                
                if (a < 0) {
                    val expected = 0.0f
                    val expected_upper16bits = (floatToBigInt(expected).toInt >> 16) & 0xFFFF
                    println(f"expected: ${expected}, expected_upper16bits: ${toBinary(expected_upper16bits, 16)}")
                    c.io.out_a.expect(expected_upper16bits.U(16.W))
                    // subtract expected from c.io.out_a to get the difference
                    // val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                    // println(f"Difference: ${diff}")
                    
                } else if (a >= 6.0f) {
                    val expected = 6.0f
                    val expected_upper16bits = (floatToBigInt(expected).toInt >> 16) & 0xFFFF
                    println(f"expected: ${expected}, expected_upper16bits: ${toBinary(expected_upper16bits, 16)}")
                    c.io.out_a.expect(expected_upper16bits.U(16.W))
                } else {
                    val expected = a
                    val expected_upper16bits = (floatToBigInt(expected).toInt >> 16) & 0xFFFF
                    println(f"expected: ${expected}, expected_upper16bits: ${toBinary(expected_upper16bits, 16)}")
                    c.io.out_a.expect(expected_upper16bits.U(16.W))
                }
                println(f"out_a_bits: ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println(f"out_a: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println("################################################")
            }
        }
    }
}