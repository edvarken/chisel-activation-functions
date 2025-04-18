package silu
import chisel3._
import chisel3.util._ // needed for Cat()

class MantissaRounder(val n: Int) extends Module {
    val io = IO(new Bundle {
        val in = Input(UInt(n.W))
        val out = Output(UInt((n - 1).W))
    })

    io.out := io.in(n - 1, 1) + io.in(0)
}

class FPMult(val n: Int) extends Module { // single-cycle pipeline latency
    val io = IO(new Bundle {
        val a = Input(Bits(n.W))
        val b = Input(Bits(n.W))
        val res = Output(Bits(n.W))
    })

    // require(Set(16, 32, 64).contains(n), "Only bfloat16, fp32, fp64 supported")require(Set(16, 32, 64).contains(n), "Only bfloat16, fp32, fp64 supported")

    val a_wrap = new FloatWrapper(io.a)
    val b_wrap = new FloatWrapper(io.b)

    val stage1_sign = a_wrap.sign.asInstanceOf[UInt] ^ b_wrap.sign.asInstanceOf[UInt]
    val stage1_exponent = a_wrap.exponent.asInstanceOf[UInt] + b_wrap.exponent.asInstanceOf[UInt]
    val stage1_mantissa = a_wrap.mantissa.asInstanceOf[UInt] * b_wrap.mantissa.asInstanceOf[UInt]
    val stage1_zero = a_wrap.zero.asInstanceOf[Bool] || b_wrap.zero.asInstanceOf[Bool]

    val sign_reg = RegNext(stage1_sign)
    val exponent_reg = RegNext(stage1_exponent)
    val mantissa_reg = RegNext(stage1_mantissa)
    val zero_reg = RegNext(stage1_zero)

    val stage2_sign = sign_reg
    // val stage2_exponent = UInt((a_wrap.exponent.getWidth).W) // should add Wire() since we only declare it without driving it yet, see correction below
    val stage2_exponent = Wire(UInt((a_wrap.exponent.asInstanceOf[UInt].getWidth).W)) // getWidth comes from FloatWrapper, but is in same package
    val stage2_mantissa = Wire(UInt((a_wrap.mantissa.asInstanceOf[UInt].getWidth - 1).W))

    val (mantissaLead, mantissaSize, exponentSize, exponentSub) = n match {
        case 16 => (15, 7, 8, 127) // BF16, mantissaLead = 15?
        case 32 => (47, 23, 8, 127) // mantissaLead = 2*mantissaSize + 1
        case 64 => (105, 52, 11, 1023) // mantissaLead = 2*mantissaSize + 1
    }

    val rounder = Module(new MantissaRounder(mantissaSize + 1))

    when (zero_reg) {
        stage2_exponent := 0.U(exponentSize.W)
        rounder.io.in := 0.U((mantissaSize + 1).W)
    } .elsewhen (mantissa_reg(mantissaLead) === 1.U) {
        stage2_exponent := exponent_reg - (exponentSub - 1).U
        rounder.io.in := mantissa_reg(mantissaLead - 1,
                                      mantissaLead - mantissaSize - 1)
    } .otherwise {
        stage2_exponent := exponent_reg - exponentSub.U
        rounder.io.in := mantissa_reg(mantissaLead - 2,
                                      mantissaLead - mantissaSize - 2)
    }

    stage2_mantissa := rounder.io.out

    io.res := Cat(stage2_sign.asUInt,
                  stage2_exponent.asUInt,
                  stage2_mantissa.asUInt)
}

class FPMult16 extends FPMult(16) {} // BF16
class FPMult32 extends FPMult(32) {}
class FPMult64 extends FPMult(64) {}

