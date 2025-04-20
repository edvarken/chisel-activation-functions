package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
import math.exp

class siluLUTTest extends AnyFreeSpec with Matchers {
    "siluLUTTest should give correct LUT value for a given index" in {
        simulate(new siluLUT) { c =>
            var tolerance = 0.016f // silu LUT approximation error never gets bigger than 0.016f
            def fracBinaryToFloat(fracValue: BigInt, digits: Int = 4): Float = {
                var result = 0.0f
                for (i <- 0 until digits) {
                    if (((fracValue >> i) & 1) == 1) {
                    result += math.pow(2, -(digits - i)).toFloat
                    }
                }
                result
            }

            for (i <- 0 until 128) { // 0000000 to 1111111
                val index = i.U(7.W)
                // print the binary string of 7 bits
                println(f"index: ${index.litValue.toInt.toBinaryString}")
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 7 bits

                val sign = index(6).litValue.toInt // sign bit
                val intPart = index(5,4).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(3,0).litValue, digits = 4) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val siluValue = signedsamplevalue / (1 + math.exp(-signedsamplevalue)).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                println(f"lutValue: ${lutValue}")
                println(f"expected: ${siluValue}")
                assert(math.abs(lutValue - siluValue) < tolerance, s"Expected ${siluValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }
}