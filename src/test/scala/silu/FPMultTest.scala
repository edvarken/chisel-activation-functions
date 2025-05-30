package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}

class FPMult16Test extends AnyFreeSpec with Matchers {
    "FPMult16ALT should correctly multiply BF16 numbers" in {
        simulate(new FPMult16ALT) { c =>

            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')

            var lastExpected = 0.0f // must be a var and not a val, so we can update it
            c.io.a.poke(floatToBigIntBF16(0.0f).U(16.W))
            c.io.b.poke(floatToBigIntBF16(3.0f).U(16.W))
            c.clock.step(1)
            assert(floatToBigIntBF16(lastExpected).U(16.W).litValue.toInt == c.io.res.peek().litValue.toInt,
            s"Expected ${toBinary(floatToBigIntBF16(lastExpected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.res.peek().litValue.toInt, 16)}")
            
            val input_a = -4.562719f
            val input_b = 0.43761528f
            val expected = -1.9967154f
            val expected_exponent = math.floor(math.log(math.abs(expected))/math.log(2)).toInt
            val max_diff = math.pow(2, expected_exponent-7).toFloat

            c.io.a.poke(floatToBigIntBF16(input_a).U(16.W))
            c.io.b.poke(floatToBigIntBF16(input_b).U(16.W))
            c.clock.step(1)
            // during usage of this module inside another module, the actual output was wrongly -1.0 instead of -1.9967.
            // this was due to overflow bug in MantissaRounder, which is fixed now.
            val actual_value = java.lang.Float.intBitsToFloat((BigInt(c.io.res.peek().litValue.toInt) << 16).toInt)
            assert(math.abs(actual_value - expected) < max_diff, s"Expected ${toBinary(floatToBigIntBF16(lastExpected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.res.peek().litValue.toInt, 16)}")
            
            for (_ <- 0 until 20) {
                val a = scala.util.Random.nextFloat() * 10000.0f - 5000.0f // [0,1]*10000-5000: -5000 to 5000
                val b = scala.util.Random.nextFloat() * 10000.0f - 5000.0f
                val expected = a * b
                // Quite large errors for numbers with large exponent 
                // -> just check maxdiff is smaller than 2^(exponent-5), so the error must be smaller than contribution of two lsbits of mantissa
                val expected_exponent = math.floor(math.log(math.abs(expected))/math.log(2)).toInt
                val max_diff = math.pow(2, expected_exponent-5).toFloat

                c.io.a.poke(floatToBigIntBF16(a).U)
                c.io.b.poke(floatToBigIntBF16(b).U)
                c.clock.step(1)

                lastExpected = expected
                // c.io.res is often 1 or 2 bits off in the mantissa of the bf16 representation
                // Largest possible error when truncating FP32 to BF16 is ~ 1E36, however error creeps in up to 3rd lsbit of the mantissa!
                val actual_value = java.lang.Float.intBitsToFloat((BigInt(c.io.res.peek().litValue.toInt) << 16).toInt)
                println(f"actual_value: ${actual_value}")
                println(f"expected: ${expected}")
                assert(math.abs(actual_value - expected) < max_diff, s"Expected ${toBinary(floatToBigIntBF16(lastExpected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.res.peek().litValue.toInt, 16)}")
            }
            c.clock.step(1)
            for (_ <- 0 until 20) {
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1]*14-7: -7 to +7
                val b = scala.util.Random.nextFloat() * 14.0f - 7.0f
                val expected = a * b
                val expected_exponent = math.floor(math.log(math.abs(expected))/math.log(2)).toInt
                val max_diff = math.pow(2, expected_exponent-5).toFloat
                c.io.a.poke(floatToBigIntBF16(a).U)
                c.io.b.poke(floatToBigIntBF16(b).U)
                c.clock.step(1)

                // c.io.res is 1 or 2 bits off mostly
                // Largest possible error when truncating FP32 to BF16 is ~ 1E36= 2^127*2^-8! Instead check for exponent being right.
                val actual_value = java.lang.Float.intBitsToFloat((BigInt(c.io.res.peek().litValue.toInt) << 16).toInt)
                println(f"actual_value: ${actual_value}")
                println(f"expected: ${expected}")
                assert(math.abs(actual_value - expected) < max_diff, s"Expected ${toBinary(floatToBigIntBF16(lastExpected).U(16.W).litValue.toInt, 16)} but got ${toBinary(c.io.res.peek().litValue.toInt, 16)}")
                println(f"expected: ${toBinary(floatToBigIntBF16(expected).U(16.W).litValue.toInt, 16)}, actual: ${toBinary(c.io.res.peek().litValue.toInt, 16)}")
            }
        }
    }
}


class FPMult32Test extends AnyFreeSpec with Matchers {
    "FPMult32 should correctly multiply floating-point numbers" in {
        simulate(new FPMult32) { c =>
            var lastExpected = 0.0f

            c.io.a.poke(floatToBigInt(0.0f).U)
            c.io.b.poke(floatToBigInt(3.0f).U)
            c.clock.step(1)

            for (_ <- 0 until 8) {
                val a = scala.util.Random.nextFloat() * 10000.0f - 5000.0f
                val b = scala.util.Random.nextFloat() * 10000.0f - 5000.0f
                val expected = a * b

                c.io.a.poke(floatToBigInt(a).U)
                c.io.b.poke(floatToBigInt(b).U)
                c.clock.step(1)

                println(s"Expecting $lastExpected or ${floatToBigInt(lastExpected)}")
                lastExpected = expected
                c.io.res.expect(floatToBigInt(lastExpected).U)
            }
            c.clock.step(1)
            c.io.res.expect(floatToBigInt(lastExpected).U)
        }
    }
}

class FPMult64Test extends AnyFreeSpec with Matchers {
    "FPMult64 should correctly multiply floating-point numbers" in {
        simulate(new FPMult64) { c =>
            var lastExpected = 0.0

            for (i <- 0 until 8) {
                val a = scala.util.Random.nextDouble() * 10000.0 - 5000.0
                val b = scala.util.Random.nextDouble() * 10000.0 - 5000.0
                val expected = a * b

                c.io.a.poke(doubleToBigInt(a).U)
                c.io.b.poke(doubleToBigInt(b).U)
                c.clock.step(1)

                lastExpected = expected
                if (i > 0) {
                    c.io.res.expect(doubleToBigInt(lastExpected).U)
                }
            }
            c.clock.step(1)
            c.io.res.expect(doubleToBigInt(lastExpected).U)
        }
    }
}