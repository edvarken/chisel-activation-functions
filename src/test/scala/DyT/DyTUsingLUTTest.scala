package DyT

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import silu.FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
import math.tanh

class DyTUsingLUTTest extends AnyFreeSpec with Matchers {
    var verbose = 0 
    var max_test_value = 8.0f
    var N = 200
    "DyTUsingLUTTest should correctly apply an approximate DyT value using a Lookup Table with 128 entries for [-4, 4] on BF16 input * alpha" in {
        simulate(new DyTUsingLUT(intBits = 2, fracBits = 4)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            var tolerance = 0.0625f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(3)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            // specifically test for input 2.0147734
            val alpha1 = 1.0f
            val alpha1_upper16bits = ((floatToBigInt(alpha1).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only keeps the lower 16 bits, but & 0xFFFF is there for clarity
            val a1 = 2.0147734f
            val a1_upper16bits = ((floatToBigInt(a1).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a1_upper16bits)
            c.io.in_alpha.poke(alpha1_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.96484375

            val alpha2 = 0.3333f
            val alpha2_upper16bits = ((floatToBigInt(alpha2).toInt >> 16) & 0xFFFF).U(16.W)
            val a2 = 2.0147734f * 3.0f
            val a2_upper16bits = ((floatToBigInt(a2).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a2_upper16bits)
            c.io.in_alpha.poke(alpha2_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, output value: 0.96484375

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f // keeps track of maximum absolute error
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + at output of fixedpoint Register + output Register of DyTUsingLUT
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val alpha_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(alpha_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (math.tanh(a_upper16bits_float*alpha_upper16bits_float)).toFloat // Dynamic tanh formula
                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                assert(diff.abs <= tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                if (verbose > 0) {
                    println(f"input a-value: ${a}")
                    println(f"input alpha-value: ${alpha}")
                    println(f"tanh_input expected: ${a*alpha}")
                    // println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                    println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact DyT value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"DyT LUT (128 entries in [-4, 4]): Mean Squared Error (MSE) for ${N} uniformly spaced inputs in [-8,8]: ${mse}")
            println(f"DyT LUT (128 entries in [-4, 4]): Mean Absolute Error (MAE) for ${N} uniformly spaced inputs in [-8,8]: ${mse_MAE}")
            println(f"DyT LUT (128 entries in [-4, 4]): Maximum Absolute Error (Max AE) for ${N} uniformly spaced inputs in [-8,8]: ${max_AE}")
        }
    }

    "DyTUsingLUTTest should correctly apply an approximate DyT value using a Lookup Table with 256 entries for [-4, 4] on BF16 input * alpha" in {
        simulate(new DyTUsingLUT(intBits = 2, fracBits = 5)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            var tolerance = 0.03125f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(3)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            // specifically test for input 2.0147734
            val alpha1 = 1.0f
            val alpha1_upper16bits = ((floatToBigInt(alpha1).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only keeps the lower 16 bits, but & 0xFFFF is there for clarity
            val a1 = 2.0147734f
            val a1_upper16bits = ((floatToBigInt(a1).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a1_upper16bits)
            c.io.in_alpha.poke(alpha1_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.96484375

            val alpha2 = 0.3333f
            val alpha2_upper16bits = ((floatToBigInt(alpha2).toInt >> 16) & 0xFFFF).U(16.W)
            val a2 = 2.0147734f * 3.0f
            val a2_upper16bits = ((floatToBigInt(a2).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a2_upper16bits)
            c.io.in_alpha.poke(alpha2_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, output value: 0.96484375

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f // keeps track of maximum absolute error
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + at output of fixedpoint Register + output Register of DyTUsingLUT
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val alpha_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(alpha_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (math.tanh(a_upper16bits_float*alpha_upper16bits_float)).toFloat // Dynamic tanh formula
                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                assert(diff.abs <= tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                if (verbose > 0) {
                    println(f"input a-value: ${a}")
                    println(f"input alpha-value: ${alpha}")
                    println(f"tanh_input expected: ${a*alpha}")
                    // println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                    println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact DyT value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"DyT LUT (256 entries in [-4, 4]): Mean Squared Error (MSE) for ${N} uniformly spaced inputs in [-8,8]: ${mse}")
            println(f"DyT LUT (256 entries in [-4, 4]): Mean Absolute Error (MAE) for ${N} uniformly spaced inputs in [-8,8]: ${mse_MAE}")
            println(f"DyT LUT (256 entries in [-4, 4]): Maximum Absolute Error (Max AE) for ${N} uniformly spaced inputs in [-8,8]: ${max_AE}")
        }
    }

    "DyTUsingLUTTest should correctly apply an approximate DyT value using a Lookup Table with 512 entries for [-4, 4] on BF16 input * alpha" in {
        simulate(new DyTUsingLUT(intBits = 2, fracBits = 6)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            var tolerance = 0.03125f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(3)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            // specifically test for input 2.0147734
            val alpha1 = 1.0f
            val alpha1_upper16bits = ((floatToBigInt(alpha1).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only keeps the lower 16 bits, but & 0xFFFF is there for clarity
            val a1 = 2.0147734f
            val a1_upper16bits = ((floatToBigInt(a1).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a1_upper16bits)
            c.io.in_alpha.poke(alpha1_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.96484375

            val alpha2 = 0.3333f
            val alpha2_upper16bits = ((floatToBigInt(alpha2).toInt >> 16) & 0xFFFF).U(16.W)
            val a2 = 2.0147734f * 3.0f
            val a2_upper16bits = ((floatToBigInt(a2).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a2_upper16bits)
            c.io.in_alpha.poke(alpha2_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, output value: 0.96484375

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f // keeps track of maximum absolute error
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + at output of fixedpoint Register + output Register of DyTUsingLUT
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val alpha_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(alpha_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (math.tanh(a_upper16bits_float*alpha_upper16bits_float)).toFloat // Dynamic tanh formula
                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                assert(diff.abs <= tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                if (verbose > 0) {
                    println(f"input a-value: ${a}")
                    println(f"input alpha-value: ${alpha}")
                    println(f"tanh_input expected: ${a*alpha}")
                    // println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                    println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact DyT value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"DyT LUT (512 entries in [-4, 4]): Mean Squared Error (MSE) for ${N} uniformly spaced inputs in [-8,8]: ${mse}")
            println(f"DyT LUT (512 entries in [-4, 4]): Mean Absolute Error (MAE) for ${N} uniformly spaced inputs in [-8,8]: ${mse_MAE}")
            println(f"DyT LUT (512 entries in [-4, 4]): Maximum Absolute Error (Max AE) for ${N} uniformly spaced inputs in [-8,8]: ${max_AE}")
        }
    }

    "DyTUsingLUTTest should correctly apply an approximate DyT value using a Lookup Table with 256 entries for [-8, 8] on BF16 input * alpha" in {
        simulate(new DyTUsingLUT(intBits = 3, fracBits = 4)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            var tolerance = 0.0625
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(3)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            // specifically test for input 2.0147734
            val alpha1 = 1.0f
            val alpha1_upper16bits = ((floatToBigInt(alpha1).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only keeps the lower 16 bits, but & 0xFFFF is there for clarity
            val a1 = 2.0147734f
            val a1_upper16bits = ((floatToBigInt(a1).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a1_upper16bits)
            c.io.in_alpha.poke(alpha1_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.96484375

            val alpha2 = 0.3333f
            val alpha2_upper16bits = ((floatToBigInt(alpha2).toInt >> 16) & 0xFFFF).U(16.W)
            val a2 = 2.0147734f * 3.0f
            val a2_upper16bits = ((floatToBigInt(a2).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a2_upper16bits)
            c.io.in_alpha.poke(alpha2_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, output value: 0.96484375

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f // keeps track of maximum absolute error
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) { // N uniformly spaced inputs in [-8,8]
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + at output of fixedpoint Register + output Register of DyTUsingLUT
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val alpha_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(alpha_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (math.tanh(a_upper16bits_float*alpha_upper16bits_float)).toFloat // Dynamic tanh formula
                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                assert(diff.abs <= tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                if (verbose > 0) {
                    println(f"input a-value: ${a}")
                    println(f"input alpha-value: ${alpha}")
                    println(f"tanh_input expected: ${a*alpha}")
                    // println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                    println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact DyT value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"DyT LUT (256 entries in [-8, 8]): Mean Squared Error (MSE) for ${N} uniformly spaced inputs in [-8,8]: ${mse}")
            println(f"DyT LUT (256 entries in [-8, 8]): Mean Absolute Error (MAE) for ${N} uniformly spaced inputs in [-8,8]: ${mse_MAE}")
            println(f"DyT LUT (256 entries in [-8, 8]): Maximum Absolute Error (Max AE) for ${N} uniformly spaced inputs in [-8,8]: ${max_AE}")
        }
    }

    "DyTUsingLUTTest should correctly apply an approximate DyT value using a Lookup Table with 512 entries for [-8, 8] on BF16 input * alpha" in {
        simulate(new DyTUsingLUT(intBits = 3, fracBits = 5)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            var tolerance = 0.03125f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(3)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            // specifically test for input 2.0147734
            val alpha1 = 1.0f
            val alpha1_upper16bits = ((floatToBigInt(alpha1).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only keeps the lower 16 bits, but & 0xFFFF is there for clarity
            val a1 = 2.0147734f
            val a1_upper16bits = ((floatToBigInt(a1).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a1_upper16bits)
            c.io.in_alpha.poke(alpha1_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.96484375

            val alpha2 = 0.3333f
            val alpha2_upper16bits = ((floatToBigInt(alpha2).toInt >> 16) & 0xFFFF).U(16.W)
            val a2 = 2.0147734f * 3.0f
            val a2_upper16bits = ((floatToBigInt(a2).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a2_upper16bits)
            c.io.in_alpha.poke(alpha2_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, output value: 0.96484375

            var mse = 0.0f
            var mse_MAE = 0.0f
            var max_AE = 0.0f
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + at output of fixedpoint Register + output Register of DyTUsingLUT
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val alpha_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(alpha_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (math.tanh(a_upper16bits_float*alpha_upper16bits_float)).toFloat // Dynamic tanh formula
                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                assert(diff.abs <= tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                if (verbose > 0) {
                    println(f"input a-value: ${a}")
                    println(f"input alpha-value: ${alpha}")
                    println(f"tanh_input expected: ${a*alpha}")
                    // println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                    println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact DyT value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"DyT LUT (512 entries in [-8, 8]): Mean Squared Error (MSE) for ${N} uniformly spaced inputs in [-8,8]: ${mse}")
            println(f"DyT LUT (512 entries in [-8, 8]): Mean Absolute Error (MAE) for ${N} uniformly spaced inputs in [-8,8]: ${mse_MAE}")
            println(f"DyT LUT (512 entries in [-8, 8]): Maximum Absolute Error (Max AE) for ${N} uniformly spaced inputs in [-8,8]: ${max_AE}")
        }
    }

    "DyTUsingLUTTest should correctly apply an approximate DyT value using a Lookup Table with 1024 entries for [-8, 8] on BF16 input * alpha" in {
        simulate(new DyTUsingLUT(intBits = 3, fracBits = 6)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            var tolerance = 0.03125f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(3)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            // specifically test for input 2.0147734
            val alpha1 = 1.0f
            val alpha1_upper16bits = ((floatToBigInt(alpha1).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only keeps the lower 16 bits, but & 0xFFFF is there for clarity
            val a1 = 2.0147734f
            val a1_upper16bits = ((floatToBigInt(a1).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a1_upper16bits)
            c.io.in_alpha.poke(alpha1_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.96484375

            val alpha2 = 0.3333f
            val alpha2_upper16bits = ((floatToBigInt(alpha2).toInt >> 16) & 0xFFFF).U(16.W)
            val a2 = 2.0147734f * 3.0f
            val a2_upper16bits = ((floatToBigInt(a2).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a2_upper16bits)
            c.io.in_alpha.poke(alpha2_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110111".U(16.W)) // real DyT value: 0.9650566, output value: 0.96484375

            var mse = 0.0f
            var mse_MAE = 0.0f
            var max_AE = 0.0f
            val step = (2*max_test_value) / N
            var a = -max_test_value
            while (a <= max_test_value) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + at output of fixedpoint Register + output Register of DyTUsingLUT
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val alpha_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(alpha_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (math.tanh(a_upper16bits_float*alpha_upper16bits_float)).toFloat // Dynamic tanh formula
                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                assert(diff.abs <= tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                if (verbose > 0) {
                    println(f"input a-value: ${a}")
                    println(f"input alpha-value: ${alpha}")
                    println(f"tanh_input expected: ${a*alpha}")
                    // println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                    println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact DyT value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
                }
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"DyT LUT (1024 entries in [-8, 8]): Mean Squared Error (MSE) for ${N} uniformly spaced inputs in [-8,8]: ${mse}")
            println(f"DyT LUT (1024 entries in [-8, 8]): Mean Absolute Error (MAE) for ${N} uniformly spaced inputs in [-8,8]: ${mse_MAE}")
            println(f"DyT LUT (1024 entries in [-8, 8]): Maximum Absolute Error (Max AE) for ${N} uniformly spaced inputs in [-8,8]: ${max_AE}")
        }
    }
}