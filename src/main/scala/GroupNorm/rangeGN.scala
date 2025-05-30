package GroupNorm
import chisel3._
import chisel3.util._ // needed for MuxLookup and ShiftRegister
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
// import _root_.circt.stage.ChiselStage
import silu.FPMult16ALT
import silu.FPAdd16ALT
import hardfloat.DivSqrtRecFN_small
/*
  * This is a Chisel implementation of the range GroupNorm function.
  * G   =   32 (=number of groups to divide the channel dimension into)
  * C   =   320,640,1280 (=number of channels in the input tensor)
  * N = C / G =   10,20,40 (=number of channels per group) -> defines over how many channels the mean and range are calculated

  * rangedGN(x_i) = (x_i - mean) / ( alpha * (max(x)-min(x)))

  * where mean = sum(x_i) / (C/G), 
  * alpha = 1 / sqrt(2*ln(G)) = (1 / sqrt(2*ln(32)))
  * The implementation only supports BF16 numbers
  * GroupNorm happens on 10,20 or 40 elements at a time, so we need channels to be ready for that after a CONV3 or resadd.
  * total latency = log2(N) * 3 + 1 + 3 + 3 + 13 + 1 = log2(N) * 3 + 21 cc = log2(C/G) * 3 + 21 cc
  * e.g. for C = 320, N = 10, total latency = 3 * ceil(log2(10)) + 1 + 3 + 3 + 11 + 1 = 3 * ceil(3.3219)+19 = 12+19=31 cc
  */
class rangeGN(val C: Int) extends Module {
  val G = 32
  val N = C / G // channels per group
  val io = IO(new Bundle {
    val in_a  = Input(Vec(N, UInt(16.W))) // BF16-encoded inputs
    val out_a = Output(Vec(N, UInt(16.W))) // BF16-encoded outputs

    val debugMeanOut = Output(UInt(16.W)) // for debugging purposes, output the mean
    val debugNumeratorsOut = Output(Vec(N, UInt(16.W))) // for debugging purposes, output the numerators
    val debugRangeOut = Output(UInt(16.W)) // for debugging purposes, output the range
    val debugdivResultsOut = Output(Vec(N, UInt(16.W))) // for debugging purposes, output the division results
  })

  // === Constants ===
  val recip_alpha = "b0_10000000_0101000".U(16.W) // ≈ 1 / (1 / sqrt(2 * ln(G))) ≈ 2.625

  val recip_N = Wire(UInt(16.W))
  recip_N := MuxLookup(C.U, 0.U)(Seq(
    320.U  -> "b0_01111011_1001101".U, // 0.1
    640.U  -> "b0_01111010_1001101".U, // 0.05
    1280.U -> "b0_01111001_1001101".U  // 0.025
  ))

  require(Set(320, 640, 1280).contains(C), "C must be 320, 640, or 1280")

  // === FPAdd16 Reduction Tree === 
  // TODO: calculate max and min already here in parallel!
  def reduceFPAdd(vec: Seq[UInt]): UInt = {
    if (vec.length == 1) vec.head
    else {
      val pairedAdds = vec.grouped(2).map {
        case Seq(a, b) =>
          val add = Module(new FPAdd16ALT) // 3 cc latency per adder
          add.io.a := a
          add.io.b := b
          add.io.res
        case Seq(a) => a
      }.toSeq
      reduceFPAdd(pairedAdds)
    }
  }
  val sum = reduceFPAdd(io.in_a) // sum(x_i) has total latency: log2(N) * 3 cc

  // === mean = sum * (1/N) ===
  val meanMult = Module(new FPMult16ALT) // 1 cc latency
  meanMult.io.a := sum
  meanMult.io.b := recip_N
  val mean = RegNext(meanMult.io.res)

  io.debugMeanOut := mean // for debugging purposes, output the mean

  // === Subtract mean: x_i - mean ===
  val subtractors = Seq.fill(N)(Module(new FPAdd16ALT)) // 3cc latency per subtractor
  val numerators = Wire(Vec(N, UInt(16.W)))

  for (i <- 0 until N) { // N parallel subtractors
    subtractors(i).io.a := io.in_a(i) // N parallel inputs(x_i)
    subtractors(i).io.b := mean ^ (1.U << 15) // flip sign of mean to subtract
    numerators(i) := subtractors(i).io.res
  }
  io.debugNumeratorsOut := numerators // for debugging purposes, output the numerators

  // === Compute max and min (bitwise comparison is okay) ===
  val maxVal = io.in_a.reduce((a, b) => Mux(a > b, a, b)) // latency: ?
  val minVal = io.in_a.reduce((a, b) => Mux(a < b, a, b))

  // === range = max - min ===
  val rangeSub = Module(new FPAdd16ALT) // 3cc latency
  rangeSub.io.a := maxVal
  rangeSub.io.b := minVal ^ (1.U << 15) // flip sign of min to subtract
  // val range = ShiftRegister(rangeSub.io.res, 3) // because rangeSub has 3cc latency
  // val range = ShiftRegister(rangeSub.io.res, 0) 
  // val range = RegNext(rangeSub.io.res) 
  val range = rangeSub.io.res

  io.debugRangeOut := range // for debugging purposes, output the range

  // === Divide each numerator by range === // setting options to round_max = "b011".U(3.W) = 3
  val dividers = Seq.fill(N)(Module(new DivSqrtRecFN_small(8, 8, options = 0))) // 11cc latency max each divider
  val divResults = Wire(Vec(N, UInt(16.W)))
  for (i <- 0 until N) {
      dividers(i).io.inValid := true.B
      dividers(i).io.a       := numerators(i)
      dividers(i).io.b       := range
      dividers(i).io.sqrtOp  := false.B
      dividers(i).io.roundingMode := 0.U // round_nearest_even
      dividers(i).io.detectTininess := 0.U

      divResults(i) := dividers(i).io.out
      // divResults(i) := ShiftRegister(dividers(i).io.out, 0) 
  }
  
    // TODO: should wait for outValid_div to be true to grab output, instead of waiting 11 cycles each time.
    // divResults(i) := ShiftRegister(dividers(i).io.out, 11) 
    // divResults(i) := ShiftRegister(dividers(i).io.out, 0) 
  // }
  io.debugdivResultsOut := divResults // for debugging purposes, output the division results

  // === Multiply by recip_alpha ===
  val finalMuls = Seq.fill(N)(Module(new FPMult16ALT)) // 1cc latency per multiplier
  val result = Wire(Vec(N, UInt(16.W)))

  for (i <- 0 until N) {
    finalMuls(i).io.a := divResults(i)
    finalMuls(i).io.b := recip_alpha
    result(i) := finalMuls(i).io.res
  }
  // === Final Output ===
  io.out_a := result
}


/**
 * Generate Verilog sources and save it in generated/rangeGN.v
 */
// object rangeGN extends App {
//     ChiselStage.emitSystemVerilogFile(
//         new rangeGN,
//         firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
//         args = Array("--target-dir", "generated")
//     )
// }