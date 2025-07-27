package toplevel
import chisel3._
import chisel3.util._ // needed for Cat()
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage // needed for ChiselStage.emitSystemVerilogFile

class Demux1toN(val n: Int, val width: Int) extends Module {
  require(isPow2(n), "Demux size must be a power of 2")
  val selWidth = log2Ceil(n)
  val io = IO(new Bundle {
    val in  = Input(UInt(width.W))
    val sel = Input(UInt(selWidth.W))
    val out = Output(Vec(n, UInt(width.W)))
  })

  io.out := VecInit(Seq.fill(n)(0.U))
  io.out(io.sel) := io.in
}

class Mux2to1(val width: Int) extends Module {
    val io = IO(new Bundle {
        val sel = Input(UInt(1.W))
        val in0 = Input(UInt(width.W))
        val in1 = Input(UInt(width.W))
        val out = Output(UInt(width.W))
  })
  io.out := Mux(io.sel.asBool, io.in1, io.in0) // if sel is 0: output in0; if sel is 1, output in1
}

class toplevel(val spatial_array_size: Int = 16, val DyTintBits: Int = 2, val DyTfracBits: Int = 6, val C: Int = 320) extends Module {
    val io = IO(new Bundle {
        val in_a     = Input(Vec(spatial_array_size, UInt(16.W))) // comes from the Scratchpad
        val in_alpha = Input(Vec(spatial_array_size, UInt(16.W))) // alpha used for DyT to approximate LayerNorm
        val sel0     = Input(Bits(2.W)) // select GN+act(00), GN(01), act(10), or bypass(11)
        val sel1     = Input(Bits(2.W)) // select SiLU(00), GELU(01), DyT(10), or don't care(11)
        val out_a    = Output(Vec(spatial_array_size, UInt(16.W))) // goes to the systolic array
    })


    // streamed for 16 elements in parallel:

    // first demux choosing between GN or not
    val demux0 = Seq.fill(spatial_array_size)(Module(new Demux1toN(2, 16))) // create 16 1-to-2 demuxes for each input in parallel

    val rangeGN = Module(new GroupNorm.rangeGN(C)) // 1 rangeGN module taking in 10 inputs at a time when C is 320

    val mux0 = Seq.fill(spatial_array_size)(Module(new Mux2to1(16))) // 16 Muxes to select between GN and bypass
    val demux1 = Seq.fill(spatial_array_size)(Module(new Demux1toN(2, 16))) // create 16 1-to-2 demuxes
    val demux2 = Seq.fill(spatial_array_size)(Module(new Demux1toN(2, 16))) // create 16 1-to-2 demuxes

    val siluandgeluPWLSigmoid20NonUniformSegments = Seq.fill(spatial_array_size)(Module(new silu.siluandgeluPWLSigmoid20NonUniformSegments)) // 16 SiLU/GELU parallel modules
    val dyT = Seq.fill(spatial_array_size)(Module(new DyT.DyTUsingLUT(DyTintBits, DyTfracBits))) // 16 DyT modules in parallel

    val mux1 = Seq.fill(spatial_array_size)(Module(new Mux2to1(16))) // 16 Muxes to select between SiLU, GELU, DyT, or don't care
    val mux2 = Seq.fill(spatial_array_size)(Module(new Mux2to1(16))) // 16 Muxes to select final output

    // Connect each input to its corresponding demux, control demux output by sel0(0), etc...
    for (i <- 0 until spatial_array_size) {
        demux0(i).io.in := io.in_a(i)
        demux0(i).io.sel := io.sel0(0) // 0: route to GN, 1: route to bypass/mux
        
        mux0(i).io.in1 := demux0(i).io.out(1) // connect demux(0) to bypass=mux path
        mux0(i).io.sel := io.sel0(0) // 0: GN path, 1: bypass path

        demux1(i).io.in := mux0(i).io.out
        demux1(i).io.sel := io.sel0(1)

        demux2(i).io.sel := io.sel1(0) // 0: SiLU/GELU, 1: DyT/don't care
        demux2(i).io.in := demux1(i).io.out(0) // connect the second demux to the third demux, which has 4 outputs instead of 2
        

        siluandgeluPWLSigmoid20NonUniformSegments(i).io.in_select := io.sel1(1) // 0: SiLU, 1: GELU
        siluandgeluPWLSigmoid20NonUniformSegments(i).io.in_a := demux2(i).io.out(0) // connect the SiLU/GELU demux2 path to the SiLU/GELU module
        dyT(i).io.in_a := demux2(i).io.out(1) // connect the DyT demux2 path to the DyT module
        dyT(i).io.in_alpha := io.in_alpha(i) // connect the alphas to DyT

        mux1(i).io.in0 := siluandgeluPWLSigmoid20NonUniformSegments(i).io.out_a // SiLU/GELU output
        mux1(i).io.in1 := dyT(i).io.out_a // DyT output
        mux1(i).io.sel := io.sel1(0)

        mux2(i).io.in0 := mux1(i).io.out
        mux2(i).io.sel := io.sel1(1) // 0: SiLU/GELU, 1: DyT
        mux2(i).io.in1 := demux1(i).io.out(1) // bypass path
        io.out_a(i) := mux2(i).io.out
    }

    for (i <- 0 until C/32) { // m = 10, just use 10 of the 16 streamed inputs to go to rangeGN
        rangeGN.io.in_a(i) := demux0(i).io.out(0) // connect the first 10 demux(0) outputs to 10 rangeGN inputs
        mux0(i).io.in0 := rangeGN.io.out_a(i) 
    }
    // rangeGN 10 outputs to 10 Mux0.in0 inputs, what about the 6 non-connected Mux0.in0 inputs?
    // connect the 6 remaining Mux0.in0 inputs to 0.U for now, since C=320 and we only use 10 inputs
    for (i <- C/32 until spatial_array_size) {
        mux0(i).io.in0 := 0.U
    }
}

/**
 * Generate Verilog sources and save it in generated2/toplevel.sv
 * Uncomment to generate the SystemVerilog file when using 'sbt run'
 */
object toplevelMain extends App {
    ChiselStage.emitSystemVerilogFile(
        new toplevel(spatial_array_size = 16, DyTintBits = 2, DyTfracBits = 5, C = 320),
        firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "--lowering-options=" + List(
        "disallowLocalVariables", "disallowPackedArrays", "locationInfoStyle=wrapInAtSquareBracket").reduce(_ + "," + _)),
        args = Array("--target-dir", "generated2")
    )
}