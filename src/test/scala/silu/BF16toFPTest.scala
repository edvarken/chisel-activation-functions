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
    "BF16toFP should extract 3 integer bits and 4 fractional bits from BF16 input numbers correctly" in {
        simulate(new BF16toFP) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')

            def fracBinaryToFloat(fracValue: BigInt, digits: Int = 4): Float = {
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
            c.io.intout.expect("b000".U(3.W))
            c.io.fracout.expect("b0000".U(4.W))
            c.io.signout.expect(0.U(1.W))

            c.io.bf16in.poke("b0_01111111_0100000".U(16.W)) // 1.25*2^0
            c.clock.step(1)
            c.io.intout.expect("b001".U(3.W))
            c.io.fracout.expect("b0100".U(4.W))
            c.io.signout.expect(0.U(1.W))

            c.io.bf16in.poke("b1_01111111_0100000".U(16.W)) // -1.25*2^0
            c.clock.step(1)
            c.io.intout.expect("b001".U(3.W))
            c.io.fracout.expect("b0100".U(4.W))
            c.io.signout.expect(1.U(1.W))

            c.io.bf16in.poke("b0_10000000_1100000".U(16.W)) // 1.75*2^1
            c.clock.step(1)
            c.io.intout.expect("b011".U(3.W))
            c.io.fracout.expect("b1000".U(4.W))
            c.io.signout.expect(0.U(1.W))

            for (_ <- 0 until 30) {
                val a = scala.util.Random.nextFloat() * 14.0f - 7.0f // [0,1] * 14 - 7
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W)
                // grab the integer and fractional parts of a
                val intPart = math.abs(a.toInt)
                val fracPartFloat = math.abs(a - a.toInt)
                
                c.io.bf16in.poke(a_upper16bits)
                
                val expectedintout = (intPart.U)(2, 0) // 3 lsbits
                val expectedsignout = a_upper16bits(15)

                c.clock.step(1)

                c.io.intout.expect(expectedintout) // 3bits
                val fracValue: BigInt = c.io.fracout.peek().litValue
                val floatResult = fracBinaryToFloat(fracValue, digits = 4) // 4bits
                assert(abs(fracPartFloat - floatResult) < tolerance, s"Expected ${fracPartFloat} but got ${floatResult}")
                c.io.signout.expect(expectedsignout)

                if (verbose) {
                    println(f"bf16 input bits: ${toBinary(a_upper16bits.litValue.toInt, 16)}")
                    println("bf16 input as float: " + a)
                    println(s"Input intPart: $intPart")
                    println(s"Input fracPart: ${fracPartFloat}")

                    println(f"Expected intout: ${toBinary(expectedintout.litValue.toInt, 3)}")

                    println(f"Output intPart: ${toBinary(c.io.intout.peek().litValue.toInt, 3)}")
                    println(s"Output fracPart: $floatResult")
                    println("error made in fracPart: " + abs(fracPartFloat - floatResult))
                    println("############################################")
                }
            }
        }
    }
}