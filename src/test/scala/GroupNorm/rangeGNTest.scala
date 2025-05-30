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
        var verbose = 1
        println("Starting test with C = 320, so N=10 elements per group")
        simulate(new rangeGN(C = 320)) { dut => 
            
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            // ###################################################################################################
            // ########################################      test 1        #######################################
            // create vector of 10 elements with values 0,1,2,...,9 in BF16 format
            // real GroupNorm output: (0-4.5)/2.87, (1-4.5)/2.87, ..., (9-4.5)/2.87
            // range GroupNorm output: ((0-4.5)*2.625)/(9-0), ((1-4.5)*2.625)/(9-0)), ..., ((9-4.5)*2.625)/(9-0)
            var tolerance = 0.0079f // tolerance on output values, should scale with input values' exponent!
            println("Testing rangeGN for ten inputs 0,1,2,...,9")
            var inputVec = (0 until 10).map(i => ((floatToBigInt(i.toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            inputVec.zipWithIndex.foreach { case (value, idx) =>
                dut.io.in_a(idx).poke(value)
            }

            dut.clock.step(29) // total latency for N=10 is 28cc

            var expectedOutput = (0 until 10).map(i => ((floatToBigInt((((i - 4.5f)*2.625f) / 9f).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            var diff_outputs = expectedOutput.zip(dut.io.out_a).map { case (exp, act) =>
                java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
            }
            if (verbose > 0) {
                println(s"Expected outputs - Actual outputs: ${diff_outputs}")
                if (verbose > 1) { // print everything
                    var expectedNumerators = (0 until 10).map(i => ((floatToBigInt((i - 4.5f).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
                    
                    var expected_divResultsOut = (0 until 10).map(i => ((floatToBigInt(((i - 4.5f) / 9f).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector // no final mult with alpha yet
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
            println("Testing rangeGN for ten random inputs between -1000 and 1000")
            inputVec = (0 until 10).map(_ => ((floatToBigInt(scala.util.Random.nextFloat() * 2000 - 1000).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            inputVec.zipWithIndex.foreach { case (value, idx) =>
                dut.io.in_a(idx).poke(value)
            }

            dut.clock.step(29)
            
            expectedOutput = (0 until 10).map(i => ((floatToBigInt((((i - 4.5f)*2.625f) / 9f).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
            diff_outputs = expectedOutput.zip(dut.io.out_a).map { case (exp, act) =>
                java.lang.Float.intBitsToFloat((BigInt(exp.litValue.toInt) << 16).toInt) - java.lang.Float.intBitsToFloat((BigInt(act.peek().litValue.toInt) << 16).toInt)
            }
            if (verbose > 0) {
                println(s"Expected outputs - Actual outputs: ${diff_outputs}")
                if (verbose > 1) { // print everything
                    var expectedNumerators = (0 until 10).map(i => ((floatToBigInt((i - 4.5f).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector
                    
                    var expected_divResultsOut = (0 until 10).map(i => ((floatToBigInt(((i - 4.5f) / 9f).toFloat).toInt >> 16) & 0xFFFF).U(16.W)).toVector // no final mult with alpha yet
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

        }
    }
}