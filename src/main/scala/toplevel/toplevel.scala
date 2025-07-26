// package toplevel
// import chisel3._
// import chisel3.util._ // needed for Cat()
// // _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
// import _root_.circt.stage.ChiselStage // needed for ChiselStage.emitSystemVerilogFile

// // rangeGN not included in this toplevel for element-wise activation functions, since it needs multiple inputs at once

// class toplevel(val PWL_segments: Int = 20, val DyTintBits: Int = 2, val DyTfracBits: Int = 4) extends Module {
//     val io = IO(new Bundle {
//         val in_a = Input(Bits(16.W)) // define as raw Bits collection, but represents BF16
//         val select_act = Input(Bits(2.W)) // select which non-linear function to use: 0 for SiLU, 1 for GELU, 2 for DyT (LayerNorm)
//         val out_a = Output(Bits(16.W))
//     })

//     // Instantiate the DyTUsingLUT module
//     val dyTModule = Module(new DyT.DyTUsingLUT(DyTintBits, DyTfracBits))
//     dyTModule.io.in_a := io.in_a
//     dyTModule.io.in_alpha := io.in_alpha
//     io.out_a := dyTModule.io.out_a

//     val siluandgeluPWLSigmoid20NonUniformSegments = Module(new silu.siluandgeluPWLSigmoid20NonUniformSegments(PWL_segments))
//     siluandgeluPWLSigmoid20NonUniformSegments.io.in_a := io.in_a
//     siluandgeluPWLSigmoid20NonUniformSegments.io.select_act := io.select_act

//     io.out_a := Mux(io.select_act === 0.U || io.select_act === 1.U,
//      siluandgeluPWLSigmoid20NonUniformSegments.io.out_a, dyTModule.io.out_a) // Silu/GELU for select_act = 0/1, DyT output if select_act = 2
// }

// /**
//  * Generate Verilog sources and save it in generated/toplevel.sv
//  * Uncomment to generate the SystemVerilog file when using 'sbt run'
//  */
// object toplevelMain extends App {
//     ChiselStage.emitSystemVerilogFile(
//         new toplevel(PWL_segments = 20, DyTintBits = 2, DyTfracBits = 4), 
//         firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "--lowering-options=" + List(
//         "disallowLocalVariables", "disallowPackedArrays", "locationInfoStyle=wrapInAtSquareBracket").reduce(_ + "," + _)),
//         args = Array("--target-dir", "generated")
//     )
// }