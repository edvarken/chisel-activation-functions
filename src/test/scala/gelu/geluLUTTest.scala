package gelu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import silu.FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
import math.sqrt
import math.tanh
import math.Pi

class geluLUTTest extends AnyFreeSpec with Matchers {
    var verbose = 0 
    "geluLUTTest should give correct LUT value for a given 7bit index(intBits=2, fracBits=4)" in {
        simulate(new geluLUT(intBits = 2, fracBits = 4)) { c =>
            var tolerance = 0.016f // gelu LUT approximation error never gets bigger than 0.004f, 
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
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 7 bits

                val sign = index(6).litValue.toInt // sign bit
                val intPart = index(5,4).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(3,0).litValue, digits = 4) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                // GELU approximation: x * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (x + 0.044715 * x**3))))
                val geluValue = (signedsamplevalue * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (signedsamplevalue + 0.044715 * math.pow(signedsamplevalue,3)))))).toFloat
                c.clock.step(1) // should be 0cc latency since the LUT is a combinatorial circuit.
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    // print the binary string of 7 bits
                    println(f"index: ${index.litValue.toInt.toBinaryString}")
                    println(f"lutValue: ${lutValue}")
                    println(f"expected: ${geluValue}")
                }
                assert(math.abs(lutValue - geluValue) < tolerance, s"Expected ${geluValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }

    "geluLUTTest should give correct LUT value for a given 8bit index(intBits=2, fracBits=5)" in {
        simulate(new geluLUT(intBits = 2, fracBits = 5)) { c =>
            var tolerance = 0.016f
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
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 8 bits

                val sign = index(7).litValue.toInt // sign bit
                val intPart = index(6,5).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(4,0).litValue, digits = 5) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val geluValue = (signedsamplevalue * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (signedsamplevalue + 0.044715 * math.pow(signedsamplevalue,3)))))).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    // print the binary string of 7 bits
                    println(f"index: ${index.litValue.toInt.toBinaryString}")
                    println(f"lutValue: ${lutValue}")
                    println(f"expected: ${geluValue}")
                }
                assert(math.abs(lutValue - geluValue) < tolerance, s"Expected ${geluValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }

    "geluLUTTest should give correct LUT value for a given 8bit index(intBits=3, fracBits=4)" in {
        simulate(new geluLUT(intBits = 3, fracBits = 4)) { c =>
            var tolerance = 0.032f
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
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 8 bits

                val sign = index(7).litValue.toInt // sign bit
                val intPart = index(6,4).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(3,0).litValue, digits = 4) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val geluValue = (signedsamplevalue * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (signedsamplevalue + 0.044715 * math.pow(signedsamplevalue,3)))))).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    // print the binary string of 7 bits
                    println(f"index: ${index.litValue.toInt.toBinaryString}")
                    println(f"lutValue: ${lutValue}")
                    println(f"expected: ${geluValue}")
                }
                assert(math.abs(lutValue - geluValue) < tolerance, s"Expected ${geluValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }

    "geluLUTTest should give correct LUT value for a given 9bit index(intBits=3, fracBits=5)" in {
        simulate(new geluLUT(intBits = 3, fracBits = 5)) { c =>
            var tolerance = 0.032f
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
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 8 bits

                val sign = index(8).litValue.toInt // sign bit
                val intPart = index(7,5).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(4,0).litValue, digits = 5) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val geluValue = (signedsamplevalue * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (signedsamplevalue + 0.044715 * math.pow(signedsamplevalue,3)))))).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    // print the binary string of 7 bits
                    println(f"index: ${index.litValue.toInt.toBinaryString}")
                    println(f"lutValue: ${lutValue}")
                    println(f"expected: ${geluValue}")
                }
                assert(math.abs(lutValue - geluValue) < tolerance, s"Expected ${geluValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }

}