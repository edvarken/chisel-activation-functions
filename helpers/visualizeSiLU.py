# import itertools
import matplotlib.pyplot as plt
import numpy as np
import math
import struct
from typing import List
        

def getSiluTableValues() -> tuple[List[float], List[float]]:
    outX = []
    outY = []
    for j in np.arange(-3.9375, 4.00000, 0.0625): # -3.9375 inclusive, 4.0000 exclusive
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


def relu(x):
    return np.maximum(0, x)



if __name__ == "__main__":
    roofline_plot = plt.figure(figsize=(12, 8)) 

    plt.title("SiLU function and approximations")
    plt.xlabel("x") # linear x and y axes

    # set the x and y limits
    xmin = -6
    xmax = 6
    ymin = -1
    ymax = 6
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.ylabel("y")
    plt.grid()
    # show more gridlines
    plt.xticks(np.arange(xmin, xmax+1, 1))
    plt.yticks(np.arange(ymin, ymax, 1))

    colors = ['k', 'r', 'blue'] # blue, red, green

    # real SiLU
    x = np.linspace(xmin, xmax, 1000) # start, stop, num
    exact_silu = x / (1 + np.exp(-x))
    plt.plot(x, exact_silu, label='exact SiLU', color=colors[0], linestyle='-', linewidth=2)

    # SiLU approximation 1
    def relu6(x):
        return np.maximum(0, np.minimum(6, x))
    approx_silu1 = (x * relu6(x+3)) / 6
    plt.plot(x, approx_silu1, label='x * relu6(x+3) / 6', color=colors[1], linestyle='--')

    # SiLU approximation 2: piecewise linear function
    
    # for x<=-4, y=0
    # for -4<x<4, y=one of 128 values out of a LookUpTable: just plot these values as points
    # for x>=4, y=x
    outX, outY = getSiluTableValues()

    approx_silu2 = np.piecewise(x, [x < -4, (x >= -4) & (x < 4), x >= 4], [0, lambda x: np.nan, lambda x: x]) # use np.nan for -4<x<4
    plt.plot(outX, outY, '.', color=colors[2], markersize=3.6)
    plt.plot(x, approx_silu2, label='y=0 for x <= -4, y = one of 128 look-up-table values for -4 < x < 4, y = x for x >= 4', color=colors[2], linestyle='-')  # Combine labels into one

    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.05), ncol=3)  # Place legend below the plot
    plt.tight_layout()  # Adjust layout to prevent stretching
    plt.show()