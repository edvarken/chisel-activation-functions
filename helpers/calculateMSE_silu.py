import math
import numpy as np
import struct
from typing import List

#### the MSE used in the text is calculated using sbt tests, not with this python file ####

def relu6(x):
    return np.maximum(0, np.minimum(6, x))

"""
version1MSE: Calculates the Mean Squared Error (MSE) between the exact SiLU and the approximated SiLU values in the range -6 <= x <= 6
At sample points from -6 to 6 with a step of 0.0625
"""
def version1MSE():
    x = np.arange(-6.0000, 6.0625, 0.0625) # start, stop, step
    exact_silu = x / (1 + np.exp(-x))
    approx_silu = (x * relu6(x+3)) / 6
    errors = exact_silu - approx_silu
    mse = np.mean(np.square(errors))
    return mse


def getSiluTableValues(min, max, step) -> tuple[List[float], List[float]]:
    outX = []
    outY = []
    for j in np.arange(min, max, step): # -3.9375 inclusive, 4.0000 exclusive
        silu_float = round(j/(1+math.exp(-j)), 6)
        # Convert float32 to 4-byte representation (big-endian)
        silu_bytes = struct.pack('>f', np.float32(silu_float))
        # Take the first 2 bytes (most significant bits) for BF16
        silu_bf16_bytes = silu_bytes[:2]
        # Convert to bit string
        silu_bf16_bits = ''.join(f'{byte:08b}' for byte in silu_bf16_bytes)
        # Convert the bf16 back to its float representation
        silu_bf16_int = int(silu_bf16_bits, 2) << 16  # Shift back to 32-bit float position
        silu_bf16_float = struct.unpack('>f', struct.pack('>I', silu_bf16_int))[0]
        outX.append(j)
        outY.append(silu_bf16_float) # append the silu_bf16_float value
    return outX, outY


def version2MSE():
    min = -3.9375  
    max = 4.0000
    step = 0.0625 
    _, piecewiseSiLU_middle = getSiluTableValues(min, max, step) # grab only the second item in the tuple
    piecewiseSiLU_left = [0] * len(np.arange(-6, -3.9375, 0.0625))  # list containing only zeroes for the range -6 to -4 inclusive(step= +0.0625!)
    piecewiseSiLU_right = list(np.arange(4, 6.0625, 0.0625))  # convert NumPy array to a flat list for the range 4 to 6 inclusive
    # concatenate the three lists
    piecewiseSiLU = piecewiseSiLU_left + piecewiseSiLU_middle + piecewiseSiLU_right

    x = np.arange(-6.0000, 6.0625, 0.0625) # start, stop, step
    print(f"amount of sampled points between -6 to 6: {len(x)}")
    exact_silu = x / (1 + np.exp(-x))

    errors = exact_silu - piecewiseSiLU
    mse = np.mean(np.square(errors))
    return mse


def version2MSESetRange(min, max, step, testmin=-10.0000, testmax=10.0000):
    _, piecewiseSiLU_middle = getSiluTableValues(min, max, step) # grab only the second item in the tuple
    piecewiseSiLU_left = [0] * len(np.arange(testmin, min, step))  # list containing only zeroes for the range -6 to -4 inclusive(step= +0.0625!)
    piecewiseSiLU_right = list(np.arange(max, testmax+step, step))  # convert NumPy array to a flat list for the range 4 to 6 inclusive
    # concatenate the three lists
    piecewiseSiLU = piecewiseSiLU_left + piecewiseSiLU_middle + piecewiseSiLU_right

    x = np.arange(testmin, testmax+step, step) # start, stop, step
    print(f"amount of sampled points between -10 and 10: {len(x)}")
    exact_silu = x / (1 + np.exp(-x))

    errors = exact_silu - piecewiseSiLU
    mse = np.mean(np.square(errors))
    return mse


def version2EdgeOfRangeClippingErrors(a):
    exact_silu1 = -a / (1 + math.exp((a))) # edge on the left of origin
    piecewiseSiLU1 = 0
    error1 = exact_silu1 - piecewiseSiLU1
    print(f"error at -{a} if we clip to 0:", error1)
    exact_silu2 = + a / (1 + math.exp(-a)) # edge on the right of origin
    piecewiseSiLU2 = a
    error2 = exact_silu2 - piecewiseSiLU2
    print(f"error at +{a} if we clip to +{a}:", error2)


def sigmoidEdgeOfRangeClippingErrors(a):
    exact_sigmoid1 = 1 / (1 + math.exp(a)) # edge on the left of origin
    piecewise_sigmoid1 = 0
    error1 = exact_sigmoid1 - piecewise_sigmoid1
    print(f"error at -{a} if we clip to 0:", error1)
    exact_sigmoid2 = 1 / (1 + math.exp(-a)) # edge on the right of origin
    piecewise_sigmoid2 = 1
    error2 = exact_sigmoid2 - piecewise_sigmoid2
    print(f"error at +{a} if we clip to +1:", error2)


if __name__ == "__main__":
    mse1 = version1MSE()
    mse2 = version2MSE()
    print(f"Version 1 MSE: {mse1:.6f}")
    print(f"Version 2 MSE: {mse2:.6f}")
    print("======== testmin=-10.0000, testmax=10.0000 ========")
    mse2a = version2MSESetRange(-3.9375, 4.0000, 0.0625, testmin=-10.0000, testmax=10.0000)  # default range
    mse2b = version2MSESetRange(-4.0000 + 0.03125, 4.0000, 0.03125, testmin=-10.0000, testmax=10.0000) # default range + smaller step
    mse2c = version2MSESetRange(-7.9375, 8.0000, 0.0625, testmin=-10.0000, testmax=10.0000)  # larger range
    mse2d = version2MSESetRange(-8.0000 + 0.03125, 8.0000, 0.03125, testmin=-10.0000, testmax=10.0000)  # larger range + smaller step
    print(f"Version 2a MSE: {mse2a:.7f}")
    print(f"Version 2b MSE: {mse2b:.7f}")
    print(f"Version 2c MSE: {mse2c:.7f}")
    print(f"Version 2d MSE: {mse2d:.7f}")
    print("======== testmin=-8.0000, testmax=8.0000 ========")
    mse2a = version2MSESetRange(-3.9375, 4.0000, 0.0625, testmin=-8.0000, testmax=8.0000)  # default range
    mse2b = version2MSESetRange(-4.0000 + 0.03125, 4.0000, 0.03125, testmin=-8.0000, testmax=8.0000) # default range + smaller step
    mse2c = version2MSESetRange(-7.9375, 8.0000, 0.0625, testmin=-8.0000, testmax=8.0000)  # larger range
    mse2d = version2MSESetRange(-8.0000 + 0.03125, 8.0000, 0.03125, testmin=-8.0000, testmax=8.0000)  # larger range + smaller step
    print(f"Version 2a MSE: {mse2a:.7f}")
    print(f"Version 2b MSE: {mse2b:.7f}")
    print(f"Version 2c MSE: {mse2c:.7f}")
    print(f"Version 2d MSE: {mse2d:.7f}")
    print("======== testmin=-15.0000, testmax=15.0000 ========")
    mse2a = version2MSESetRange(-3.9375, 4.0000, 0.0625, testmin=-15.0000, testmax=15.0000)  # default range
    mse2b = version2MSESetRange(-4.0000 + 0.03125, 4.0000, 0.03125, testmin=-15.0000, testmax=15.0000) # default range + smaller step
    mse2c = version2MSESetRange(-7.9375, 8.0000, 0.0625, testmin=-15.0000, testmax=15.0000)  # larger range
    mse2d = version2MSESetRange(-8.0000 + 0.03125, 8.0000, 0.03125, testmin=-15.0000, testmax=15.0000)  # larger range + smaller step
    print(f"Version 2a MSE: {mse2a:.7f}")
    print(f"Version 2b MSE: {mse2b:.7f}")
    print(f"Version 2c MSE: {mse2c:.7f}")
    print(f"Version 2d MSE: {mse2d:.7f}")
    print("======== version 2 edge of range: clipping errors ========")
    version2EdgeOfRangeClippingErrors(1)
    version2EdgeOfRangeClippingErrors(2)
    version2EdgeOfRangeClippingErrors(4)
    version2EdgeOfRangeClippingErrors(8)
    version2EdgeOfRangeClippingErrors(16)
    version2EdgeOfRangeClippingErrors(32)

    print("======== version 3: Sigmoid edge of range: clipping errors ========")
    sigmoidEdgeOfRangeClippingErrors(1)
    sigmoidEdgeOfRangeClippingErrors(2)
    sigmoidEdgeOfRangeClippingErrors(4)
    sigmoidEdgeOfRangeClippingErrors(8)
    sigmoidEdgeOfRangeClippingErrors(16)
    sigmoidEdgeOfRangeClippingErrors(32)

