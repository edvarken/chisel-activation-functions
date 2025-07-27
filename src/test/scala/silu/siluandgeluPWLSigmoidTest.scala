package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import silu.FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
// for gelu
import math.tanh 
import math.Pi
import math.sqrt
// for silu
import math.exp 

class siluandgeluPWLSigmoidTest extends AnyFreeSpec with Matchers {
    var max_test_value = 6.0f // declare the maximum test value
    var positive_only = false
    var N = 500 // declare N
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
    "siluandgeluPWLSigmoidTest should correctly apply an approximate SiLU value (in_select=0) for a BF16 input, using 12 PWL Sigmoid segments" in {
        simulate(new siluandgeluPWLSigmoid12Segments) { c =>
            var tolerance = 0.015625f*4
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) // latency: 5 comparators with 1 mult 1.0*x (1cc), 1 indexReg (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x>0
            c.io.out_a.expect("b0_01111111_0011100".U(16.W)) // 1.22636171429 

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(11) // latency: 11cc since negative input so additional Adder with 3cc for the 1-f(-x) operation
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(11)
            c.io.out_a.expect("b1_01111101_0010000".U(16.W)) // -0.27363828571 ~ -0.28125

            var mse = 0.0f // mean squared error accumulator
            var mse_MAE = 0.0f // mean absolute error accumulator
            var max_AE = 0.0f // maximum absolute error accumulator
            if (positive_only) {
                step = (max_test_value) / N
                a = 0.0f
            }
            else {
                step = (2*max_test_value) / N
                a = -max_test_value
            }
            while (a <= max_test_value) {
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b0".U(1.W))
                c.clock.step(11)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float / (1 + math.exp(-a_upper16bits_float))).toFloat // SiLU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output silu-using-PWLSigmoid12-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact SiLU value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
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
            println(f"SiLU 12 PWL Sigmoid segments: Mean Squared Error (MSE): ${mse}")
            println(f"SiLU 12 PWL Sigmoid segments: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"SiLU 12 PWL Sigmoid segments: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "siluandgeluPWLSigmoidTest should correctly apply an approximate SiLU value (in_select=0) for a BF16 input, using 10 PWL Sigmoid segments" in {
        simulate(new siluandgeluPWLSigmoid10Segments) { c =>
            var tolerance = 0.015625f*4
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) // latency: 5 comparators with 1 mult 1.0*x (1cc), 1 indexReg (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x>0
            c.io.out_a.expect("b0_01111111_0011100".U(16.W)) // 1.22636171429 

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(11) // latency: 11cc since negative input so additional Adder with 3cc for the 1-f(-x) operation
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(11)
            c.io.out_a.expect("b1_01111101_0010000".U(16.W)) // -0.27363828571 ~ -0.28125

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
                c.io.in_select.poke("b0".U(1.W))
                c.clock.step(11)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float / (1 + math.exp(-a_upper16bits_float))).toFloat // SiLU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output silu-using-PWLSigmoid10-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact SiLU value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
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
            println(f"SiLU 10 PWL Sigmoid segments: Mean Squared Error (MSE): ${mse}")
            println(f"SiLU 10 PWL Sigmoid segments: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"SiLU 10 PWL Sigmoid segments: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "siluandgeluPWLSigmoidTest should correctly apply an approximate SiLU value (in_select=0) for a BF16 input, using 18 PWL Sigmoid segments" in {
        simulate(new siluandgeluPWLSigmoid18Segments) { c =>
            var tolerance = 0.015625f*4
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) // latency: 5 comparators with 1 mult 1.0*x (1cc), 1 indexReg (1cc), 1 sigmoidReg (1cc), 1 mult (1cc), no Add since x>0
            c.io.out_a.expect("b0_01111111_0011101".U(16.W)) // 1.22636171429 

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(11) // latency: 11cc since negative input so additional Adder with 3cc for the 1-f(-x) operation
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(11)
            c.io.out_a.expect("b1_01111101_0010000".U(16.W)) // -0.27363828571 ~ -0.28125

            var mse = 0.0f
            var mse_MAE = 0.0f 
            var max_AE = 0.0f 
            if (positive_only) {
                step = (max_test_value) / N
                a = 0.0f
            }
            else {
                step = (2*max_test_value) / N
                a = -max_test_value
            }
            while (a <= max_test_value) { 
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b0".U(1.W))
                c.clock.step(11)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float / (1 + math.exp(-a_upper16bits_float))).toFloat // SiLU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    print(s"$diff, ")
                    // println(f"input x-value: ${a_upper16bits_float}")
                    // println(f"output silu-using-PWLSigmoid18-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
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
            println(f"SiLU 18 PWL Sigmoid segments: Mean Squared Error (MSE): ${mse}")
            println(f"SiLU 18 PWL Sigmoid segments: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"SiLU 18 PWL Sigmoid segments: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "siluandgeluPWLSigmoidTest should correctly apply an approximate SiLU value (in_select=0) for a BF16 input, using 20 PWL Sigmoid non-uniform segments with Extra Adder" in {
        simulate(new siluandgeluPWLSigmoid20NonUniformSegmentsUsingExtraAdder) { c =>
            var tolerance = 0.015625f*3
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) 
            c.io.out_a.expect("b0_01111111_0011101".U(16.W)) // 1.22636171429 

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(11) // extra adder 3cc latency for x < 0
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(11) // extra adder 3cc latency for x < 0 
            c.io.out_a.expect("b1011111010010000".U(16.W)) // -0.27363828571 ~ -0.28125

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
                c.io.in_select.poke("b0".U(1.W))
                c.clock.step(11)
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
            println(f"SiLU 20 PWL Sigmoid non-uniform segments with extra adder: Mean Squared Error (MSE): ${mse}")
            println(f"SiLU 20 PWL Sigmoid non-uniform segments with extra adder: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"SiLU 20 PWL Sigmoid non-uniform segments with extra adder: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "siluandgeluPWLSigmoidTest should correctly apply an approximate SiLU value (in_select=0) for a BF16 input, using 20 PWL Sigmoid non-uniform segments with Extra Intercepts" in {
        simulate(new siluandgeluPWLSigmoid20NonUniformSegments) { c =>
            var tolerance = 0.015625f*3
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) 
            c.io.out_a.expect("b0_01111111_0011101".U(16.W)) // 1.22636171429 

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b1011111010001101".U(16.W)) // -0.27363828571 ~ -0.275390625

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
                c.io.in_select.poke("b0".U(1.W))
                c.clock.step(8)
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
            println(f"SiLU 20 PWL Sigmoid non-uniform segments with Extra Intercepts: Mean Squared Error (MSE): ${mse}")
            println(f"SiLU 20 PWL Sigmoid non-uniform segments with Extra Intercepts: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"SiLU 20 PWL Sigmoid non-uniform segments with Extra Intercepts: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "siluandgeluPWLSigmoidTest should correctly apply an approximate SiLU value (in_select=0) for a BF16 input, using 36 PWL Sigmoid non-uniform segments with Extra Intercepts" in {
        simulate(new siluandgeluPWLSigmoid36NonUniformSegments) { c =>
            var tolerance = 0.015625f*3
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8) 
            c.io.out_a.expect("b0_01111111_0011101".U(16.W)) // 1.22636171429 

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b0".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b1011111010001101".U(16.W)) // -0.27363828571 ~ -0.275390625

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
                c.io.in_select.poke("b0".U(1.W))
                c.clock.step(8)
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
            println(f"SiLU 36 PWL Sigmoid non-uniform segments with Extra Intercepts: Mean Squared Error (MSE): ${mse}")
            println(f"SiLU 36 PWL Sigmoid non-uniform segments with Extra Intercepts: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"SiLU 36 PWL Sigmoid non-uniform segments with Extra Intercepts: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
            println("==============================")
        }
    }

    // #########################################################################################################
    // ##############################        GELU        #######################################################
    // #########################################################################################################
    "siluandgeluPWLSigmoidTest should correctly apply an approximate GELU value (in_select=1) for a BF16 input, using 12 PWL Sigmoid segments" in {
        simulate(new siluandgeluPWLSigmoid12Segments) { c =>
            var tolerance = 0.015625f*4
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b0_01111111_0110001".U(16.W)) // 1.39978919809671290 ~ 1.3828125

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(11) // latency: 11cc since negative input so additional Adder with 3cc for the 1-f(-x) operation
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(11) // latency: 11
            c.io.out_a.expect("b1_01111011_1110000".U(16.W)) // -0.100210801903287099 ~ -0.1171875

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
                c.io.in_select.poke("b1".U(1.W))
                c.clock.step(11)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat // GELU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output gelu-using-using-PWLSigmoid12-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact GELU value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
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
            println(f"GELU 12 PWL Sigmoid segments: Mean Squared Error (MSE): ${mse}")
            println(f"GELU 12 PWL Sigmoid segments: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU 12 PWL Sigmoid segments: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "siluandgeluPWLSigmoidTest should correctly apply an approximate GELU value (in_select=1) for a BF16 input, using 10 PWL Sigmoid segments" in {
        simulate(new siluandgeluPWLSigmoid10Segments) { c =>
            var tolerance = 0.015625f*4
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b0_01111111_0110001".U(16.W)) // 1.39978919809671290 ~ 1.3828125

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(11) // latency: 11cc since negative input so additional Adder with 3cc for the 1-f(-x) operation
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(11) // latency: 11
            c.io.out_a.expect("b1_01111011_1110000".U(16.W)) // -0.100210801903287099 ~ -0.1171875

            var mse = 0.0f
            var mse_MAE = 0.0f
            var max_AE = 0.0f
            if (positive_only) {
                step = (max_test_value) / N
                a = 0.0f
            }
            else {
                step = (2*max_test_value) / N
                a = -max_test_value
            }
            while (a <= max_test_value) {
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b1".U(1.W))
                c.clock.step(11)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat // GELU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output gelu-using-using-PWLSigmoid10-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact GELU value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
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
            println(f"GELU 10 PWL Sigmoid segments: Mean Squared Error (MSE): ${mse}")
            println(f"GELU 10 PWL Sigmoid segments: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU 10 PWL Sigmoid segments: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "siluandgeluPWLSigmoidTest should correctly apply an approximate GELU value (in_select=1) for a BF16 input, using 18 PWL Sigmoid segments" in {
        simulate(new siluandgeluPWLSigmoid18Segments) { c =>
            var tolerance = 0.015625f*4
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b0_01111111_0110010".U(16.W)) // 1.39978919809671290 ~ 1.3828125

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(11) // latency: 11cc since negative input so additional Adder with 3cc for the 1-f(-x) operation
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(11) // latency: 11
            c.io.out_a.expect("b1_01111011_1110000".U(16.W)) // -0.100210801903287099 ~ -0.1171875

            var mse = 0.0f
            var mse_MAE = 0.0f
            var max_AE = 0.0f
            if (positive_only) {
                step = (max_test_value) / N
                a = 0.0f
            }
            else {
                step = (2*max_test_value) / N
                a = -max_test_value
            }
            while (a <= max_test_value) {
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b1".U(1.W))
                c.clock.step(11)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat // GELU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    println(f"input x-value: ${a_upper16bits_float}")
                    println(f"output gelu-using-using-PWLSigmoid18-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    println(f"expected exact GELU value: ${expected}")
                    println(f"Difference: ${diff}")
                    println("###########")
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
            println(f"GELU 18 PWL Sigmoid segments: Mean Squared Error (MSE): ${mse}")
            println(f"GELU 18 PWL Sigmoid segments: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU 18 PWL Sigmoid segments: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "siluandgeluPWLSigmoidTest should correctly apply an approximate GELU value (in_select=1) for a BF16 input, using 20 PWL Sigmoid non-uniform segments with Extra Adder" in {
        simulate(new siluandgeluPWLSigmoid20NonUniformSegmentsUsingExtraAdder) { c =>
            var tolerance = 0.015625f*3
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b0_01111111_0110010".U(16.W)) // 1.39978919809671290 ~ 1.3828125

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(11) // extra adder 3cc latency for x < 0
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(11) // extra adder 3cc latency for x < 0
            c.io.out_a.expect("b1011110111110000".U(16.W)) // -0.100210801903287099 ~ -0.1171875

            var mse = 0.0f
            var mse_MAE = 0.0f
            var max_AE = 0.0f
            if (positive_only) {
                step = (max_test_value) / N
                a = 0.0f
            }
            else {
                step = (2*max_test_value) / N
                a = -max_test_value
            }
            while (a <= max_test_value) {
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b1".U(1.W))
                c.clock.step(11)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat // GELU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    // capture errors, print to stdout(=terminal)
                    print(s"${diff}, ")
                    // println(f"input x-value: ${a_upper16bits_float}")
                    // println(f"output gelu-using-using-PWLSigmoid20-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    // println(f"expected exact GELU value: ${expected}")
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
            println(f"GELU 20 PWL Sigmoid non-uniform segments with adder: Mean Squared Error (MSE): ${mse}")
            println(f"GELU 20 PWL Sigmoid non-uniform segments with adder: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU 20 PWL Sigmoid non-uniform segments with adder: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "siluandgeluPWLSigmoidTest should correctly apply an approximate GELU value (in_select=1) for a BF16 input, using 20 PWL Sigmoid non-uniform segments with Extra Intercepts" in {
        simulate(new siluandgeluPWLSigmoid20NonUniformSegments) { c =>
            var tolerance = 0.015625f*3
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b0_01111111_0110010".U(16.W)) // 1.39978919809671290 ~ 1.3828125

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8) 
            c.io.out_a.expect("b1011110111011110".U(16.W)) // -0.100210801903287099 ~ -0.10546875

            var mse = 0.0f
            var mse_MAE = 0.0f
            var max_AE = 0.0f
            if (positive_only) {
                step = (max_test_value) / N
                a = 0.0f
            }
            else {
                step = (2*max_test_value) / N
                a = -max_test_value
            }
            while (a <= max_test_value) {
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b1".U(1.W))
                c.clock.step(8)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat // GELU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    // capture errors, print to stdout(=terminal)
                    print(s"${diff}, ")
                    // println(f"input x-value: ${a_upper16bits_float}")
                    // println(f"output gelu-using-using-PWLSigmoid20-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    // println(f"expected exact GELU value: ${expected}")
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
            println(f"GELU 20 PWL Sigmoid non-uniform segments with Extra Intercepts: Mean Squared Error (MSE): ${mse}")
            println(f"GELU 20 PWL Sigmoid non-uniform segments with Extra Intercepts: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU 20 PWL Sigmoid non-uniform segments with Extra Intercepts: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
        }
    }

    "siluandgeluPWLSigmoidTest should correctly apply an approximate GELU value (in_select=1) for a BF16 input, using 36 PWL Sigmoid non-uniform segments with Extra Intercepts" in {
        simulate(new siluandgeluPWLSigmoid36NonUniformSegments) { c =>
            var tolerance = 0.015625f*3
            var verbose = 0
            var step = 0.0f // declare step variable
            var a = 0.0f // declare running variable
            c.io.in_a.poke("b0_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8) // latency: 1cc mult, 1cc for the parallel slope and intercept Regs, 1cc mult + 3cc add for the PWL segment, 1cc fullRangeSigmoidReg, 1cc final mult = 8cc
            c.io.out_a.expect("b0_00000000_0000000".U(16.W))

            c.io.in_a.poke("b0_01111111_1000000".U(16.W)) // 1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b0_01111111_0110010".U(16.W)) // 1.39978919809671290 ~ 1.3828125

            c.io.in_a.poke("b1_00000000_0000000".U(16.W))
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8)
            c.io.out_a.expect("b1_00000000_0000000".U(16.W))

            c.io.in_a.poke("b1_01111111_1000000".U(16.W)) // -1.5
            c.io.in_select.poke("b1".U(1.W))
            c.clock.step(8) 
            c.io.out_a.expect("b1011110111011110".U(16.W)) // -0.100210801903287099 ~ -0.10546875

            var mse = 0.0f
            var mse_MAE = 0.0f
            var max_AE = 0.0f
            if (positive_only) {
                step = (max_test_value) / N
                a = 0.0f
            }
            else {
                step = (2*max_test_value) / N
                a = -max_test_value
            }
            while (a <= max_test_value) {
                val a_upper16bits = ((floatToBigInt(a).toInt >> 16) & 0xFFFF).U(16.W) // .U(16.W) already keeps the lower 16 bits only, so & 0xFFFF is here only for clarity
                c.io.in_a.poke(a_upper16bits)
                c.io.in_select.poke("b1".U(1.W))
                c.clock.step(8)
                val a_upper16bits_float = java.lang.Float.intBitsToFloat((BigInt(a_upper16bits.litValue.toInt) << 16).toInt)
                val expected = (a_upper16bits_float * 0.5 * (1 + math.tanh((math.sqrt(2 / math.Pi) * (a_upper16bits_float + 0.044715 * math.pow(a_upper16bits_float,3)))))).toFloat // GELU formula
                val diff = expected - java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)
                if (verbose > 0) {
                    // capture errors, print to stdout(=terminal)
                    print(s"${diff}, ")
                    // println(f"input x-value: ${a_upper16bits_float}")
                    // println(f"output gelu-using-using-PWLSigmoid20-approx. value: ${java.lang.Float.intBitsToFloat((BigInt(c.io.out_a.peek().litValue.toInt) << 16).toInt)}")
                    // println(f"expected exact GELU value: ${expected}")
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
            println(f"GELU 36 PWL Sigmoid non-uniform segments with Extra Intercepts: Mean Squared Error (MSE): ${mse}")
            println(f"GELU 36 PWL Sigmoid non-uniform segments with Extra Intercepts: Mean Absolute Error (MAE): ${mse_MAE}")
            println(f"GELU 36 PWL Sigmoid non-uniform segments with Extra Intercepts: Maximum Absolute Error (Max AE): ${max_AE}")
            println("==============================")
            println("==============================")
        }
    }
}