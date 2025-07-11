package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
import math.exp

class siluUsingLUTTest extends AnyFreeSpec with Matchers {
    var verbose = 0 
    var max_test_value = 8.0f
    var N = 300
    println(f"${N} inputs in the range: [-${max_test_value}, ${max_test_value}]")
    "siluUsingLUTTest should correctly apply an approximate SiLU value using a Lookup Table with 128 entries for [-4, 4] on BF16 input numbers" in {
        simulate(new siluUsingLUT(intBits = 2, fracBits = 4)) { c =>
            var tolerance = 0.015625f*8
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))
            var mse = 0.0f // mean squared error accumulator
            for (_ <- 0 until N) {
                val a = scala.util.Random.nextFloat() * 2*max_test_value - max_test_value // [0,1]*14-7: -7 to 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in siluUsingLUT.scala, this is a lot less than silu.scala which has 5 clock cycles latency!!
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float / (1 + math.exp(-a_upper16bits_float))).toFloat // SiLU formula
                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output silu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact silu value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                mse += diff * diff
            }
            mse /= N.toFloat
            println(f"SiLU LUT (128 entries for [-4, 4]): Mean Squared Error (MSE): ${mse}")
        }
    }

    "siluUsingLUTTest should correctly apply an approximate SiLU value using a Lookup Table with 256 entries for [-4, 4] on BF16 input numbers" in {
        simulate(new siluUsingLUT(intBits = 2, fracBits = 5)) { c =>
            var tolerance = 0.015625f*6
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))
            var mse = 0.0f
            for (_ <- 0 until N) {
                val a = scala.util.Random.nextFloat() * 2*max_test_value - max_test_value
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float / (1 + math.exp(-a_upper16bits_float))).toFloat
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output silu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact silu value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                mse += diff * diff
            }
            mse /= N.toFloat
            println(f"SiLU LUT (256 entries for [-4, 4]): Mean Squared Error (MSE): ${mse}")
        }
    }

    "siluUsingLUTTest should correctly apply an approximate SiLU value using a Lookup Table with 256 entries for [-8, 8] on BF16 input numbers" in {
        simulate(new siluUsingLUT(intBits = 3, fracBits = 4)) { c =>
            var tolerance = 0.015625f*6
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))
            var mse = 0.0f
            for (_ <- 0 until N) {
                val a = scala.util.Random.nextFloat() * 2*max_test_value - max_test_value
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float / (1 + math.exp(-a_upper16bits_float))).toFloat
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output silu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact silu value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                mse += diff * diff
            }
            mse /= N.toFloat    
            println(f"SiLU LUT (256 entries for [-8, 8]): Mean Squared Error (MSE): ${mse}")
        }
    }

    "siluUsingLUTTest should correctly apply an approximate SiLU value using a Lookup Table with 512 entries for [-8, 8] on BF16 input numbers" in {
        simulate(new siluUsingLUT(intBits = 3, fracBits = 5)) { c =>
            var tolerance = 0.0078125f*8
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))
            var mse = 0.0f
            for (_ <- 0 until N) {
                val a = scala.util.Random.nextFloat() * 2*max_test_value - max_test_value
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float / (1 + math.exp(-a_upper16bits_float))).toFloat
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output silu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact silu value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                mse += diff * diff
            }
            mse /= N.toFloat    
            println(f"SiLU LUT (512 entries for [-8, 8]): Mean Squared Error (MSE): ${mse}")
        }
    }
}