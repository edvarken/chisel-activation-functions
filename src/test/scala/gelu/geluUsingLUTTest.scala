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
    "geluUsingLUTTest should correctly apply an approximate GELU value using a Lookup Table with 128 entries for [-4, 4] on BF16 input numbers" in {
        simulate(new geluUsingLUT(intBits = 2, fracBits = 4)) { c =>
            var tolerance = 0.16f // GELU LUT approximation+quantization errors together never gets bigger than 0.16f (this is 10x the approx. error only)
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            for (_ <- 0 until 50) {
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1]*14-7: -7 to 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in geluUsingLUT.scala
                // GELU 'exact/approximating' formula:
                val expected = (a * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a + 0.044715 * math.pow(a,3)))))).toFloat
                println(f"input x-value: ${a}")
                println(f"output gelu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact gelu value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println("###########")
            }
        }
    }

    "geluUsingLUTTest should correctly apply an approximate GELU value using a Lookup Table with 256 entries for [-4, 4] on BF16 input numbers" in {
        simulate(new geluUsingLUT(intBits = 2, fracBits = 5)) { c =>
            var tolerance = 0.16f // gelu LUT approximation+quantization errors together never gets bigger than 0.16f (this is 10x the approx. error only)
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            for (_ <- 0 until 50) {
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1]*14-7: -7 to 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in geluUsingLUT.scala
                val expected = (a * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a + 0.044715 * math.pow(a,3)))))).toFloat
                println(f"input x-value: ${a}")
                println(f"output gelu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact gelu value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println("###########")
            }
        }
    }

    "geluUsingLUTTest should correctly apply an approximate GELU value using a Lookup Table with 256 entries for [-8, 8] on BF16 input numbers" in {
        simulate(new geluUsingLUT(intBits = 3, fracBits = 4)) { c =>
            var tolerance = 0.16f // gelu LUT approximation+quantization errors together never gets bigger than ?
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            for (_ <- 0 until 50) {
                val a = scala.util.Random.nextFloat() * 20.0f - 10.0f // [0,1]*20-10: -10 to +10
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in geluUsingLUT.scala
                val expected = (a * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a + 0.044715 * math.pow(a,3)))))).toFloat
                println(f"input x-value: ${a}")
                println(f"output gelu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact gelu value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println("###########")
            }
        }
    }

    "geluUsingLUTTest should correctly apply an approximate GELU value using a Lookup Table with 512 entries for [-8, 8] on BF16 input numbers" in {
        simulate(new geluUsingLUT(intBits = 3, fracBits = 5)) { c =>
            var tolerance = 0.16f // gelu LUT approximation+quantization errors together never gets bigger than ?
            c.io.in_a.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            for (_ <- 0 until 50) {
                val a = scala.util.Random.nextFloat() * 20.0f - 10.0f // [0,1]*20-10: -10 to +10
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(1) // 1cc latency due to output Register in geluUsingLUT.scala
                val expected = (a * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a + 0.044715 * math.pow(a,3)))))).toFloat
                println(f"input x-value: ${a}")
                println(f"output gelu-LUT-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println(f"expected exact gelu value: ${expected}")

                // subtract c.io.out_a from expected to get the difference
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                println(f"Difference: ${diff}")
                assert(diff.abs < tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                println("###########")
            }
        }
    }
}