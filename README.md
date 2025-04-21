# SiLU hardware function
Analytically: `SiLU(x) = x / (1+exp(-x))`

In hardware however, this function needs to be approximated.

This repository contains two different hardware implementations of the Sigmoid-Linear-Unit(SiLU) activation function for BrainFloat16(BF16) inputs.
It makes use of the Chisel3 framework to describe, test and generate the hardware.

### Version 1
Version 1 is described in `src/main/scala/silu/silu.scala` and approximates the SiLU(x) function as `x * ReLU6(x+3) / 6`.
For this an Adder and two Multipliers are needed. The Adder is pipelined and has 3 cycles latency, the two Multipliers each have 1 cycle latency, totaling 5 cycles latency for the SiLU approximation.

(The Adder and Multiplier modules support BF16, floating point and double numbers. The SiLU module supports only BF16 numbers)

### Version 2
Version 2 is described in `src/main/scala/silu/siluUsingLUT.scala` and uses a piecewise function to approximate SiLU(x) 
- SiLU(x) = 0  for x <= -4
- SiLU(x) = one of the 128 entries in a lookup-table  for -4 < x < 4
- SiLU(x) = x itself  for x >= 4

siluUsingLUT.scala has only 1 cycle latency for the SiLU approximation

## Chisel3 tests
Use `sbt test` to run all chisel3 tests. Running only the test for silu.scala can be done with `sbt 'testOnly silu.siluTest'`
Running the test for siluUsingLUT.scala can be done with `sbt 'testOnly silu.siluUsingLUTTest'`

## Generate SystemVerilog RTL files
Use `sbt run` to generate all the systemverilog files (files ending on .sv). All files are saved into the directory generated/

## Comparing the two versions
### Version 1: silu.scala
- Accuracy:
- Area:
- Power:
- Latency:
### Version 2: siluUsingLUT.scala
- Accuracy:
- Area:
- Power:
- Latency:

## directory tree
```
.
├── LICENSE
├── README.md
├── build.sbt
├── helpers
│   ├── generateSiluLUT.py
│   └── indices_silubf16.txt
├── project
│   └─ build.properties
└── src
    ├── main
    │   └── scala
    │        └── silu
    |            ├── BF16toFP.scala
    │            ├── FPAdd.scala
    │            ├── FPMult.scala
    │            ├── FloatUtils.scala
    │            ├── FloatWrapper.scala
    │            ├── relu6.scala
    │            ├── silu.scala
    |            ├── siluLUT.scala
    |            └── siluUsingLUT.scala
    └── test
        └── scala
            └── silu
                ├── BF16toFPTest.scala
                ├── FPAddTest.scala
                ├── FPMultTest.scala
                ├── relu6Test.scala
                ├── siluLUTTest.scala
                ├── siluTest.scala
                └── siluUsingLUTTest.scala
```