package silu
import chisel3._
import chisel3.util._ // needed for Cat()

// Bundles that represent the raw bits of custom datatypes
// Wraps a Chisel BF16 or Flo or Dbl datatype to allow easy
// extraction of the different parts (sign, exponent, mantissa)

class FloatWrapper(val num: Bits) {
    val (sign, exponent, mantissa, zero) = num.getWidth match {
        case 16 => ((num(15)).asBool, // BF16
                    num(14, 7),
                    // if the exponent is 0 it's a denormalized number(between 0 and 2^-127), prepend 0
                    // else(most of the time) prepend the implicit 1 to the mantissa
                    Cat(Mux(num(14, 7) === 0.U(8.W),
                            0.U(1.W), 1.U(1.W)),
                        num(6, 0)),
                    (num(14, 0) === 0.U(15.W)))

        case 32 => (num(31).asBool,
                    num(30, 23), // 8bit exponent
                    // if the exponent is 0
                    // this is a denormalized number
                    Cat(Mux(num(30, 23) === 0.U(8.W),
                            0.U(1.W), 1.U(1.W)),
                        num(22, 0)),
                    (num(30, 0) === 0.U(31.W)))
        case 64 => (num(63).asBool,
                    num(62, 52), // 11bit exponent
                    Cat(Mux(num(62, 52) === 0.U(11.W),
                            0.U(1.W), 1.U(1.W)),
                        num(51, 0)),
                    num(62, 0) === 0.U(63.W))
    }
}