package hardfloat

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class rawFloatFromFNWrapperTest extends AnyFreeSpec with Matchers {
    "rawFloatFromFNWrapper should convert Floating Point number correctly to raw Format for expWidth=5 and sigWidth=10" in { 
        var verbose = false
        val expWidth = 5 // this means bias = 2^(expWidth-1) - 1 = 15
        val sigWidth = 11
        simulate(new rawFloatFromFNWrapper(expWidth, sigWidth)) { c =>
            def toBinary(i: Int, digits: Int = 16) =
                String.format("%" + digits + "s", i.toBinaryString)// .replace(' ', '0')
            val input = "b0_10100_1000000000".U(16.W)
            c.io.in.poke(input)
            c.clock.step()
            val raw_sign = c.io.sign.peek()
            val raw_sExp = c.io.sExp.peek()
            val raw_sig = c.io.sig.peek()
            println(s"Input: ${input.litValue}")
            println(s"Output raw_sign: ${toBinary(raw_sign.litValue.toInt, 1)}")
            println(s"Output raw_sExp: ${toBinary(raw_sExp.litValue.toInt, expWidth)}")
            println(s"Output raw_sig: ${toBinary(raw_sig.litValue.toInt, sigWidth-1)}")
            // println(s"sign: ${raw.sign.litValue}")
            // println(s"sExp: ${raw.sExp.litValue}")
            // println(s"sig: ${raw.sig.litValue}")
        }
    }
}