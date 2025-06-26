# Chisel activation functions
This repository contains hardware descriptions for activation and normalization functions used in Diffusion Models. The following functions are implemented in this repository: SiLU(Sigmoid-Linear-Unit), GELU, GroupNorm, and a replacement for LayerNorm called Dynamic Tanh.
Analytically: `SiLU(x) = x / (1+exp(-x))`, `GELU(x) = x*0.5*[1+erf(x/sqrt(2))]`, `LayerNorm(x) = (x_i - mean)/(sqrt(variance))` and `GroupNorm(x_i) = (x_i - mean)/(sqrt(variance))`. The mean and variance here are calculated along all channels for layernorm, while for GroupNorm the mean and variance are only calculated along the channels per group.
In hardware however, these functions need to be approximated.
The SiLU is approximated in two different ways. In a first version SiLU is approximated as `SiLU1(x) = x * ReLU6(x+3) / 6`, while the second version is implemented as a lookup-table(LUT). The GELU function is only approximated as a lookup-table.
The LayerNorm is approximated using a Dynamic Tanh: `DyT(x) = tanh(α*x)`, which is implemented as a lookup-table.
For GroupNorm, we use the range GroupNorm approximation: `rangeGN(x_i) = (x_i - mean)/(alpha*range)`.
All inputs and outputs are in BrainFloat16(BF16) format.

The Chisel3 framework is used to describe, test and generate all hardware.
## SiLU 
### Visualization of SiLU and the two approximative versions
![SiluFunctionandApproximations](img/SiLUand2ApproxFunctions.png)
### Version 1
Version 1 is described in `src/main/scala/silu/silu.scala` and approximates the SiLU(x) function as `SiLU1(x) = x * ReLU6(x+3) / 6`.
For this an Adder and two Multipliers are needed. The Adder is pipelined and has 3 cycles latency, the two Multipliers each have 1 cycle latency, totaling 5 cycles latency for the SiLU approximation. The module can be pipelined however, to hide this latency.
(The Adder and Multiplier modules support BF16, floating point and double numbers. The SiLU module itself only supports BF16 numbers)

### Version 2
Version 2 is described in `src/main/scala/silu/siluUsingLUT.scala` and uses a piecewise function to approximate the SiLU function. Multiple flavours of this version exist however. The range where the lookup-table is used can be set to (-4, 4) or (-8, 8). The amount of entries in the lookup-table is configurable, where more entries correspond to more samplepoints per range, leading to a more detailed approximation. Entries in the lookup-table are chosen using an index. This index consists of 1 signBit concatenated with intBits and fracBits. The four flavours are listed below from least to most LUT entries.

| Function  | LUT Range      | LUT Entries | LUT index: (signBit; #intBits; #fracBits) |
|-----------|----------------|-------------|-------------------------------------------|
| SiLU2a(x) | -4 < x < 4     | 128         | (signbit; 2; 4)                           |
| SiLU2b(x) | -4 < x < 4     | 256         | (signbit; 2; 5)                           |
| SiLU2c(x) | -8 < x < 8     | 256         | (signbit; 3; 4)                           |
| SiLU2d(x) | -8 < x < 8     | 512         | (signbit; 3; 5)                           |

For x values below the LUT range the output is `SiLU2(x)=0`, for x values above the LUT range the output equals the input `SiLU2(x)=x`.

`siluUsingLUT.scala` has only 1 cycle latency for the SiLU approximation.

### Comparing the SiLU versions
For all versions a clock period of 5ns=5000ps is used, corresponding to a 200MHz frequency. Synthesized in TSMC 65nm, the wiring net area is neglected.
The mean squared error(MSE) is calculated using linearly spaced sample points in the range -10 to 10. It shows how well the approximation fits the exact SiLU function, where a lower MSE is better.

| Function  | MSE            | Cells | Area (um^2)      | Power (mW)   | Critical path delay (ps) | Scaled area 65nm->22nm (factor x0.1) (um^2) |
|-----------|----------------|-------|------------------|--------------|--------------------------|---------------------------------------------|
| SiLU1(x)  | 0.004861       | 629   | 1755.04          | 0.633941     | 2263                     | 175.50                                      |
| SiLU2a(x) | 0.0003603      | 358   | 599.48           | 0.114480     | 1002                     | 59.95                                       | 
| SiLU2b(x) | 0.0003519      | 582   | 924.00           | 0.133114     | 1214                     | 92.40                                       | 
| SiLU2c(x) | 0.0000949      | 579   | 908.040          | 0.132313     | 1048                     | 90.80                                       | 
| SiLU2d(x) | 0.0000938      | 979   | 1472.520         | 0.171142     | 1137                     | 147.25                                      | 

### Fitting the SiLU hardware module into the Gemmini accelerator platform
This SiLU unit must fit into the Gemmini accelerator platform. This means the SiLU approximation unit must be parallelized just like the systolic array in Gemmini. This means that for a systolic array of size 16 by 16, we place 16 SiLU hardware units in parallel, to be able to apply 16 activation functions on 16 inputs in parallel. This ensures only single-cycle latencies are perceived when using the SiLU hardware units. Working with a 200MHz 16by16 systolic array configuration of Gemmini, 16 SiLU units are placed in parallel at the input of the scratchpad SRAM. This way the inputs can be activated with the SiLU function, before going into the systolic array. This puts a factor 16x on the area and power usage.

## Dynamic Tanh 
### Visualization of Dynamic Tanh and the approximative version
![DyTandApproximation](img/DyTandApproximation.png)
### Approximative function for dynamic tanh
The approximative function is described in `src/main/scala/silu/DyTUsingLUT.scala` and uses a piecewise function to approximate the DyT function.
- DyT(α*x) = -1  for x <= -4
- DyT(α*x) = one of the 128 entries in a lookup-table  for -4 < x < 4
- DyT(α*x) = +1  for x >= 4

DyTUsingLUT.scala has 3 cycles latency for the DyT approximation, but can work in a pipelined manner.

### Multiple Dynamic Tanh versions
Multiple flavours of this version exist however. The range where the lookup-table is used can be set to (-4, 4) or (-8, 8). The amount of entries in the lookup-table is configurable, where more entries correspond to more samplepoints per range, leading to a more detailed approximation. Entries in the lookup-table are chosen using an index. This index consists of 1 signBit concatenated with intBits and fracBits. The four flavours are listed below from least to most LUT entries.

| Function  | LUT Range      | LUT Entries | LUT index: (signBit; #intBits; #fracBits) |
|-----------|----------------|-------------|-------------------------------------------|
| DyT1(x)   | -4 < x < 4     | 128         | (signbit; 2; 4)                           |
| DyT2(x)   | -4 < x < 4     | 256         | (signbit; 2; 5)                           |
| DyT3(x)   | -8 < x < 8     | 256         | (signbit; 3; 4)                           |
| DyT4(x)   | -8 < x < 8     | 512         | (signbit; 3; 5)                           |

### Comparing the Dynamic Tanh versions
For all versions a clock period of 5ns=5000ps is used, corresponding to a 200MHz frequency. Synthesized in TSMC 65nm, the wiring net area is neglected.
The mean squared error(MSE) of every approximation versus the exact dynamic tanh function is calculated using linearly spaced sample points in the range -10 to 10. It shows how well the approximation fits the exact dynamic tanh function, where a lower MSE is better.

| Function  | MSE            | Cells | Area (um^2)      | Power (mW)   | Critical path delay (ps) | Scaled area 65nm->22nm (factor x0.1) (um^2) |
|-----------|----------------|-------|------------------|--------------|--------------------------|---------------------------------------------|
| Dyt1(x)   | 0.0000020      | 428   | 1069.60          | 0.355952     | 1112                     | 106.96                                      |
| DyT2(x)   | 0.0000018      | 497   | 1172.08          | 0.387214     | 1112                     | 117.21                                      | 
| DyT3(x)   | 0.0000073      | 464   | 1120.00          | 0.385373     | 1114                     | 112.00                                      | 
| DyT4(x)   | 0.0000071      | 523   | 1212.96          | 0.404407     | 1112                     | 121.30                                      | 

### Fitting the Dynamic Tanh hardware module into the Gemmini accelerator platform
The dynamic hyperbolic tangent function replaces the LayerNorm normalization. Instead of requiring multiple passes across the channel dimension, a single application of the dynamic tanh activation is used. Each spatial element (height and width) has its own alpha factor, which is shared across the channels, approximating the LayerNorm. Since the module takes both alpha and the tensor's element as inputs, we can just place 16 DyT modules in parallel to match the systolic array bandwidth.


## range GroupNorm
![GroupNorm](img/GroupNorm.png)
### Approximative function for GroupNorm: rangeGN
The approximative function is described in `src/main/scala/GroupNorm/rangeGN.scala` and uses a simplified calculation to approximate the GroupNorm normalization function. `rangeGN(x_i) = (x_i - mean)/(alpha*range)`
with `alpha = 1/sqrt(2*ln(C/G))`, `mean = (1/(C/G)) *sum(x_k)` and `range = max() - min()`

The latency of rangeGN.scala depends on the number of elements per group. The Diffusion Model uses 32 groups(G) and the number of channels(C) can be 320, 640 or 1280. This means there are respectively 10, 20 or 40 elements per group. The amount of elements per group impacts the adder tree's depth for the mean calculation. The total latency ranges from 29 clock cycles to 31 to 39 respectively.
This implementation of the range GroupNorm approximation cannot work in a pipelined manner.

### rangeGN results
A clock period of 5ns=5000ps is used, corresponding to a 200MHz frequency. Synthesized in TSMC 65nm, the wiring net area is neglected.
The mean squared error(MSE) of range GroupNorm versus the classic GroupNorm is calculated using linearly spaced sample points in the range -10 to 10. It shows how well the approximation fits the classic GroupNorm, where a lower MSE is better.

| Function        | UNET level | m  | Cells | Area (um^2)  | Power (mW) | critical path delay (ps) | Scaled area 65nm->22nm (factor x0.1) (um^2) |
|-----------------|------------|----|-------|--------------|------------|--------------------------|---------------------------------------------|
| rangeGN320C(x)  | 0          | 10 | 14191 | 32875.92     | 14.9033    | 4792                     | 3287.59                                     |                
| rangeGN640C(x)  | 1          | 20 | 31053 | 70198.24     | 32.7568    | 4792                     | 7019.82                                     |
| rangeGN1280C(x) | 2&3        | 40 | 68824 | 152923.96    | 69.2825    | 5519                     | 15292.40                                    |


## Chisel3 tests
Use `sbt test` to run all chisel3 tests. Running only the test for silu.scala can be done with `sbt 'testOnly silu.siluTest'`
Running only the test for siluUsingLUT.scala can be done with `sbt 'testOnly silu.siluUsingLUTTest'`
Running only the test for DyTUsingLUT.scala can be done with `sbt 'testOnly DyT.DyTUsingLUTTest'`
Use `sbt "testOnly hardfloat.DivSqrtRecFN_smallTest"` to run the test for the 16bit brainfloat division.
Use `sbt "testOnly GroupNorm.rangeGNTest"` to run the test for the range GroupNorm.

## Generate SystemVerilog RTL files
Use `sbt run` to generate systemverilog files (files ending on .sv). Terminal will ask you what file is toplevel. Generated files are saved into `generated/`

### Acknowledgements
Credits to https://github.com/zhemao/chisel-float/ for the floating point multiplier and adder. See LICENSE
Credits to https://github.com/ucb-bar/berkeley-hardfloat/ for the floating point divider unit. See LICENSE
