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
    var verbose = 0
    "siluLUTTest should give correct LUT value for a given 7bit index(intBits=2, fracBits=4)" in {
        simulate(new siluLUT(intBits = 2, fracBits = 4)) { c =>
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
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 7 bits

                val sign = index(6).litValue.toInt // sign bit
                val intPart = index(5,4).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(3,0).litValue, digits = 4) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val siluValue = signedsamplevalue / (1 + math.exp(-signedsamplevalue)).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"lutValue: ${lutValue}")
                    println(f"expected: ${siluValue}")
                    // print the binary string of 7 bits
                    println(f"index: ${index.litValue.toInt.toBinaryString}")
                }
                assert(math.abs(lutValue - siluValue) < tolerance, s"Expected ${siluValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }
    "siluLUTTest should give correct LUT value for a given 8bit index(intBits=2, fracBits=5)" in {
        simulate(new siluLUT(intBits = 2, fracBits = 5)) { c =>
            var tolerance = 0.016f // silu LUT approximation error never gets bigger than 0.016f
            def fracBinaryToFloat(fracValue: BigInt, digits: Int = 5): Float = {
                var result = 0.0f
                for (i <- 0 until digits) {
                    if (((fracValue >> i) & 1) == 1) {
                    result += math.pow(2, -(digits - i)).toFloat
                    }
                }
                result
            }
            for (i <- 0 until 256) { // 00000000 to 11111111
                val index = i.U(8.W)
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 8 bits

                val sign = index(7).litValue.toInt // sign bit
                val intPart = index(6,5).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(4,0).litValue, digits = 5) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val siluValue = signedsamplevalue / (1 + math.exp(-signedsamplevalue)).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"lutValue: ${lutValue}")
                    println(f"expected: ${siluValue}")
                    // print the binary string of 8 bits
                    println(f"index: ${index.litValue.toInt.toBinaryString}")
                }
                assert(math.abs(lutValue - siluValue) < tolerance, s"Expected ${siluValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }

    "siluLUTTest should give correct LUT value for a given 8bit index(intBits=3, fracBits=4)" in {
        simulate(new siluLUT(intBits = 3, fracBits = 4)) { c =>
            var tolerance = 0.032f // silu LUT approximation error never gets bigger than 0.04f ! which is quite large actually :(
            def fracBinaryToFloat(fracValue: BigInt, digits: Int = 4): Float = {
                var result = 0.0f
                for (i <- 0 until digits) {
                    if (((fracValue >> i) & 1) == 1) {
                    result += math.pow(2, -(digits - i)).toFloat
                    }
                }
                result
            }
            for (i <- 0 until 256) {
                val index = i.U(8.W) // index is 8 bits
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 8 bits: exactly what we want

                val sign = index(7).litValue.toInt // sign bit
                val intPart = index(6,4).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(3,0).litValue, digits = 4) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val siluValue = signedsamplevalue / (1 + math.exp(-signedsamplevalue)).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"lutValue: ${lutValue}")
                    println(f"expected: ${siluValue}")
                    // print the binary string of 8 bits
                    println(f"index: ${index.litValue.toInt.toBinaryString}")
                }
                assert(math.abs(lutValue - siluValue) < tolerance, s"Expected ${siluValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }

    "siluLUTTest should give correct LUT value for a given 9bit index(intBits=3, fracBits=5)" in {
        simulate(new siluLUT(intBits = 3, fracBits = 5)) { c =>
            var tolerance = 0.032f // silu LUT approximation error never gets bigger than 0.04f ! which is quite large actually :(
            def fracBinaryToFloat(fracValue: BigInt, digits: Int = 5): Float = {
                var result = 0.0f
                for (i <- 0 until digits) {
                    if (((fracValue >> i) & 1) == 1) {
                    result += math.pow(2, -(digits - i)).toFloat
                    }
                }
                result
            }
            for (i <- 0 until 512) {
                val index = i.U(9.W) // index is 9 bits now!
                c.io.indexIn.poke(index) // padded with zeroes at the left if less than 8 bits: exactly what we want

                val sign = index(8).litValue.toInt // sign bit
                val intPart = index(7,5).litValue.toInt.toFloat // integer part
                val fracPart = fracBinaryToFloat(index(4,0).litValue, digits = 5) // fractional part
                val samplevalue = intPart + fracPart
                val signedsamplevalue = if (sign == 1) -samplevalue else samplevalue
                val siluValue = signedsamplevalue / (1 + math.exp(-signedsamplevalue)).toFloat

                c.clock.step(1)
                
                val lutValue = java.lang.Float.intBitsToFloat((BigInt(c.io.valueOut.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"lutValue: ${lutValue}")
                    println(f"expected: ${siluValue}")
                    // print the binary string of 9 bits
                    println(f"index: ${index.litValue.toInt.toBinaryString}")
                }
                assert(math.abs(lutValue - siluValue) < tolerance, s"Expected ${siluValue} but got ${lutValue}, for signedsamplevalue: ${signedsamplevalue}, index: ${index.litValue.toInt}")
            }
        }
    }
}