package silu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import FloatUtils.{floatToBigInt, floatToBigIntBF16, doubleToBigInt, getExpMantWidths,
                   floatAdd, doubleAdd}
import scala.collection.mutable.Queue

class FPAdd16Test extends AnyFreeSpec with Matchers {
    "FPAdd16 should add BF16 numbers correctly" in {
        simulate(new FPAdd16) { c =>
            val inputsQueue = Queue((0.0f, 0.0f)) // starts with an initial tuple (0.0f, 0.0f)
            val usedInputsQueue = Queue[(Float, Float)]() // starts as a completely empty Queue/FIFO
            val expectedQueue = Queue(None, None, Some(0.0f)) // prepare the expected FIFO: Front -> (None, None, Some(0.0f)) <- Rear
            // we will read from this, so after the 3rd clock cycle we will have the first result of the Adder (3 cycle latency)

            def randFloat(): Float = {
                scala.util.Random.nextFloat() * 10000.0f - 5000.0f
            }

            for (_ <- 0 until 20) { // prepare 20 additions and their results in two queues
                val a = randFloat()
                val b = randFloat()
                inputsQueue.enqueue((a, b)) // adds to the rear of the queue(=FIFO)
                expectedQueue.enqueue(Some(floatAdd(a, b)))
            }

            while (inputsQueue.nonEmpty) {
                val (a, b) = inputsQueue.dequeue() // removes from the front of the queue(=FIFO)
                c.io.a.poke((floatToBigInt(a) >> 16).U(16.W))
                c.io.b.poke((floatToBigInt(b) >> 16).U(16.W))
                c.clock.step(1)
                usedInputsQueue.enqueue((a, b))
                val expectedOption = expectedQueue.dequeue()

                if (expectedOption.nonEmpty) { // the first non empty element is Some(0.0f) (4th element in FIFO)
                    val expected = expectedOption.get
                    val (inputa, inputb) = usedInputsQueue.dequeue()
                    println(s"$inputa + $inputb = $expected")
                    println(f"A: ${floatToBigInt(inputa)}%x, B: ${floatToBigInt(inputb)}%x, HW: ${c.io.res.peek().litValue}%x, EMU: ${floatToBigInt(floatAdd(inputa, inputb))}%x")

                    // make sure to check after right amount of clock cycles=latency of the adder!
                    assert((c.io.res.peek().litValue.toInt & 0xFFFF0000) == (floatToBigIntBF16(expected).U(16.W).litValue.toInt & 0xFFFF0000),
                    s"Expected ${floatToBigIntBF16(expected).U(16.W)} but got ${c.io.res.peek().litValue.toInt & 0xFFFF0000}")
                }
            }

            while (expectedQueue.nonEmpty) {
                val expectedOption = expectedQueue.dequeue()
                val expected = expectedOption.get
                val (inputa, inputb) = usedInputsQueue.dequeue()

                println(f"$inputa%f + $inputb%f = $expected%f")
                println(f"A: ${floatToBigInt(inputa)}%x, B: ${floatToBigInt(inputb)}%x, HW: ${c.io.res.peek().litValue}%x, EMU: ${floatToBigInt(floatAdd(inputa, inputb))}%x")
                c.clock.step(1)

                assert((c.io.res.peek().litValue.toInt & 0xFFFF0000) == (floatToBigIntBF16(expected).U(16.W).litValue.toInt & 0xFFFF0000),
                s"Expected ${floatToBigIntBF16(expected).U(16.W)} but got ${c.io.res.peek().litValue.toInt & 0xFFFF0000}")
            }
        }
    }
}

class FPAdd32Test extends AnyFreeSpec with Matchers {
    "FPAdd32 should add floating point numbers correctly" in {
        simulate(new FPAdd32) { c =>
            val inputsQueue = Queue((0.0f, 0.0f)) // starts with an initial tuple (0.0f, 0.0f)
            val usedInputsQueue = Queue[(Float, Float)]() // starts as a completely empty Queue/FIFO
            val expectedQueue = Queue(None, None, Some(0.0f)) // prepare the expected FIFO: Front -> (None, None, Some(0.0f)) <- Rear
            // we will read from this, so after the 3rd clock cycle we will have the first result of the Adder (3 cycle latency)

            def randFloat(): Float = {
                scala.util.Random.nextFloat() * 10000.0f - 5000.0f
            }

            for (_ <- 0 until 20) { // prepare 20 additions and their results in two queues
                val a = randFloat()
                val b = randFloat()
                inputsQueue.enqueue((a, b)) // adds to the rear of the queue(=FIFO)
                expectedQueue.enqueue(Some(floatAdd(a, b)))
            }

            while (inputsQueue.nonEmpty) {
                val (a, b) = inputsQueue.dequeue() // removes from the front of the queue(=FIFO)
                c.io.a.poke(floatToBigInt(a).U)
                c.io.b.poke(floatToBigInt(b).U)
                c.clock.step(1)
                usedInputsQueue.enqueue((a, b))
                val expectedOption = expectedQueue.dequeue()

                if (expectedOption.nonEmpty) { // the first non empty element is Some(0.0f) (4th element in FIFO)
                    val expected = expectedOption.get
                    val (inputa, inputb) = usedInputsQueue.dequeue()
                    println(s"$inputa + $inputb = $expected")
                    println(f"A: ${floatToBigInt(inputa)}%x, B: ${floatToBigInt(inputb)}%x, HW: ${c.io.res.peek().litValue}%x, EMU: ${floatToBigInt(floatAdd(inputa, inputb))}%x")
                    c.io.res.expect(floatToBigInt(expected).U) // depends on amount of clock cycles=latency of the adder!
                }
            }

            while (expectedQueue.nonEmpty) {
                val expectedOption = expectedQueue.dequeue()
                val expected = expectedOption.get
                val (inputa, inputb) = usedInputsQueue.dequeue()

                println(f"$inputa%f + $inputb%f = $expected%f")
                println(f"A: ${floatToBigInt(inputa)}%x, B: ${floatToBigInt(inputb)}%x, HW: ${c.io.res.peek().litValue}%x, EMU: ${floatToBigInt(floatAdd(inputa, inputb))}%x")
                c.clock.step(1)
                c.io.res.expect(floatToBigInt(expected).U)
            }
        }
    }
}

class FPAdd64Test extends AnyFreeSpec with Matchers {
    "FPAdd64 should add double-precision floating point numbers correctly" in {
        simulate(new FPAdd64) { c =>
            val inputsQueue = Queue((0.0, 0.0))
            val expectedQueue = Queue(None, Some(0.0)) // prepare the expected FIFO: Front -> (None, Some(0.0)) <- Rear
            // after 3 clock cycles we will have read twice from this, which is when the first result: Some(0.0) is produced since 3 cycles of latency went by

            def randDouble(): Double = {
                scala.util.Random.nextDouble() * 1000000.0 - 500000.0
            }

            c.io.a.poke(doubleToBigInt(0.0).U)
            c.io.b.poke(doubleToBigInt(0.0).U)
            c.clock.step(1)

            for (_ <- 0 until 8) {
                val a = randDouble()
                val b = randDouble()
                inputsQueue.enqueue((a, b))
                expectedQueue.enqueue(Some(doubleAdd(a, b)))

                c.io.a.poke(doubleToBigInt(a).U)
                c.io.b.poke(doubleToBigInt(b).U)
                c.clock.step(1)

                val expectedOption = expectedQueue.dequeue()

                if (expectedOption.nonEmpty) {
                    val expected = expectedOption.get
                    val (inputa, inputb) = inputsQueue.dequeue()
                    println(s"Expecting $inputa + $inputb = $expected")
                    println("AKA %x + %x = %x".format(doubleToBigInt(inputa),
                                                        doubleToBigInt(inputb),
                                                        doubleToBigInt(expected)))
                    c.io.res.expect(doubleToBigInt(expected).U)
                }
            }

            while (expectedQueue.nonEmpty) {
                val expectedOption = expectedQueue.dequeue()
                val expected = expectedOption.get
                val (inputa, inputb) = inputsQueue.dequeue()

                println(s"Expecting $expected or ${doubleToBigInt(expected)}")
                println(s"Expecting $inputa + $inputb = $expected")
                println("AKA %x + %x = %x".format(doubleToBigInt(inputa),
                                                    doubleToBigInt(inputb),
                                                    doubleToBigInt(expected)))
                c.clock.step(1)
                c.io.res.expect(doubleToBigInt(expected).U)
            }
        }
    }
}