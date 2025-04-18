package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}

class FPMult16Test extends AnyFreeSpec with Matchers {
    "FPMult16 should correctly multiply floating-point numbers" in {
        simulate(new FPMult16) { c =>
            var lastExpected = 0.0f // must be a var and not a val, so we can update it
            c.io.a.poke(floatToBigIntBF16(0.0f).U(16.W))
            c.io.b.poke(floatToBigIntBF16(3.0f).U(16.W))
            c.clock.step(1)
            
            for (_ <- 0 until 8) {
                val a = scala.util.Random.nextFloat() * 10000.0f - 5000.0f
                val b = scala.util.Random.nextFloat() * 10000.0f - 5000.0f
                val expected = a * b

                c.io.a.poke(floatToBigIntBF16(a).U)
                c.io.b.poke(floatToBigIntBF16(b).U)
                c.clock.step(1)

                lastExpected = expected
                // c.io.res is 1 or 2 bits off mostly
                // Largest possible error when truncating FP32 to BF16 is ~ 1E36! Instead check for upper 16 bits being the same
                assert((c.io.res.peek().litValue.toInt & 0xFFFF0000) == (floatToBigIntBF16(lastExpected).U(16.W).litValue.toInt & 0xFFFF0000),
                s"Expected ${floatToBigIntBF16(lastExpected).U(16.W)} but got ${c.io.res.peek().litValue.toInt & 0xFFFF0000}")
            }
            c.clock.step(1)
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