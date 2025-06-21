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
    "DyTUsingLUTTest should correctly apply an approximate DyT value using a Lookup Table with 128 entries for [-4, 4] on BF16 input * alpha" in {
        simulate(new DyTUsingLUT(intBits = 2, fracBits = 4)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            var tolerance = 0.07f // DyT LUT approximation's error never gets bigger than 0.004f
            // however, an additional quantization error is introduced when the input value is inbetween two samplepoints of the LUT.
            // the approx+quantization errors together are larger than 0.004f! but do not exceed 0.07f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
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
            c.io.out_a.expect("b0_01111110_1110110".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.960938

            val alpha2 = 0.3333f
            val alpha2_upper16bits = ((floatToBigInt(alpha2).toInt >> 16) & 0xFFFF).U(16.W)
            val a2 = 2.0147734f * 3.0f
            val a2_upper16bits = ((floatToBigInt(a2).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a2_upper16bits)
            c.io.in_alpha.poke(alpha2_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110110".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.960938

            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1]*14-7: -7 to 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + at output of fixedpoint Register + output Register of DyTUsingLUT
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
            // test for larger inputs, alpha still in [0,1]
            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 100.0f - 50.0f // [0,1]*100-50: -50 to +50
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
            // test for smaller inputs, alpha still in [0,1]
            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 1.0f - .5f // [0,1]*1-0.5: -0.5 to +0.5
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
        }
    }

    "DyTUsingLUTTest should correctly apply an approximate DyT value using a Lookup Table with 256 entries for [-4, 4] on BF16 input * alpha" in {
        simulate(new DyTUsingLUT(intBits = 2, fracBits = 5)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            var tolerance = 0.07f // DyT LUT approximation's error never gets bigger than 0.004f
            // however, an additional quantization error is introduced when the input value is inbetween two samplepoints of the LUT.
            // the approx+quantization errors together are larger than 0.004f! but they do not exceed 0.07f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
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
            c.io.out_a.expect("b0_01111110_1110110".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.7578125??? !=tanh(2.014) but also !=tanh(2.0) ?? // TODO: this fails

            val alpha2 = 0.3333f
            val alpha2_upper16bits = ((floatToBigInt(alpha2).toInt >> 16) & 0xFFFF).U(16.W)
            val a2 = 2.0147734f * 3.0f
            val a2_upper16bits = ((floatToBigInt(a2).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a2_upper16bits)
            c.io.in_alpha.poke(alpha2_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110110".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.960938

            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1]*14-7: -7 to 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + Register at the output of fixedpoint + output Register of DyTUsingLUT
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
            // test for larger inputs, alpha still in [0,1]
            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 100.0f - 50.0f // [0,1]*100-50: -50 to +50
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
            // test for smaller inputs, alpha still in [0,1]
            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 1.0f - .5f // [0,1]*1-0.5: -0.5 to +0.5
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
        }
    }

    "DyTUsingLUTTest should correctly apply an approximate DyT value using a Lookup Table with 256 entries for [-8, 8] on BF16 input * alpha" in {
        simulate(new DyTUsingLUT(intBits = 3, fracBits = 4)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            var tolerance = 0.07f // DyT LUT approximation's error never gets bigger than 0.004f
            // however, an additional quantization error is introduced when the input value is inbetween two samplepoints of the LUT.
            // the approx+quantization errors together are larger than 0.004f! but they do not exceed 0.07f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
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
            c.io.out_a.expect("b0_01111110_1110110".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.7578125??? !=tanh(2.014) but also !=tanh(2.0) ?? // TODO: this fails

            val alpha2 = 0.3333f
            val alpha2_upper16bits = ((floatToBigInt(alpha2).toInt >> 16) & 0xFFFF).U(16.W)
            val a2 = 2.0147734f * 3.0f
            val a2_upper16bits = ((floatToBigInt(a2).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a2_upper16bits)
            c.io.in_alpha.poke(alpha2_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110110".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.960938

            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1]*14-7: -7 to 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + Register at the output of fixedpoint + output Register of DyTUsingLUT
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
            // test for larger inputs, alpha still in [0,1]
            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 100.0f - 50.0f // [0,1]*100-50: -50 to +50
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
            // test for smaller inputs, alpha still in [0,1]
            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 1.0f - .5f // [0,1]*1-0.5: -0.5 to +0.5
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
        }
    }

    "DyTUsingLUTTest should correctly apply an approximate DyT value using a Lookup Table with 512 entries for [-8, 8] on BF16 input * alpha" in {
        simulate(new DyTUsingLUT(intBits = 3, fracBits = 5)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            var tolerance = 0.07f // DyT LUT approximation's error never gets bigger than 0.004f
            // however, an additional quantization error is introduced when the input value is inbetween two samplepoints of the LUT.
            // the approx+quantization errors together are larger than 0.004f! but they do not exceed 0.07f
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
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
            c.io.out_a.expect("b0_01111110_1110110".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.7578125??? !=tanh(2.014) but also !=tanh(2.0) ?? // TODO: this fails

            val alpha2 = 0.3333f
            val alpha2_upper16bits = ((floatToBigInt(alpha2).toInt >> 16) & 0xFFFF).U(16.W)
            val a2 = 2.0147734f * 3.0f
            val a2_upper16bits = ((floatToBigInt(a2).toInt >> 16) & 0xFFFF).U(16.W)
            c.io.in_a.poke(a2_upper16bits)
            c.io.in_alpha.poke(alpha2_upper16bits)
            c.clock.step(3)
            c.io.out_a.expect("b0_01111110_1110110".U(16.W)) // real DyT value: 0.9650566, LUT value: 0.960938

            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1]*14-7: -7 to 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + Register at the output of fixedpoint + output Register of DyTUsingLUT
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
            // test for larger inputs, alpha still in [0,1]
            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 100.0f - 50.0f // [0,1]*100-50: -50 to +50
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
            // test for smaller inputs, alpha still in [0,1]
            for (_ <- 0 until 50) {
                val alpha = scala.util.Random.nextFloat() // [0,1]
                val alpha_upper16bits = ((floatToBigInt(alpha).toInt >> 16) & 0xFFFF).U(16.W)
                val a = scala.util.Random.nextFloat() * 1.0f - .5f // [0,1]*1-0.5: -0.5 to +0.5
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                c.io.in_a.poke(a_upper16bits)
                c.io.in_alpha.poke(alpha_upper16bits)
                c.clock.step(3) // 3cc latency due to multiplier + fixedpoint Register +  output Register
                // seting step to 4 does not help, still test fails sometimes e.g.
                val expected = (math.tanh(a*alpha)).toFloat // Dynamic tanh formula
                println(f"input a-value: ${a}")
                println(f"input alpha-value: ${alpha}")
                println(f"tanh_input expected: ${a*alpha}")
                println(f"actual tanh_input: ${java.lang.Float.intBitsToFloat((BigInt(c.io.debug_out_tanh_input.peek().litValue.toInt) << 16).toInt)}")
                println(f"output DyT-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact DyT value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.out_a.peek().litValue.toInt, 16)}")
                println("###########")
            }
        }
    }
}