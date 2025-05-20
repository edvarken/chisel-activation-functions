package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
import math.abs

class BF16toFPTest extends AnyFreeSpec with Matchers {
    "BF16toFP should extract 2 integer bits and 4 fractional bits from BF16 input numbers correctly" in {
        val intBits = 2
        val fracBits = 4
        simulate(new BF16toFP(intBits, fracBits)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')

            def fracBinaryToFloat(fracValue: BigInt, digits: Int = fracBits): Float = {
                var result = 0.0f
                for (i <- 0 until digits) {
                    if (((fracValue >> i) & 1) == 1) {
                    result += math.pow(2, -(digits - i)).toFloat
                    }
                }
                result
            }
            var verbose = false
            var tolerance = 0.07f

            c.io.bf16in.poke("b0_00000000_0000000".U(16.W)) // BF16 are the upper 16 bits of a 32-bit float
            c.clock.step(1)
            c.io.intout.expect(0.U(intBits.W))
            c.io.fracout.expect(0.U(fracBits.W))
            c.io.signout.expect(0.U(1.W))

            c.io.bf16in.poke("b0_01111111_0100000".U(16.W)) // 1.25*2^0
            c.clock.step(1)
            c.io.intout.expect("b01".U(intBits.W)) // 2bits
            c.io.fracout.expect("b0100".U(fracBits.W)) // 4bits
            c.io.signout.expect(0.U(1.W))

            c.io.bf16in.poke("b1_01111111_0100000".U(16.W)) // -1.25*2^0
            c.clock.step(1)
            c.io.intout.expect("b01".U(intBits.W))
            c.io.fracout.expect("b0100".U(fracBits.W))
            c.io.signout.expect(1.U(1.W))

            c.io.bf16in.poke("b0_10000000_1100000".U(16.W)) // 1.75*2^1
            c.clock.step(1)
            c.io.intout.expect("b11".U(intBits.W))
            c.io.fracout.expect("b1000".U(fracBits.W))
            c.io.signout.expect(0.U(1.W))

            for (_ <- 0 until 50) {
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1] * 8 - 4
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already only the keeps the lower 16 bits, but & 0xFFFF is there for clarity
                // grab the integer and fractional parts of a
                val intPart = math.abs(a.toInt)
                val fracPartFloat = math.abs(a - a.toInt)
                
                c.io.bf16in.poke(a_upper16bits)
                
                val expectedintout = (intPart.U)(intBits-1, 0) // 2 lsbits
                val expectedsignout = a_upper16bits(15)

                c.clock.step(1)

                c.io.intout.expect(expectedintout) // 2bits: the integer part must be correct for numbers between -4 and 4
                val fracValue: BigInt = c.io.fracout.peek().litValue
                val floatResult = fracBinaryToFloat(fracValue, digits = fracBits) // 4bits: the frac part must not deviate more than 0.07f
                assert(abs(fracPartFloat - floatResult) < tolerance, s"Expected ${fracPartFloat} but got ${floatResult}")
                c.io.signout.expect(expectedsignout)

                if (verbose) {
                    println(f"bf16 input bits: ${toBinary(a_upper16bits.litValue.toInt, 16)}")
                    println("bf16 input as float: " + a)
                    println(s"Input intPart: $intPart")
                    println(s"Input fracPart: ${fracPartFloat}")

                    println(f"Expected intout: ${toBinary(expectedintout.litValue.toInt, intBits)}")

                    println(f"Output intPart: ${toBinary(c.io.intout.peek().litValue.toInt, intBits)}")
                    println(s"Output fracPart: $floatResult")
                    println("error made in fracPart: " + abs(fracPartFloat - floatResult))
                    println("############################################")
                }
            }
        }
    }
}