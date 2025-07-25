package gelu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import silu.FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
import math.tanh
import math.Pi
import math.sqrt

class geluUsingLUTTest extends AnyFreeSpec with Matchers {
    var verbose = 0 
    var max_test_value = 8.0f
    var N = 200
    println(f"${N} inputs in the range: [-${max_test_value}, ${max_test_value}]")
    "geluUsingLUTTest should correctly apply an approximate GELU value using a Lookup Table with 128 entries for [-4, 4] on BF16 input numbers" in {
        simulate(new geluUsingLUT(intBits = 2, fracBits = 4)) { c =>
            var tolerance = 0.015625f*8
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f // keeps track of maximum absolute error
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in geluUsingLUT.scala
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                // GELU 'nearly exact' formula:
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat
                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a}")
                    println(f"output gelu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact gelu value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"GELU LUT (128 entries in [-4, 4]): Mean Squared Error (MSE): ${mse}")
            println(f"GELU LUT (128 entries in [-4, 4]): Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU LUT (128 entries in [-4, 4]): Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "geluUsingLUTTest should correctly apply an approximate GELU value using a Lookup Table with 256 entries for [-4, 4] on BF16 input numbers" in {
        simulate(new geluUsingLUT(intBits = 2, fracBits = 5)) { c =>
            var tolerance = 0.0078125f*8
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in geluUsingLUT.scala
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                // GELU 'nearly exact' formula:
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat
                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a}")
                    println(f"output gelu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact gelu value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"GELU LUT (256 entries in [-4, 4]): Mean Squared Error (MSE): ${mse}")
            println(f"GELU LUT (256 entries in [-4, 4]): Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU LUT (256 entries in [-4, 4]): Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "geluUsingLUTTest should correctly apply an approximate GELU value using a Lookup Table with 512 entries for [-4, 4] on BF16 input numbers" in {
        simulate(new geluUsingLUT(intBits = 2, fracBits = 6)) { c =>
            var tolerance = 0.0078125f*8
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in geluUsingLUT.scala
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                // GELU 'nearly exact' formula:
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a}")
                    println(f"output gelu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact gelu value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"GELU LUT (512 entries in [-4, 4]): Mean Squared Error (MSE): ${mse}")
            println(f"GELU LUT (512 entries in [-4, 4]): Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU LUT (512 entries in [-4, 4]): Maximum Absolute Error (Max AE): ${max_AE}")
        }
    }

    "geluUsingLUTTest should correctly apply an approximate GELU value using a Lookup Table with 256 entries for [-8, 8] on BF16 input numbers" in {
        simulate(new geluUsingLUT(intBits = 3, fracBits = 4)) { c =>
            var tolerance = 0.015625f*8
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in geluUsingLUT.scala
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                // GELU 'nearly exact' formula:
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a}")
                    println(f"output gelu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact gelu value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"GELU LUT (256 entries in [-8, 8]): Mean Squared Error (MSE): ${mse}")
            println(f"GELU LUT (256 entries in [-8, 8]): Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU LUT (256 entries in [-8, 8]): Maximum Absolute Error (Max AE): ${max_AE}")
        }
    }

    "geluUsingLUTTest should correctly apply an approximate GELU value using a Lookup Table with 512 entries for [-8, 8] on BF16 input numbers" in {
        simulate(new geluUsingLUT(intBits = 3, fracBits = 5)) { c =>
            var tolerance = 0.0078125f*8
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f // keeps track of maximum absolute error
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in geluUsingLUT.scala
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                // GELU 'nearly exact' formula:
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a}")
                    println(f"output gelu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact gelu value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"GELU LUT (512 entries in [-8, 8]): Mean Squared Error (MSE): ${mse}")
            println(f"GELU LUT (512 entries in [-8, 8]): Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU LUT (512 entries in [-8, 8]): Maximum Absolute Error (Max AE): ${max_AE}")
        }
    }

    "geluUsingLUTTest should correctly apply an approximate GELU value using a Lookup Table with 1024 entries for [-8, 8] on BF16 input numbers" in {
        simulate(new geluUsingLUT(intBits = 3, fracBits = 6)) { c =>
            var tolerance = 0.00390625f*8
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f // keeps track of maximum absolute error
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in geluUsingLUT.scala
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                // GELU 'nearly exact' formula:
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a}")
                    println(f"output gelu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact gelu value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"GELU LUT (1024 entries in [-8, 8]): Mean Squared Error (MSE): ${mse}")
            println(f"GELU LUT (1024 entries in [-8, 8]): Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU LUT (1024 entries in [-8, 8]): Maximum Absolute Error (Max AE): ${max_AE}")
        }
    }
}