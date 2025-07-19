import math
import numpy as np
import struct
from typing import List
import torch

#### the MSE used in the text is calculated using sbt tests, not with this python file ####

"""
Calculate the Mean Squared Error (MSE) between the exact GELU and the approximated GELU values in the range -6 <= x <= 6
At samplepoints from -6 to 6 with a step of 0.0625
"""


def getGELUTableValues(min, max, step) -> tuple[List[float], List[float]]:
    outX = []
    outY = []
    for j in np.arange(min, max, step): # -3.9375 inclusive, 4.0000 exclusive
        GELU_float = round(j*0.5*(1+math.erf(j/math.sqrt(2))), 6)
        # Convert float32 to 4-byte representation (big-endian)
        GELU_bytes = struct.pack('>f', np.float32(GELU_float))
        # Take the first 2 bytes (most significant bits) for BF16
        GELU_bf16_bytes = GELU_bytes[:2]
        # Convert to bit string
        GELU_bf16_bits = ''.join(f'{byte:08b}' for byte in GELU_bf16_bytes)
        # Convert the bf16 back to its float representation
        GELU_bf16_int = int(GELU_bf16_bits, 2) << 16  # Shift back to 32-bit float position
        GELU_bf16_float = struct.unpack('>f', struct.pack('>I', GELU_bf16_int))[0]
        outX.append(j)
        outY.append(GELU_bf16_float) # append the GELU_bf16_float value
    return outX, outY


def version2MSE():
    min = -3.9375  
    max = 4.0000
    step = 0.0625 
    _, piecewiseGELU_middle = getGELUTableValues(min, max, step) # grab only the second item in the tuple
    piecewiseGELU_left = [0] * len(np.arange(-6, -3.9375, 0.0625))  # list containing only zeroes for the range -6 to -4 inclusive(step= +0.0625!)
    piecewiseGELU_right = list(np.arange(4, 6.0625, 0.0625))  # GELU(x) = x
    # concatenate the three lists
    piecewiseGELU = piecewiseGELU_left + piecewiseGELU_middle + piecewiseGELU_right

    x = np.arange(-6.0000, 6.0625, 0.0625) # start, stop, step
    print(f"amount of sampled points between -6 to 6: {len(x)}")
    exact_GELU =  torch.nn.functional.gelu(torch.tensor(x, dtype=torch.float32)).numpy()  # Use PyTorch's GELU for exact values

    errors = exact_GELU - piecewiseGELU
    mse = np.mean(np.square(errors))
    return mse

def version2MSESetRange(min, max, step, testmin=-10.0000, testmax=10.0000):
    _, piecewiseGELU_middle = getGELUTableValues(min, max, step) # grab only the second item in the tuple
    piecewiseGELU_left = [0] * len(np.arange(testmin, min, step))  # list containing only zeroes for the range -6 to -4 inclusive(step= +0.0625!)
    piecewiseGELU_right = list(np.arange(max, testmax+step, step))  # GELU(x) = x
    # concatenate the three lists
    piecewiseGELU = piecewiseGELU_left + piecewiseGELU_middle + piecewiseGELU_right

    x = np.arange(testmin, testmax+step, step) # start, stop, step
    print(f"amount of sampled points between -10 and 10: {len(x)}")
    exact_GELU =  torch.nn.functional.gelu(torch.tensor(x, dtype=torch.float32)).numpy()  # Use PyTorch's GELU for exact values

    errors = exact_GELU - piecewiseGELU
    mse = np.mean(np.square(errors))
    return mse


if __name__ == "__main__":
    mse2 = version2MSE()
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
    mse2bb = version2MSESetRange(-4.0000 + 0.015625 , 4.0000, 0.015625 , testmin=-10.0000, testmax=10.0000) # default range + even smaller step
    print(f"Version 2bb MSE: {mse2bb:.7f}") # no additional accuracy benefit, since limited by the 7-bit mantissa
    mse2bbb = version2MSESetRange(-4.0000 + 0.0078125 , 4.0000, 0.0078125 , testmin=-10.0000, testmax=10.0000) # default range + even supersmaller step
    print(f"Version 2bbb MSE: {mse2bbb:.7f}") # no additional accuracy benefit, since limited by the 7-bit mantissa