# BF16 Silu approximation Chisel Module
## Two variations to approximate SiLU

### 1. silu.scala 
silu.scala approximates the SiLU(x) function as `x * ReLU6(x+3) / 6` using a pipelined Adder with 3 cycles latency, and two Multipliers each with 1 cycle latency, totaling 5 cycles latency for the SiLU approximation.
(The Adder and Multiplier modules support BF16, floating point and double numbers. The SiLU module supports only BF16 numbers)
### 2. siluUsingLUT.scala
siluUsingLUT.scala uses a piecewise function where 
- SiLU(x) = 0  for x <= -4
- SiLU(x) = one of the 128 entries in the lookup-table  for -4 < x < 4
- SiLU(x) = x itself  for x >= 4

siluUsingLUT.scala has 1 cycle latency for the SiLU approximation


## directory tree
```
├── README.md
├── build.sbt
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

Use `sbt test` to run all tests. Running only the test for silu.scala can be done with `sbt 'testOnly silu.siluTest'`
Running the test for siluUsingLUT.scala can be done with `sbt 'testOnly silu.siluUsingLUTTest'`
