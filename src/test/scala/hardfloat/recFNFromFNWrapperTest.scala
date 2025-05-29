package hardfloat

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class recFNFromFNWrapperTest extends AnyFreeSpec with Matchers {
    "rawFloatFromFNWrapper should convert Floating Point number correctly to raw Format for expWidth=5 and sigWidth=10" in { 
        var verbose = false
        val expWidth = 5 // this means bias = 2^(expWidth-1) - 1 = 15
        val sigWidth = 11
        simulate(new recFNFromFNWrapper(expWidth, sigWidth)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString)// .replace(' ', '0')
            val input = "b0_10100_1000000000".U(16.W)
            c.io.in.poke(input)
            c.clock.step()
            val output = c.io.out.peek()
            println(s"Input FP16 format: ${toBinary(input.litValue.toInt, 16)}") // FP16 format
            println(s"Output recoded format: ${toBinary(output.litValue.toInt, 17)}") // recoded format
        }
    }
}