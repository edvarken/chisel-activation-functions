import matplotlib.pyplot as plt
import numpy as np
import math
import struct
from typing import List
        

def getDyTTableValues() -> tuple[List[float], List[float]]:
    outX = []
    outY = []
    for j in np.arange(-3.9375, 4.00000, 0.0625): # -3.9375 inclusive, 4.0000 exclusive
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
        outY.append(DyT_bf16_float) # append the silu_bf16_float value
    return outX, outY


if __name__ == "__main__":
    DyT_plot = plt.figure(figsize=(12, 8)) 

    plt.title("Dynamic Tanh function and approximation")
    plt.xlabel("α*x") # linear x and y axes

    # set the x and y limits
    xmin = -5
    xmax = 5
    ymin = -2
    ymax = 2
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.ylabel("y")
    plt.grid()
    # show more gridlines
    plt.xticks(np.arange(xmin, xmax+1, 1))
    plt.yticks(np.arange(ymin, ymax, 1))

    colors = ['k', 'r', 'blue'] # black, red, green

    # real DyT: we use x to represent α*x
    x = np.linspace(xmin, xmax, 1000) # start, stop, num
    exact_DyT = np.tanh(x)
    plt.plot(x, exact_DyT, label='exact DyT', color=colors[0], linestyle='-', linewidth=2)

    # DyT approximation: piecewise linear function
    # for x<=-4, y=-1
    # for -4<x<4, y=one of 128 values out of a LookUpTable: just plot these values as points
    # for x>=4, y=+1
    outX, outY = getDyTTableValues()

    approx_DyT = np.piecewise(x, [x < -4, (x >= -4) & (x < 4), x >= 4], [-1, lambda x: np.nan, lambda x: 1]) # use np.nan for -4<x<4
    plt.plot(outX, outY, '.', color=colors[1], markersize=3.6)
    plt.plot(x, approx_DyT, label='DyT(α*x) = -1 for α*x <= -4; one of 128 look-up-table values for -4 < α*x < 4; +1 for α*x >= 4', color=colors[1], linestyle='-')  # Combine labels into one

    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.05), ncol=3)  # Place legend below the plot
    plt.tight_layout()  # Adjust layout to prevent stretching
    plt.show()