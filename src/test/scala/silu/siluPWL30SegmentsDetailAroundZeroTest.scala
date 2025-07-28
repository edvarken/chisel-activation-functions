package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import silu.FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
// for silu
import math.exp 

class siluPWL30SegmentsDetailAroundZeroTest extends AnyFreeSpec with Matchers {
    var max_test_value = 8.0f // declare the maximum test value
    var positive_only = false
    var N = 200 // declare N
    if (positive_only) {
        N = N/2 // if positive_only, then N is halved since we only test in [0, max_test_value]
        println(f"${N} inputs in the range: [0.0, ${max_test_value}]")
    }
    else {
        N = N 
        println(f"${N} inputs in the range: [-${max_test_value}, ${max_test_value}]")
    }
    // #########################################################################################################
    // ##############################        SiLU         ######################################################
    // #########################################################################################################
    "siluPWL30SegmentsDetailAroundZeroTest should correctly apply an approximate SiLU value for a BF16 input, using 30 PWL SiLU non-uniform segments with shared intercepts for pos. and neg. segments" in {
        simulate(new siluPWL30SegmentsDetailAroundZero) { c =>
            var tolerance = 0.015625f*2.5
            var verbose = 1
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.clock.step(6) // latency: 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc outputReg
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.clock.step(6) 
            c.io.out_a.expect("b0_01111111_0011101".U(16.W)) // 1.22636171429 

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.clock.step(6)
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.clock.step(6)
            c.io.out_a.expect("b1011111010001100".U(16.W)) // -0.27363828571 ~ -0.275390625

            var mse = 0.0f
            var mse_MAE = 0.0f 
            var max_AE = 0.0f 
            if (positive_only) { // N uniformly spaced inputs in [0,8]
                step = (max_test_value) / N
                a = 0.0f
            }
            else { // N uniformly spaced inputs in [-8,8]
                step = (2*max_test_value) / N
                a = -max_test_value
            }
            while (a <= max_test_value) { 
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.clock.step(6)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float / (1 + math.exp(-a_upper16bits_float))).toFloat // SiLU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    // capture errors, print to stdout(=terminal)
                    print(s"${diff}, ")
                    // println(f"input x-value: ${a_upper16bits_float}")
                    // println(f"output silu-using-PWLSigmoid20-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    // println(f"expected exact SiLU value: ${expected}")
                    // println(f"Difference: ${diff}")
                    // println("###########")
                }
                assert(diff.abs <= tolerance, s"Expected ${expected} but got ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                mse += diff * diff
                if (diff.abs > max_AE) {
                    max_AE = diff.abs
                }
                mse_MAE += diff.abs
                a += step
            }
            mse /= (N+1).toFloat
            mse_MAE /= (N+1).toFloat
            println(f"SiLU 30 PWL non-uniform segments with shared intercepts : Mean Squared Error (MSE): ${mse}")
            println(f"SiLU 30 PWL non-uniform segments with shared intercepts : Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"SiLU 30 PWL non-uniform segments with shared intercepts : Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }
}