package DyT

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import silu.FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
import math.tanh

class DyTLUTTest extends AnyFreeSpec with Matchers {
    "DyTLUTTest should give correct LUT value for a given 7bit index(intBits=2, fracBits=4)" in {
        simulate(new DyTLUT(intBits = 2, fracBits = 4)) { c =>
            var tolerance = 0.004f // DyT LUT approximation error never gets bigger than 0.004f, 
            // This is the only contribution to the error if the input value is exactly at a samplepoint of the LUT.
            // When an input value is inbetween two samplepoints of the LUT, the quantization error also contributes to the error.
            // To minimize quantization impact a larger LUT size can be used. e.g. 256 instead of 128.
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
                val DyTValue = (math.tanh(signedsamplevalue)).toFloat

                c.clock.step(1) // should be 0cc latency since the LUT is a combinatorial circuit.
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                println(f"lutValue: ${lutValue}")
                println(f"expected: ${DyTValue}")
                assert(math.abs(lutValue - DyTValue) < tolerance, s"Expected ${DyTValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }

    "DyTLUTTest should give correct LUT value for a given 8bit index(intBits=2, fracBits=5)" in {
        simulate(new DyTLUT(intBits = 2, fracBits = 5)) { c =>
            var tolerance = 0.004f
            def fracBinaryToFloat(fracValue: BigInt, digits: Int = 5): Float = {
                var result = 0.0f
                for (i <- 0 until digits) {
                    if (((fracValue >> i) & 1) == 1) {
                    result += math.pow(2, -(digits - i)).toFloat
                    }
                }
                result
            }

            for (i <- 0 until 256) { // 0000000 to 1111111
                val index = i.U(8.W)
                // print the binary string of 7 bits
                println(f"index: ${index.litValue.toInt.toBinaryString}")
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 8 bits

                val sign = index(7).litValue.toInt // sign bit
                val intPart = index(6,5).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(4,0).litValue, digits = 5) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val DyTValue = (math.tanh(signedsamplevalue)).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                println(f"lutValue: ${lutValue}")
                println(f"expected: ${DyTValue}")
                assert(math.abs(lutValue - DyTValue) < tolerance, s"Expected ${DyTValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }

    "DyTLUTTest should give correct LUT value for a given 8bit index(intBits=3, fracBits=4)" in {
        simulate(new DyTLUT(intBits = 3, fracBits = 4)) { c =>
            var tolerance = 0.004f
            def fracBinaryToFloat(fracValue: BigInt, digits: Int = 4): Float = {
                var result = 0.0f
                for (i <- 0 until digits) {
                    if (((fracValue >> i) & 1) == 1) {
                    result += math.pow(2, -(digits - i)).toFloat
                    }
                }
                result
            }

            for (i <- 0 until 256) { // 0000000 to 1111111
                val index = i.U(8.W)
                // print the binary string of 7 bits
                println(f"index: ${index.litValue.toInt.toBinaryString}")
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 8 bits

                val sign = index(7).litValue.toInt // sign bit
                val intPart = index(6,4).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(3,0).litValue, digits = 4) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val DyTValue = (math.tanh(signedsamplevalue)).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                println(f"lutValue: ${lutValue}")
                println(f"expected: ${DyTValue}")
                assert(math.abs(lutValue - DyTValue) < tolerance, s"Expected ${DyTValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }

    "DyTLUTTest should give correct LUT value for a given 9bit index(intBits=3, fracBits=5)" in {
        simulate(new DyTLUT(intBits = 3, fracBits = 5)) { c =>
            var tolerance = 0.004f
            def fracBinaryToFloat(fracValue: BigInt, digits: Int = 5): Float = {
                var result = 0.0f
                for (i <- 0 until digits) {
                    if (((fracValue >> i) & 1) == 1) {
                    result += math.pow(2, -(digits - i)).toFloat
                    }
                }
                result
            }

            for (i <- 0 until 512) { // 0000000 to 1111111
                val index = i.U(9.W)
                // print the binary string of 7 bits
                println(f"index: ${index.litValue.toInt.toBinaryString}")
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 8 bits

                val sign = index(8).litValue.toInt // sign bit
                val intPart = index(7,5).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(4,0).litValue, digits = 5) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val DyTValue = (math.tanh(signedsamplevalue)).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                println(f"lutValue: ${lutValue}")
                println(f"expected: ${DyTValue}")
                assert(math.abs(lutValue - DyTValue) < tolerance, s"Expected ${DyTValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }

}