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
    silu_plot = plt.figure(figsize=(10, 8)) 
    plt.rcParams["font.family"] = "Times New Roman"

    plt.title("Exact SiLU and two approximations", fontsize=18)
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

    colors = ['k', 'r', 'blue'] # blue, red, green

    # real SiLU
    x = np.linspace(xmin, xmax, 1000) # start, stop, num
    exact_silu = x / (1 + np.exp(-x))
    plt.rcParams['text.usetex'] = True
    plt.plot(x, exact_silu, label=r'exact SiLU', color=colors[0], linestyle='-', linewidth=1)

    # SiLU approximation 1
    def relu6(x):
        return np.maximum(0, np.minimum(6, x))
    approx_silu1 = (x * relu6(x+3)) / 6
    plt.rcParams['text.usetex'] = True
    plt.plot(x, approx_silu1, label=r'$SiLU_1(x) = \frac{x}{6} \cdot \mathrm{ReLU6}(x+3)$', color=colors[1], linestyle='--')

    # SiLU approximation 2: piecewise linear function
    # for x<=-4, y=0
    # for -4<x<4, y=one of 128 values out of a LookUpTable: just plot these values as points
    # for x>=4, y=x
    outX, outY = getSiluTableValues()

    approx_silu2_left = np.where(x <= -4, 0, np.nan)
    approx_silu2_right = np.where(x >= 4, x, np.nan)
    plt.plot(x, approx_silu2_left, color=colors[2], linestyle=':', label=None, linewidth=3)
    plt.plot(outX, outY, ':', color=colors[2], markersize=3.6, label=None, linewidth=3)
    # plt.plot(x, approx_silu2_right, color=colors[2], linestyle='-', label=(
    #     "SiLU2(x) =\n"
    #     "   0         if  x ≤ -4\n"
    #     "   LUT(x)    if -4 < x < 4\n"
    #     "   x         if  x ≥ 4"
    # ))
    plt.rcParams['text.usetex'] = True
#     plt.plot(x, approx_silu2_right, color=colors[2], linestyle='-', label=(
#     r"$\mathrm{SiLU2}(x) =$" "\n"
#     r"$\quad 0 \quad\quad \mathrm{if}\ x \leq -4$" "\n"
#     r"$\quad \mathrm{LUT}(x) \quad \mathrm{if}\ -4 < x < 4$" "\n"
#     r"$\quad x \quad\quad \mathrm{if}\ x \geq 4$"
# ))
    plt.plot(x, approx_silu2_right, color=colors[2], linestyle=':',linewidth=3, label=(
        r'$SiLU_2(x)=\left\{ \begin{array}{l} 0, \qquad \qquad x \leq -4 \\ LUT(x), \quad -4 < x < 4 \\ x, \qquad \qquad x \geq 4 \end{array} \right.$'
    ))




    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.10), ncol=1, fontsize=16, frameon=True)  # Place legend below the plot

    plt.tight_layout()  # Adjust layout to prevent stretching
    plt.show()