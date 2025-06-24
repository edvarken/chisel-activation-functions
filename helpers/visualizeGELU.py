import matplotlib.pyplot as plt
import numpy as np
import math
import struct
from typing import List
from scipy.special import erf
        

def getGELUTableValues() -> tuple[List[float], List[float]]:
    outX = []
    outY = []
    for j in np.arange(-3.9375, 4.00000, 0.0625): # -3.9375 inclusive, 4.0000 exclusive
        gelu_float = round(j*0.5*(1+math.erf(j/math.sqrt(2))), 6)
        # Convert float32 to 4-byte representation (big-endian)
        gelu_bytes = struct.pack('>f', np.float32(gelu_float))
        # Take the first 2 bytes (most significant bits) for BF16
        gelu_bf16_bytes = gelu_bytes[:2]
        # Convert to bit string
        gelu_bf16_bits = ''.join(f'{byte:08b}' for byte in gelu_bf16_bytes)
        # Convert the bf16 back to its float representation
        gelu_bf16_int = int(gelu_bf16_bits, 2) << 16  # Shift back to 32-bit float position
        gelu_bf16_float = struct.unpack('>f', struct.pack('>I', gelu_bf16_int))[0]
        outX.append(j)
        outY.append(gelu_bf16_float) # append the gelu_bf16_float value
    return outX, outY


if __name__ == "__main__":
    gelu_plot = plt.figure(figsize=(10, 8)) 

    plt.title("GELU function and approximation", fontsize=18)
    plt.xlabel("x", fontsize=16) # linear x and y axes

    # set the x and y limits
    xmin = -6
    xmax = 6
    ymin = -1
    ymax = 6
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.ylabel("y", fontsize=16)
    plt.grid()
    # show more gridlines
    plt.xticks(np.arange(xmin, xmax+1, 1), fontsize=14)
    plt.yticks(np.arange(ymin, ymax, 1), fontsize=14)

    colors = ['k', 'r', 'r'] # black, red, r

    # real GELU
    x = np.linspace(xmin, xmax, 1000) # start, stop, num

    exact_gelu = x * 0.5 * (1 + erf(x / np.sqrt(2)))

    plt.plot(x, exact_gelu, label='exact GELU', color=colors[0], linestyle='-', linewidth=2)

    # GELU approximation : piecewise linear function
    # for x<=-4, y=0
    # for -4<x<4, y=one of 128 values out of a LookUpTable: just plot these values as points
    # for x>=4, y=x
    outX, outY = getGELUTableValues()

    approx_gelu = np.piecewise(x, [x < -4, (x >= -4) & (x < 4), x >= 4], [0, lambda x: np.nan, lambda x: x]) # use np.nan for -4<x<4
    plt.plot(outX, outY, '.', color=colors[2], markersize=3.6)
    plt.plot(x, approx_gelu, label='GELU(x) = 0 for x <= -4; one of 128 look-up-table values for -4 < x < 4; x for x >= 4', color=colors[2], linestyle='-')  # Combine labels into one

    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.10), ncol=1, fontsize=13)  # Place legend below the plot
    plt.tight_layout()  # Adjust layout to prevent stretching
    # plt.subplots_adjust(left=0.15, right=0.85, top=0.95, bottom=0.15)
    plt.show()