package silu
import chisel3._
import chisel3.util._ // needed for Cat()

class MantissaRounder(val n: Int) extends Module {
    val io = IO(new Bundle {
        val in = Input(UInt(n.W))
        val out = Output(UInt((n - 1).W))
        val carry = Output(Bool())
    })
    // if all bits of in are 1, set carry to true, rounder.io.out will be all zeroes which is correct.
    io.carry := io.in.andR // if all bits are 1, carry is true
    io.out := io.in(n - 1, 1) + io.in(0) // overflow if: io.out = 1111111(7bits) + 1
}

class FPMult(val n: Int) extends Module { // single-cycle pipeline latency
    val io = IO(new Bundle {
        val a = Input(Bits(n.W))
        val b = Input(Bits(n.W))
        val res = Output(Bits(n.W))
    })

    val a_wrap = new FloatWrapper(io.a) // (FloatWrapper prepends implicit 1 to mantissa!)
    val b_wrap = new FloatWrapper(io.b)

    val stage1_sign = a_wrap.sign.asInstanceOf[UInt] ^ b_wrap.sign.asInstanceOf[UInt]
    val stage1_exponent = a_wrap.exponent.asInstanceOf[UInt] + b_wrap.exponent.asInstanceOf[UInt]
    val stage1_mantissa = a_wrap.mantissa.asInstanceOf[UInt] * b_wrap.mantissa.asInstanceOf[UInt] // chisel makes multiplier in verilog from this
    val stage1_zero = a_wrap.zero.asInstanceOf[Bool] || b_wrap.zero.asInstanceOf[Bool]

    val sign_reg = RegNext(stage1_sign)
    val exponent_reg = RegNext(stage1_exponent)
    val mantissa_reg = RegNext(stage1_mantissa)
    val zero_reg = RegNext(stage1_zero)

    val stage2_sign = sign_reg
    // val stage2_exponent = UInt((a_wrap.exponent.getWidth).W) // should add Wire() since we only declare it without driving it yet, see correction in line below
    val stage2_exponent = Wire(UInt((a_wrap.exponent.asInstanceOf[UInt].getWidth).W)) // getWidth comes from FloatWrapper, but is in same package
    val stage3_exponent = Wire(UInt((a_wrap.exponent.asInstanceOf[UInt].getWidth).W))
    val stage2_mantissa = Wire(UInt((a_wrap.mantissa.asInstanceOf[UInt].getWidth - 1).W))

    val (mantissaLead, mantissaSize, exponentSize, exponentSub) = n match {
        case 16 => (15, 7, 8, 127) // BF16, mantissaLead = 15 = 2*7 + 1
        case 32 => (47, 23, 8, 127) // mantissaLead = 2*mantissaSize + 1
        case 64 => (105, 52, 11, 1023) // mantissaLead = 2*mantissaSize + 1
    }

    val rounder = Module(new MantissaRounder(mantissaSize + 1)) // receives 8bits as input

    when (zero_reg) { // if either input is zero, output is zero
        stage2_exponent := 0.U(exponentSize.W)
        rounder.io.in := 0.U((mantissaSize + 1).W)

    } .elsewhen (mantissa_reg(mantissaLead) === 1.U) { // if the product of prepended1_mantissa_a * prepended1_mantissa_b has a 1 bit at the front, we need to add 1 to exponent to normalize mantissa
        stage2_exponent := exponent_reg - (exponentSub - 1).U // If you increment the exponent due to overflow, 
        // you should also shift the mantissa right by one to normalize it. This is done here(the lsbit starts more to the left, and msbit ends more to the left as well)
        rounder.io.in := mantissa_reg(mantissaLead - 1, mantissaLead - mantissaSize - 1)

    } .otherwise { 
        stage2_exponent := exponent_reg - exponentSub.U
        rounder.io.in := mantissa_reg(mantissaLead - 2, mantissaLead - mantissaSize - 2)
    }
    stage2_mantissa := rounder.io.out
    // If mantissa rounding causes overflow, increment the exponent to normalize
    stage3_exponent := Mux(rounder.io.carry === true.B, stage2_exponent + 1.U, stage2_exponent)

    io.res := Cat(stage2_sign.asUInt,
                  stage3_exponent.asUInt,
                  stage2_mantissa.asUInt)
}

class FPMult16ALT extends FPMult(16) {} // BF16
class FPMult32 extends FPMult(32) {}
class FPMult64 extends FPMult(64) {}

