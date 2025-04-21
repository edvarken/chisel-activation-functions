# BF16 Silu approximation Scala Chisel3 Module
SiLU(x) can be approximated as x * ReLU6(x+3) / 6

This repository contains a pipelined Adder with 3 cycles latency, a Multiplier with single cycle latency, and SiLU approximation module with 5 cycles latency.

The Adder and Multiplier modules support BF16, floating point and double numbers. The SiLU module supports only BF16 numbers.

```
├── README.md
├── build.sbt
├── project
│   └─ build.properties
└── src
    ├── main
    │   └── scala
    │        └── silu
    │            ├── FPAdd.scala
    │            ├── FPMult.scala
    │            ├── FloatUtils.scala
    │            ├── FloatWrapper.scala
    │            ├── relu6.scala
    │            └── silu.scala
    └── test
        └── scala
            └── silu
                ├── FPAddTest.scala
                ├── FPMultTest.scala
                ├── relu6Test.scala
                └── siluTest.scala
```

Use `sbt test` to run all tests. Running only the test for SiLU can be done with `sbt 'testOnly silu.siluTest'`