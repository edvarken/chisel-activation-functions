package GroupNorm

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import silu.FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}

class rangeGNTest extends AnyFreeSpec with Matchers {
    "rangeGNTest should correctly apply range GroupNorm to every element of the input vector of C/32 elements" in {
        var latencyNis10 = 29
        var latencyNis20 = 31
        var latencyNis40 = 39
        var verbose = 1 // 2
        println(s"Starting test with C = 320, so N=10 elements per group; using ${latencyNis10} cc's latency")
        simulate(new rangeGN(C = 320)) { dut => 
            var N = 10
            // ###################################################################################################
            // ########################################      test 1        #######################################
            // create vector of 10 elements with values 0,1,2,...,9 in BF16 format
            // real GroupNorm output: (0-4.5)/2.87, (1-4.5)/2.87, ..., (9-4.5)/2.87
            // range GroupNorm output: ((0-4.5)*2.146)/(9-0)), ..., ((9-4.5)*2.146)/(9-0)
            var tolerance = 0.012f // tolerance on output values, should scale with input values' exponent!
            println("Testing rangeGN for 10 inputs 0,1,2,...,9")
            var inputVec = (0 until N).map(i => ((floatToBigInt(i.toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            inputVec.zipWithIndex.foreach { case (value, idx) =>
                dut.io.in_a(idx).poke(value)
            }

            dut.clock.step(latencyNis10) // total latency for N=10 is 29cc

            var expectedOutput = (0 until N).map(i => ((floatToBigInt((((i - 4.5f)*2.146f) / (N-1).toFloat).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            var diff_outputs = expectedOutput.zip(dut.io.out_a).map { case (exp, act) =>
                java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
            }
            if (verbose > 0) {
                println(s"Expected outputs - Actual outputs: ${diff_outputs}")
                if (verbose > 1) { // print everything
                    var expectedNumerators = (0 until N).map(i => ((floatToBigInt((i - 4.5f).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
                    
                    var expected_divResultsOut = (0 until N).map(i => ((floatToBigInt(((i - 4.5f) / (N-1).toFloat).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector // no final mult with alpha yet
                    println(s"Expected Mean: ${(floatToBigIntBF16(4.5f).U(16.W)).litValue.toString(2)}")
                    println(s"Actual Mean:   ${dut.io.debugMeanOut.peek().litValue.toString(2)}")

                    println(s"Expected Numerators: ${expectedNumerators.map(_.litValue.toString(2))}")
                    println(s"Actual Numerators:   ${dut.io.debugNumeratorsOut.map(_.peek().litValue.toString(2))}")

                    println(s"Expected Range: ${floatToBigIntBF16(9f).U(16.W).litValue.toString(2)}")
                    println(s"Actual Range:   ${dut.io.debugRangeOut.peek().litValue.toString(2)}")

                    var diff_divresults = expected_divResultsOut.zip(dut.io.debugdivResultsOut).map { case (exp, act) =>
                        java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
                    }
                    println(s"Expected div results - Actual div results: ${diff_divresults}")
                }
            }
            assert(diff_outputs.forall(diff => math.abs(diff) < tolerance), s"Expected outputs differ too much from actual outputs")

            // ###################################################################################################
            // ########################################      test 2       #######################################
            tolerance = 8.0f // log2(1000) ~ 9, if second to last bit in manttisa is wrong, then error can be 2^9 * 2^-6 = 2^3 = 8
            println("Testing rangeGN for 10 random inputs between -1000 and 1000")
            inputVec = (0 until N).map(_ => ((floatToBigInt(scala.util.Random.nextFloat() * 2000 - 1000).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            inputVec.zipWithIndex.foreach { case (value, idx) => dut.io.in_a(idx).poke(value) }
            var inputfloats = inputVec.map(i => java.lang.Float.intBitsToFloat((BigInt(i.litValue.toInt) << 16).toInt))
            var mean = (inputfloats.sum) / N.toFloat
            var range = inputfloats.max - inputfloats.min
            
            dut.clock.step(latencyNis10)

            expectedOutput = inputVec.map { i =>
                val floatVal = java.lang.Float.intBitsToFloat((BigInt(i.litValue.toInt) << 16).toInt)
                val scaled   = floatToBigInt(((floatVal - mean) * 2.146f) / range)
                val shifted  = (scaled.toInt >> 16) & 0xFFFF
                shifted.U(16.W)
            }.toVector
            diff_outputs = expectedOutput.zip(dut.io.out_a).map { case (exp, act) =>
                java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
            }
            if (verbose > 0) {
                println(s"Expected outputs - Actual outputs: ${diff_outputs}")
                if (verbose > 1) { // print more details
                    println(s"Expected Mean: ${(floatToBigIntBF16(mean).U(16.W)).litValue.toString(2)}")
                    println(s"Actual Mean:   ${dut.io.debugMeanOut.peek().litValue.toString(2)}")

                    println(s"Expected Range: ${floatToBigIntBF16(range).U(16.W).litValue.toString(2)}")
                    println(s"Actual Range:   ${dut.io.debugRangeOut.peek().litValue.toString(2)}")

                    println(s"Expected div results - Actual div results: ${diff_outputs}")
                }
            }
            assert(diff_outputs.forall(diff => math.abs(diff) < tolerance), s"Expected outputs differ too much from actual outputs")
        }

        println("######################################################")
        println(s"Starting test with C = 640, so N=20 elements per group; using ${latencyNis20} cc's latency")
        simulate(new rangeGN(C = 640)) { dut => 
            var N = 20
            // ###################################################################################################
            // ########################################      test 1        #######################################
            // create vector of 20 elements with values 0,1,2,...,19 in BF16 format
            var tolerance = 0.012f
            println("Testing rangeGN for 20 inputs 0,1,2,...,19")
            var inputVec = (0 until N).map(i => ((floatToBigInt(i.toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            inputVec.zipWithIndex.foreach { case (value, idx) =>
                dut.io.in_a(idx).poke(value)
            }

            dut.clock.step(latencyNis20) // total latency for N=20 is 31, however according to formula: ceil(log2(20))*3+17, it should be 32 cc's

            var expectedOutput = (0 until N).map(i => ((floatToBigInt((((i - 9.5f)*2.447f) / (N-1).toFloat).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            var diff_outputs = expectedOutput.zip(dut.io.out_a).map { case (exp, act) =>
                java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
            }
            if (verbose > 0) {
                println(s"Expected outputs - Actual outputs: ${diff_outputs}")
                if (verbose > 1) { // print everything
                    var expectedNumerators = (0 until N).map(i => ((floatToBigInt((i - 9.5f).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
                    
                    var expected_divResultsOut = (0 until N).map(i => ((floatToBigInt(((i - 9.5f) / (N-1).toFloat).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector // no final mult with alpha yet
                    println(s"Expected Mean: ${(floatToBigIntBF16(9.5f).U(16.W)).litValue.toString(2)}")
                    println(s"Actual Mean:   ${dut.io.debugMeanOut.peek().litValue.toString(2)}")

                    println(s"Expected Numerators: ${expectedNumerators.map(_.litValue.toString(2))}")
                    println(s"Actual Numerators:   ${dut.io.debugNumeratorsOut.map(_.peek().litValue.toString(2))}")

                    println(s"Expected Range: ${floatToBigIntBF16((N-1).toFloat).U(16.W).litValue.toString(2)}")
                    println(s"Actual Range:   ${dut.io.debugRangeOut.peek().litValue.toString(2)}")

                    var diff_divresults = expected_divResultsOut.zip(dut.io.debugdivResultsOut).map { case (exp, act) =>
                        java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
                    }
                    println(s"Expected div results - Actual div results: ${diff_divresults}")
                }
            }
            assert(diff_outputs.forall(diff => math.abs(diff) < tolerance), s"Expected outputs differ too much from actual outputs")

            // ###################################################################################################
            // ########################################      test 2       #######################################
            tolerance = 8.0f // log2(1000) ~ 9, if second to last bit in manttisa is wrong, then error can be 2^9 * 2^-6 = 2^3 = 8
            println("Testing rangeGN for 20 random inputs between -1000 and 1000")
            inputVec = (0 until N).map(_ => ((floatToBigInt(scala.util.Random.nextFloat() * 2000 - 1000).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            inputVec.zipWithIndex.foreach { case (value, idx) => dut.io.in_a(idx).poke(value) }
            var inputfloats = inputVec.map(i => java.lang.Float.intBitsToFloat((BigInt(i.litValue.toInt) << 16).toInt))
            var mean = (inputfloats.sum) / N.toFloat
            var range = inputfloats.max - inputfloats.min

            dut.clock.step(latencyNis20)
            
            expectedOutput = inputVec.map { i =>
                val floatVal = java.lang.Float.intBitsToFloat((BigInt(i.litValue.toInt) << 16).toInt)
                val scaled   = floatToBigInt(((floatVal - mean) * 2.447f) / range)
                val shifted  = (scaled.toInt >> 16) & 0xFFFF
                shifted.U(16.W)
            }.toVector
            diff_outputs = expectedOutput.zip(dut.io.out_a).map { case (exp, act) =>
                java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
            }
            if (verbose > 0) {
                println(s"Expected outputs - Actual outputs: ${diff_outputs}")
                if (verbose > 1) { // print more details
                    println(s"Expected Mean: ${(floatToBigIntBF16(mean).U(16.W)).litValue.toString(2)}")
                    println(s"Actual Mean:   ${dut.io.debugMeanOut.peek().litValue.toString(2)}")

                    println(s"Expected Range: ${floatToBigIntBF16(range).U(16.W).litValue.toString(2)}")
                    println(s"Actual Range:   ${dut.io.debugRangeOut.peek().litValue.toString(2)}")

                    println(s"Expected div results - Actual div results: ${diff_outputs}")
                }
            }
            assert(diff_outputs.forall(diff => math.abs(diff) < tolerance), s"Expected outputs differ too much from actual outputs")

        }
        println("######################################################")
        println(s"Starting test with C = 1280, so N=40 elements per group; using ${latencyNis40} cc's latency")
        simulate(new rangeGN(C = 1280)) { dut => 
            var N = 40
            // ###################################################################################################
            // ########################################      test 1        #######################################
            // create vector of 40 elements with values 0,1,2,...,39 in BF16 format
            var tolerance = 0.047f 
            println("Testing rangeGN for 40 inputs 0,1,2,...,39")
            var inputVec = (0 until N).map(i => ((floatToBigInt(i.toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            inputVec.zipWithIndex.foreach { case (value, idx) =>
                dut.io.in_a(idx).poke(value)
            }

            dut.clock.step(latencyNis40) // total latency for N=40 is 39, however according to formula: ceil(log2(40))*3+17, it should only be 35 cc's

            var expectedOutput = (0 until N).map(i => ((floatToBigInt((((i - 19.5f)*2.716f) / (N-1).toFloat).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            var diff_outputs = expectedOutput.zip(dut.io.out_a).map { case (exp, act) =>
                java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
            }
            if (verbose > 0) {
                println(s"Expected outputs - Actual outputs: ${diff_outputs}")
                if (verbose > 1) { // print everything
                    var expectedNumerators = (0 until N).map(i => ((floatToBigInt((i - 19.5f).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
                    
                    var expected_divResultsOut = (0 until N).map(i => ((floatToBigInt(((i - 19.5f) / (N-1).toFloat).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector // no final mult with alpha yet
                    println(s"Expected Mean: ${(floatToBigIntBF16(19.5f).U(16.W)).litValue.toString(2)}")
                    println(s"Actual Mean:   ${dut.io.debugMeanOut.peek().litValue.toString(2)}")

                    println(s"Expected Numerators: ${expectedNumerators.map(_.litValue.toString(2))}")
                    println(s"Actual Numerators:   ${dut.io.debugNumeratorsOut.map(_.peek().litValue.toString(2))}")

                    println(s"Expected Range: ${floatToBigIntBF16((N-1).toFloat).U(16.W).litValue.toString(2)}")
                    println(s"Actual Range:   ${dut.io.debugRangeOut.peek().litValue.toString(2)}")

                    var diff_divresults = expected_divResultsOut.zip(dut.io.debugdivResultsOut).map { case (exp, act) =>
                        java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
                    }
                    println(s"Expected div results - Actual div results: ${diff_divresults}")
                }
            }
            assert(diff_outputs.forall(diff => math.abs(diff) < tolerance), s"Expected outputs differ too much from actual outputs")

            // ###################################################################################################
            // ########################################      test 2       #######################################
            tolerance = 8.0f // log2(1000) ~ 9, if second to last bit in mantissa is wrong, then error can be 2^9 * 2^-6 = 2^3 = 8
            println("Testing rangeGN for 40 random inputs between -1000 and 1000")
            inputVec = (0 until N).map(_ => ((floatToBigInt(scala.util.Random.nextFloat() * 2000 - 1000).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            inputVec.zipWithIndex.foreach { case (value, idx) => dut.io.in_a(idx).poke(value) }
            var inputfloats = inputVec.map(i => java.lang.Float.intBitsToFloat((BigInt(i.litValue.toInt) << 16).toInt))
            var mean = (inputfloats.sum) / N.toFloat
            var range = inputfloats.max - inputfloats.min

            dut.clock.step(latencyNis40)
            
            expectedOutput = inputVec.map { i =>
                val floatVal = java.lang.Float.intBitsToFloat((BigInt(i.litValue.toInt) << 16).toInt)
                val scaled   = floatToBigInt(((floatVal - mean) * 2.7162f) / range)
                val shifted  = (scaled.toInt >> 16) & 0xFFFF
                shifted.U(16.W)
            }.toVector
            diff_outputs = expectedOutput.zip(dut.io.out_a).map { case (exp, act) =>
                java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
            }
            if (verbose > 0) {
                println(s"Expected outputs - Actual outputs: ${diff_outputs}")
                if (verbose > 1) { // print more details
                    println(s"Expected Mean: ${(floatToBigIntBF16(mean).U(16.W)).litValue.toString(2)}")
                    println(s"Actual Mean:   ${dut.io.debugMeanOut.peek().litValue.toString(2)}")

                    println(s"Expected Range: ${floatToBigIntBF16(range).U(16.W).litValue.toString(2)}")
                    println(s"Actual Range:   ${dut.io.debugRangeOut.peek().litValue.toString(2)}")

                    println(s"Expected div results - Actual div results: ${diff_outputs}")
                }
            }
            assert(diff_outputs.forall(diff => math.abs(diff) < tolerance), s"Expected outputs differ too much from actual outputs")

        }

    }
}