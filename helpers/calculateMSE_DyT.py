import math
import numpy as np
import struct
from typing import List


"""
Calculate the Mean Squared Error (MSE) between the exact tanh() and the approximated DyT values in the range -6 <= x <= 6
At samplepoints from -6 to 6 with a step of 0.0625
"""


def getDyTTableValues(min, max, step) -> tuple[List[float], List[float]]:
    outX = []
    outY = []
    for j in np.arange(min, max, step): # -3.9375 inclusive, 4.0000 exclusive
        DyT_float = round(math.tanh(j), 6)
        # Convert float32 to 4-byte representation (big-endian)
        DyT_bytes = struct.pack('>f', np.float32(DyT_float))
        # Take the first 2 bytes (most significant bits) for BF16
        DyT_bf16_bytes = DyT_bytes[:2]
        # Convert to bit string
        DyT_bf16_bits = ''.join(f'{byte:08b}' for byte in DyT_bf16_bytes)
        # Convert the bf16 back to its float representation
        DyT_bf16_int = int(DyT_bf16_bits, 2) << 16  # Shift back to 32-bit float position
        DyT_bf16_float = struct.unpack('>f', struct.pack('>I', DyT_bf16_int))[0]
        outX.append(j)
        outY.append(DyT_bf16_float) # append the DyT_bf16_float value
    return outX, outY


def version2MSE():
    min = -3.9375  
    max = 4.0000
    step = 0.0625 
    _, piecewiseDyT_middle = getDyTTableValues(min, max, step) # grab only the second item in the tuple
    piecewiseDyT_left = [-1] * len(np.arange(-6, -3.9375, 0.0625))  # list containing only -1 for the range -6 to -4 inclusive(step= +0.0625!)
    piecewiseDyT_right = [1] * len(np.arange(4, 6.0625, 0.0625))  # list containing only +1 for the range 4 to 6 inclusive
    # concatenate the three lists
    piecewiseDyT = piecewiseDyT_left + piecewiseDyT_middle + piecewiseDyT_right

    x = np.arange(-6.0000, 6.0625, 0.0625) # start, stop, step
    print(f"amount of sampled points between -6 to 6: {len(x)}")
    exact_DyT =  np.tanh(x)

    errors = exact_DyT - piecewiseDyT
    mse = np.mean(np.square(errors))
    return mse

def version2MSESetRange(min, max, step, testmin=-10.0000, testmax=10.0000):
    _, piecewiseDyT_middle = getDyTTableValues(min, max, step) # grab only the second item in the tuple
    piecewiseDyT_left = [-1] * len(np.arange(testmin, min, step))  # list containing only zeroes for the range -6 to -4 inclusive(step= +0.0625!)
    piecewiseDyT_right = [1] * len(np.arange(max, testmax+step, step))  # list containing only +1 for the range 4 to 6 inclusive
    # concatenate the three lists
    piecewiseDyT = piecewiseDyT_left + piecewiseDyT_middle + piecewiseDyT_right

    x = np.arange(testmin, testmax+step, step) # start, stop, step
    print(f"amount of sampled points between -10 and 10: {len(x)}")
    exact_DyT = np.tanh(x)

    errors = exact_DyT - piecewiseDyT
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



    # print("======== testmin=-8.0000, testmax=8.0000 ========")
    # mse2a = version2MSESetRange(-3.9375, 4.0000, 0.0625, testmin=-8.0000, testmax=8.0000)  # default range
    # mse2b = version2MSESetRange(-4.0000 + 0.03125, 4.0000, 0.03125, testmin=-8.0000, testmax=8.0000) # default range + smaller step
    # mse2c = version2MSESetRange(-7.9375, 8.0000, 0.0625, testmin=-8.0000, testmax=8.0000)  # larger range
    # mse2d = version2MSESetRange(-8.0000 + 0.03125, 8.0000, 0.03125, testmin=-8.0000, testmax=8.0000)  # larger range + smaller step
    # print(f"Version 2a MSE: {mse2a:.7f}")
    # print(f"Version 2b MSE: {mse2b:.7f}")
    # print(f"Version 2c MSE: {mse2c:.7f}")
    # print(f"Version 2d MSE: {mse2d:.7f}")

    # print("======== testmin=-15.0000, testmax=15.0000 ========")
    # mse2a = version2MSESetRange(-3.9375, 4.0000, 0.0625, testmin=-15.0000, testmax=15.0000)  # default range
    # mse2b = version2MSESetRange(-4.0000 + 0.03125, 4.0000, 0.03125, testmin=-15.0000, testmax=15.0000) # default range + smaller step
    # mse2c = version2MSESetRange(-7.9375, 8.0000, 0.0625, testmin=-15.0000, testmax=15.0000)  # larger range
    # mse2d = version2MSESetRange(-8.0000 + 0.03125, 8.0000, 0.03125, testmin=-15.0000, testmax=15.0000)  # larger range + smaller step
    # print(f"Version 2a MSE: {mse2a:.7f}")
    # print(f"Version 2b MSE: {mse2b:.7f}")
    # print(f"Version 2c MSE: {mse2c:.7f}")
    # print(f"Version 2d MSE: {mse2d:.7f}")