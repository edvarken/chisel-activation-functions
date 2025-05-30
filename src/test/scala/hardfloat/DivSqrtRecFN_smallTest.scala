package hardfloat

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class DivSqrtRecFN_smallTest extends AnyFreeSpec with Matchers {
    "DivSqrtRawFN_small should compute division correctly for expWidth=5 and sigWidth=11(includes implicit m.s. bit)" in { // Compare with ETH Zuerich's CV-FPU's division Verilog implementation
        var verbose = true
        val expWidth = 5 // this means bias = 2^(expWidth-1) - 1 = 15
        val sigWidth = 11
        simulate(new DivSqrtRecFN_small(expWidth, sigWidth, options = 0)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            // ###################################################################################################
            // ########################################      test 1        #######################################
            println("FP16: Starting test 1...")
            while (!c.io.inReady.peek().litToBoolean) { // wait until inReady is true to start the operation
                c.clock.step(1)
            }
            c.io.inValid.poke(true.B) // You must drive the inValid signal high for 1 cycle and then low
            c.io.sqrtOp.poke(false.B) // important so sqrtOp_Z is false(sqrtOp_Z := io.sqrtOp), otherwise it will not compute division!!
            c.io.a.poke("b0_10100_1000000000".U(16.W)) // 2^5*1.5 = 48
            c.io.b.poke("b0_10011_0100000000".U(16.W)) // 2^4*1.25 = 20
            // result = a / b = 2.4
            c.io.roundingMode.poke(0.U) // round_nearest_even
            c.io.detectTininess.poke(0.U) // detectTininess_never
            c.clock.step(1)
            c.io.inValid.poke(false.B)
            var waited = 0
            println(s"Start waiting for rawOutValid_div to become true...")
            while (!c.io.outValid_div.peek().litToBoolean && waited < 40) { // Wait until rawOutValid_div (or _sqrt) becomes true to read the result.
                c.clock.step(1)
                waited += 1
            }
            if (verbose) {
                println(s"inReady: ${c.io.inReady.peek().litToBoolean}")
                println(s"inValid: ${c.io.inValid.peek().litToBoolean}")
                println(s"rawOutValid_div: ${c.io.outValid_div.peek().litToBoolean}")
                println(s"rawOutValid_sqrt: ${c.io.outValid_sqrt.peek().litToBoolean}")
            }
            assert(c.io.outValid_div.peek().litToBoolean, "Division result did not become valid") // The assertion passes if this value is true.
            println(s"waited cycles: $waited") // should be 13?
            var result = c.io.out.peek().litValue // Get the result as a BigInt
            if (verbose) {
                println(f"Result: ${toBinary(result.toInt, 16)}") // recoded format!, since mantissa has an extra m.s. bit for special cases, ignore it for now
                println(f"sign: ${toBinary(((result >> 15) & 0x1).toInt, 1)}") // 0
                println(f"exponent: ${toBinary(((result >> 10) & 0x1F).toInt, 5)}") // 2^(16-15) = 2^1 = 2
                println(f"significand: ${toBinary((result & 0x3FF).toInt, 10)}") // 1,2001953125
            }
            assert(result == "b0_10000_0011001101".U(16.W).litValue, "Division result does not match expected value") 
            // Result is 2^1 * 1.2001953125 = 2.400390625 :)
            println("############")
            // ###################################################################################################
            // ########################################      test 2        #######################################
            println("FP16: Starting test 2...")
            while (!c.io.inReady.peek().litToBoolean) { // wait until inReady is true to start another operation
                c.clock.step(1)
            }
            c.io.inValid.poke(true.B) 
            c.io.sqrtOp.poke(false.B) 
            c.io.a.poke("b0_10111_1000100011".U(16.W)) // 2^8 * 1.535 = 392.8
            c.io.b.poke("b0_10000_0100111001".U(16.W)) // 2^1 * 1.306 = 2.612
            // result = a / b = 150.382848392 
            c.io.roundingMode.poke(0.U) 
            c.io.detectTininess.poke(0.U) 
            c.clock.step(1)
            c.io.inValid.poke(false.B)
            waited = 0
            println(s"Start waiting for rawOutValid_div to become true...")
            while (!c.io.outValid_div.peek().litToBoolean && waited < 40) {
                c.clock.step(1)
                waited += 1
            }
            if (verbose) {
                println(s"inReady: ${c.io.inReady.peek().litToBoolean}")
                println(s"inValid: ${c.io.inValid.peek().litToBoolean}")
                println(s"rawOutValid_div: ${c.io.outValid_div.peek().litToBoolean}")
                println(s"rawOutValid_sqrt: ${c.io.outValid_sqrt.peek().litToBoolean}")
            }
            assert(c.io.outValid_div.peek().litToBoolean, "Division result did not become valid") // if assertion fails it prints its message.
            println(s"waited cycles: $waited") // should be 13?
            result = c.io.out.peek().litValue 
            
            if (verbose) {
                println(f"Result: ${toBinary(result.toInt, 16)}") 
                println(f"sign: ${toBinary(((result >> 15) & 0x1).toInt, 1)}")
                println(f"exponent: ${toBinary(((result >> 10) & 0x1F).toInt, 5)}")
                println(f"significand: ${toBinary((result & 0x3FF).toInt, 10)}")
            }
            assert(result == "b0_10110_0010110011".U(16.W).litValue, "Division result does not match expected value") 
            println("############")
            // Result is 2^7 * 1.175 = 150.4
        }
    }

    "DivSqrtRawFN_small should compute division correctly for expWidth=8 and sigWidth=8(includes implicit m.s. bit)" in { // Compare with ETH Zuerich's CV-FPU's division Verilog implementation
        var verbose = true
        val expWidth = 8 // this means bias = 2^(expWidth-1) - 1 = 127
        val sigWidth = 8 // this is actually 7 bits of significand + 1 implicit bit, so total 8 bits
        simulate(new DivSqrtRecFN_small(expWidth, sigWidth, options = 0)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString).replace(' ', '0')
            // ###################################################################################################
            // ########################################      test 1        #######################################
            println("BF16: Starting test 1...")
            while (!c.io.inReady.peek().litToBoolean) { // wait until inReady is true to start the operation
                c.clock.step(1)
            }
            c.io.inValid.poke(true.B) // You must drive the inValid signal high for 1 cycle and then low
            c.io.sqrtOp.poke(false.B) // important so sqrtOp_Z is false(sqrtOp_Z := io.sqrtOp), otherwise it will not compute division!!
            c.io.a.poke("b0_10000000_1001001".U(16.W)) // 2^1 * 1.5703125  ~ 3.1406
            c.io.b.poke("b0_10011000_0100011".U(16.W)) // 2^25 * 1.2734375 = 42729470
            // result = a / b = 7.34996245e-8: 0_01100111_0011101
            c.io.roundingMode.poke(0.U) // round_nearest_even
            c.io.detectTininess.poke(0.U) // detectTininess_never
            c.clock.step(1)
            c.io.inValid.poke(false.B)
            var waited = 0
            println(s"Start waiting for rawOutValid_div to become true...")
            while (!c.io.outValid_div.peek().litToBoolean && waited < 40) { // Wait until rawOutValid_div (or _sqrt) becomes true to read the result.
                c.clock.step(1)
                waited += 1
            }
            if (verbose) {
                println(s"inReady: ${c.io.inReady.peek().litToBoolean}")
                println(s"inValid: ${c.io.inValid.peek().litToBoolean}")
                println(s"rawOutValid_div: ${c.io.outValid_div.peek().litToBoolean}")
                println(s"rawOutValid_sqrt: ${c.io.outValid_sqrt.peek().litToBoolean}")
            }
            assert(c.io.outValid_div.peek().litToBoolean, "Division result did not become valid") // The assertion passes if this value is true.
            println(s"waited cycles: $waited") // should be 13?
            var result = c.io.out.peek().litValue
            if (verbose) {
                println(f"Result: ${toBinary(result.toInt, 16)}")
                println(f"sign: ${toBinary(((result >> 15) & 0x1).toInt, 1)}")
                println(f"exponent: ${toBinary(((result >> 7) & 0xFF).toInt, 8)}")
                println(f"significand: ${toBinary((result & 0x7F).toInt, 7)}")
            }
            assert(result == "b0_01100111_0011110".U(16.W).litValue, "Division result does not match expected value") 
            // Result is 7.34996245e-8: 0_01100111_0011101, but rounds to nearest even so 0_01100111_0011110
            println("############")
            // ###################################################################################################
            // ########################################      BF16: iterative test        #######################################

            // TODO: there is still a BUG when dividing a negative number, the exponent becomes all 1s :(
            // no matter if 2 negative numbers or only 1 is involved, and no matter if they are same absolute value or not, exponent becomes 11111111 :(

            println("BF16: Starting iterative test...")
            for ((a, b, golden_result) <- Seq(
                ("b0_10000001_0000000".U(16.W), "b0_10000001_0000000".U(16.W), "b0_01111111_0000000".U(16.W).litValue), // 4.0 / 4.0 = 1.0
                ("b0_10000010_0000000".U(16.W), "b0_01111111_1000000".U(16.W), "b0_10000001_0101011".U(16.W).litValue), // 8.0 / 1.5 = 5.33333333333
                ("b0_01111111_0000000".U(16.W), "b0_01111111_0000000".U(16.W), "b0_01111111_0000000".U(16.W).litValue), // 1.0 / 1.0 = 1.0
                ("b0_00000000_0000000".U(16.W), "b0_10000000_0000000".U(16.W), "b0_00000000_0000000".U(16.W).litValue), // 0.0 / 2.0 = 0.0
                // 1 negative number
                ("b1_10000000_0000000".U(16.W), "b0_10000000_0000000".U(16.W), "b1_01111111_0000000".U(16.W).litValue), // -2.0 / 2.0 = -1.0 
                ("b0_10000000_0000000".U(16.W), "b1_10000000_0000000".U(16.W), "b1_01111111_0000000".U(16.W).litValue), // 2.0 / -2.0 = -1.0 
                ("b1_10000000_0000000".U(16.W), "b0_01111111_0000000".U(16.W), "b1_10000000_0000000".U(16.W).litValue), // -2.0 / 1.0 = -2.0 
                ("b1_10000010_0000000".U(16.W), "b0_01111111_1000000".U(16.W), "b1_10000001_0101011".U(16.W).litValue), // -8.0 / 1.5 = -5.33333333333
                // 2 negative numbers
                ("b1_10000000_0000000".U(16.W), "b1_01111111_0000000".U(16.W), "b0_10000000_0000000".U(16.W).litValue), // -2.0 / -1.0 = +2.0
                // special cases: these fail due to different rec FN format
                // ("b0_00000000_0000001".U(16.W), "b0_01111111_0000000".U(16.W), "b0_00000000_0000001".U(16.W).litValue), // smallest subnormal / 1.0 = smallest subnormal 
                // ("b0_10000000_0000000".U(16.W), "b0_00000000_0000001".U(16.W), "b0_11111111_1111111".U(16.W).litValue), // 2.0 / smallest subnormal = large number
            )) {
                while (!c.io.inReady.peek().litToBoolean) { // wait until inReady is true to start another operation
                    c.clock.step(1)
                }
                c.io.inValid.poke(true.B) 
                c.io.sqrtOp.poke(false.B) 
                c.io.a.poke(a) 
                c.io.b.poke(b)
                c.io.roundingMode.poke(0.U) 
                c.io.detectTininess.poke(0.U) 
                c.clock.step(1)
                c.io.inValid.poke(false.B)
                waited = 0
                println(s"Start waiting for rawOutValid_div to become true...")
                while (!c.io.outValid_div.peek().litToBoolean && waited < 40) {
                    c.clock.step(1)
                    waited += 1
                }
                assert(c.io.outValid_div.peek().litToBoolean, "Division result did not become valid") // if assertion fails it prints its message.
                println(s"waited cycles: $waited") // should be 13?
                result = c.io.out.peek().litValue 
                if (verbose) {
                    println(f"Result: ${toBinary(result.toInt, 16)}")
                println(f"sign: ${toBinary(((result >> 15) & 0x1).toInt, 1)}")
                println(f"exponent: ${toBinary(((result >> 7) & 0xFF).toInt, 8)}")
                println(f"significand: ${toBinary((result & 0x7F).toInt, 7)}")
                println("############")
                }
                assert(result == golden_result, "Division result does not match expected value") 
            }
        }
    }
}
