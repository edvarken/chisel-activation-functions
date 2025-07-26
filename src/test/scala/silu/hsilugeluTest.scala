package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
// for gelu
import math.tanh 
import math.Pi
import math.sqrt
// for silu
import math.exp 

class hsilugeluTest extends AnyFreeSpec with Matchers {
    var verbose = 0 
    var max_test_value = 8.0f
    var N = 200
    println(f"${N} inputs in the range: [-${max_test_value}, ${max_test_value}]")
    "hsilugeluTest should correctly apply approximate h-SiLU function on BF16 numbers" in {
        simulate(new hsilugelu) { c =>
            var tolerance = 0.15f // silu approximation's error never gets bigger than 0.15f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(6)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f // keeps track of maximum absolute error
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b0".U(1.W))
                c.clock.step(6) // 5 clock cycles for the adder and 2 multipliers
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float / (1 + math.exp(-a_upper16bits_float))).toFloat // SiLU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output h-silu-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact silu value: ${expected}")
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
            println(f"h-SiLU: Mean Squared Error (MSE): ${mse}")
            println(f"h-SiLU: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"h-SiLU: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "hsilugeluTest should correctly apply approximate h-GELU function on BF16 numbers" in {
        simulate(new hsilugelu) { c =>
            var tolerance = 0.15f // GELU approximation's error never gets bigger than 0.15f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.io.in_select.poke("b1".U(1.W)) // 1 for GELU
            c.clock.step(6)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f // keeps track of maximum absolute error
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b1".U(1.W))
                c.clock.step(6)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat // GELU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output h-gelu-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
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
            println(f"h-GELU: Mean Squared Error (MSE): ${mse}")
            println(f"h-GELU: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"h-GELU: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }
}