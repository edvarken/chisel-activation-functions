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


def visualizeGELUAndApprox():
    gelu_plot = plt.figure(figsize=(10, 8)) 
    plt.rcParams["font.family"] = "Times New Roman"
    plt.title("GELU function and approximation", fontsize=18)
    # set the x and y limits
    xmin = -6
    xmax = 6
    ymin = -1
    ymax = 6
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.xlabel("x", fontsize=16)
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


def getSigmoidTableValues(entries=32) -> tuple[List[float], List[float]]:
    outX = []
    sigmoidLUT = []
    if entries == 32:
        step = 0.03125/2
    elif entries == 64:
        step = 0.015625/2
    elif entries == 128:
        step = 0.0078125/2

    # f(x) = 1-f(-x)
    for j in np.arange(1.0000-step, 0.5, -step): # 0.5 inclusive, 1.0000 exclusive
        InvSigmoid_float = -math.log((1/(1-j)) - 1)
        outX.append(InvSigmoid_float)
        sigmoidLUT.append(1-j) # append the silu_bf16_float value
        
    for j in np.arange(0.5, 1.00000, step): # 0.5 inclusive, 1.0000 exclusive
        InvSigmoid_float = -math.log((1/j) - 1)
        outX.append(InvSigmoid_float)
        sigmoidLUT.append(j) # append the silu_bf16_float value

    return outX, sigmoidLUT


def relu(x):
    return np.maximum(0, x)

def relu6(x):
    return np.maximum(0, np.minimum(6, x))


def visualizeSiLUAndApprox():
    silu_plot = plt.figure(figsize=(10, 8)) 
    plt.rcParams["font.family"] = "Times New Roman"
    plt.title("Exact SiLU and three approximations", fontsize=18)

    xmin = -6
    xmax = 6
    ymin = -1
    ymax = 6
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.xlabel("x", fontsize=16) # linear x and y axes
    plt.ylabel("y", fontsize=16)
    plt.grid()
    # show more gridlines
    plt.xticks(np.arange(xmin, xmax+1, 1), fontsize=14)
    plt.yticks(np.arange(ymin, ymax, 1), fontsize=14)

    colors = ['k', 'green', 'blue', 'red'] # black, red, green, blue

    # real SiLU
    x = np.linspace(xmin, xmax, 1000) # start, stop, num
    exact_silu = x / (1 + np.exp(-x))
    plt.rcParams['text.usetex'] = True
    plt.plot(x, exact_silu, label=r'exact SiLU', color=colors[0], linestyle='-', linewidth=1)

    # SiLU approximation 1
    approx_silu1 = (x * relu6(x+3)) / 6
    plt.rcParams['text.usetex'] = True
    plt.plot(x, approx_silu1, label=r'$SiLU_1(x) = \frac{x}{6} \cdot \mathrm{ReLU6}(x+3)$', color=colors[1], linestyle='--')

    # SiLU approximation 2: piecewise linear function
    # for x<=-4, y=0
    # for -4<x<4, y=LUT(x): one of 128 values out of a LookUpTable: just plot these values as points
    # for x>=4, y=x
    outX, outY = getSiluTableValues()
    approx_silu2_left = np.where(x <= -4, 0, np.nan)
    approx_silu2_right = np.where(x >= 4, x, np.nan)
    plt.plot(x, approx_silu2_left, color=colors[2], linestyle='-', linewidth=1.5, label=None)
    plt.scatter(outX, outY, marker='*', color=colors[2], s=15, label=None)
    plt.rcParams['text.usetex'] = True
    plt.plot(x, approx_silu2_right, color=colors[2], linestyle='-', linewidth=1.5, label=(
        r'$SiLU_2(x)=\left\{ \begin{array}{l} 0, \qquad \qquad \qquad \quad x \leq -4 \\ siluLUT(x), \quad -4 < x < 4 \\ x, \qquad \qquad \qquad \quad x \geq 4 \end{array} \right.$'
    ))

    # SiLU approximation 3: piecewise linear function using inverse Sigmoid
    # for x<=-8, y=0
    # for -8<x<8, y=x*sigmoid(x)
    # for x>=8, y=x
    outX, sigmoidLUT = getSigmoidTableValues(entries=64)
    approx_silu3_left = np.where(x <= -8, 0, np.nan)
    approx_silu3_right = np.where(x >= 8, x, np.nan)
    plt.plot(x, approx_silu3_left, color=colors[3], linestyle='-', linewidth=1.5, label=None)
    plt.scatter(outX, np.array(sigmoidLUT) * np.array(outX), marker='.', s=20, label=None, color=colors[3])
    plt.rcParams['text.usetex'] = True
    plt.plot(x, approx_silu3_right, color=colors[3], linestyle='-', linewidth=1.5, label=(
        r'$SiLU_3(x)=\left\{ \begin{array}{l} 0, \qquad \qquad \qquad \qquad \quad  x \leq -8 \\ x*sigmoidLUT(x), -8 < x < 8 \\ x, \qquad \qquad \qquad \qquad \quad  x \geq 8 \end{array} \right.$'
    ))
    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.10), ncol=1, fontsize=16, frameon=True)  # Place legend below the plot
    plt.tight_layout()  # Adjust layout to prevent stretching
    plt.show()


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


def visualizeDyTAndApprox():
    DyT_plot = plt.figure(figsize=(10, 8)) 
    plt.rcParams["font.family"] = "Times New Roman"
    plt.title("Dynamic Tanh function and approximation", fontsize=18)
    # set the x and y limits
    xmin = -5
    xmax = 5
    ymin = -2
    ymax = 2
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.xlabel("α*x", fontsize=16) # linear x and y axes
    plt.ylabel("y", fontsize=16)
    plt.grid()
    # show more gridlines
    plt.xticks(np.arange(xmin, xmax+1, 1), fontsize=14)
    plt.yticks(np.arange(ymin, ymax, 1), fontsize=14)

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

    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.10), ncol=1, fontsize=13)  # Place legend below the plot
    plt.tight_layout()  # Adjust layout to prevent stretching
    plt.show()

#########################################################################################################
#########################################################################################################

def visualizeGELUAndSiLU():
    plot = plt.figure(figsize=(12, 10)) 
    plt.rcParams["font.family"] = "Times New Roman"
    xmin = -4
    xmax = 4
    ymin = -4
    ymax = 4

    ax = plt.gca()
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.xticks(np.arange(xmin, xmax+1, 1), fontsize=14)
    plt.yticks(np.arange(ymin, ymax+1, 1), fontsize=14)

    colors = ['blue', 'red']

    # real SiLU
    x = np.linspace(xmin, xmax, 1000)
    exact_silu = x / (1 + np.exp(-x))
    plt.rcParams['text.usetex'] = True
    ax.plot(x, exact_silu, label='SiLU', color=colors[0], linestyle='-', linewidth=1.5)

    # real GELU
    exact_gelu = x * 0.5 * (1 + erf(x / np.sqrt(2)))
    ax.plot(x, exact_gelu, label='GELU', color=colors[1], linestyle='-', linewidth=2)

    # Move spines to center
    ax.spines['left'].set_position('center')
    ax.spines['bottom'].set_position('center')
    ax.spines['right'].set_color('none')
    ax.spines['top'].set_color('none')

    # Make axes arrows (ensure arrow tips are visible within figure bounds)
    arrowprops = dict(arrowstyle="->", linewidth=1.2, color='black', shrinkA=0, shrinkB=0)
    # Use slightly less than the axis limits for arrow tips
    ax.annotate('', xy=(xmax, 0), xytext=(xmin, 0), arrowprops=arrowprops, clip_on=False)
    ax.annotate('', xy=(0, ymax), xytext=(0, ymin), arrowprops=arrowprops, clip_on=False)

    # Place axis labels at arrow tips, just inside the bounds
    ax.annotate('x', xy=(xmax, 0), xytext=(xmax-0.15, -0.25), fontsize=16, fontweight='bold', clip_on=False)
    ax.annotate('y', xy=(0, ymax), xytext=(-0.35, ymax-0.15), fontsize=16, fontweight='bold', clip_on=False)

    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.05), ncol=2, fontsize=15)
    plt.tight_layout()
    plt.show()


def visualizeSigmoid():
    plt.figure(figsize=(12, 10)) 
    plt.rcParams["font.family"] = "Times New Roman"
    # xmin = -8 # these were used for the invSigmoid plot
    # xmax = 8
    # ymin = -0.2
    # ymax = 1
    xmin = -4
    xmax = 4
    ymin = -4
    ymax = 4

    ax = plt.gca()
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    # Ticks at axis ends so they overlap with arrows
    plt.xticks(np.arange(xmin, xmax+0.5, 2), fontsize=14)
    plt.yticks(np.arange(ymin, ymax+0.1, 0.1), fontsize=14)

    colors = ['k', 'grey']

    # real sigmoid
    x = np.linspace(xmin, xmax, 1000)
    exact_silu = 1 / (1 + np.exp(-x))
    plt.rcParams['text.usetex'] = True
    ax.plot(x, exact_silu, label=r'Sigmoid', color=colors[0], linestyle='-', linewidth=1.7)

    # Move spines so they overlap with the arrows
    ax.spines['left'].set_position(('data', 0))
    ax.spines['bottom'].set_position(('data', 0))
    ax.spines['right'].set_color('none')
    ax.spines['top'].set_color('none')

    # Make axes arrows (draw arrows above the axes ticks)
    arrowprops = dict(arrowstyle="->", linewidth=1.2, color='black', shrinkA=0, shrinkB=0, zorder=10)
    ax.annotate('', xy=(xmax, 0), xytext=(xmin, 0), arrowprops=arrowprops, clip_on=False)
    ax.annotate('', xy=(0, ymax), xytext=(0, ymin), arrowprops=arrowprops, clip_on=False)
    ax.set_axisbelow(True)

    # Place axis labels at arrow tips, just inside the bounds
    ax.annotate('x', xy=(xmax, 0), xytext=(xmax-0.25, -0.1), fontsize=16, fontweight='bold', clip_on=False)
    ax.annotate('y', xy=(0, ymax), xytext=(-0.6, ymax-0.05), fontsize=16, fontweight='bold', clip_on=False)

    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.05), ncol=1, fontsize=15)
    plt.tight_layout()
    plt.show()


def visualizeSiLUAndZeroOrderApprox():
    plot = plt.figure(figsize=(12, 10)) 
    plt.rcParams["font.family"] = "Times New Roman"
    xmin = -2
    xmax = 4
    ymin = -1
    ymax = 4

    ax = plt.gca()
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.xticks(np.arange(xmin, xmax+1, 1), fontsize=14)
    plt.yticks(np.arange(ymin, ymax+1, 1), fontsize=14)

    colors = ['k', 'blue'] # black for official, blue for 256 segments of zero-order approx. silu

    # real SiLU
    x = np.linspace(xmin, xmax, 1000)
    exact_silu = x / (1 + np.exp(-x))
    plt.rcParams['text.usetex'] = True
    ax.plot(x, exact_silu, label='SiLU', color=colors[0], linestyle='-', linewidth=1.5)

    # 256 segments of zero-order approximation of SiLU in -8,8 which means 128 segments in -4,4
    # which means 64 segments in 0,4
    segment_edges = np.linspace(xmin, xmax, int((xmax-xmin)/16 * 256))  # 96 segments => 97 edges
    segment_centers = (segment_edges[:-1] + segment_edges[1:]) / 2  # 96 centers
    silu_centers = segment_centers / (1 + np.exp(-segment_centers))

    # For plotting, draw a horizontal line for each segment
    for i in range(len(segment_centers)): # 96 segments in -2,4
        ax.hlines(silu_centers[i], segment_edges[i], segment_edges[i+1], colors=colors[1], linestyles='-', linewidth=1.5, label='SiLU zero-order approx. using 256 segments in [-8,8]' if i == 0 else "")

    # Only add the label once for the legend (on the first segment)


    # Move left and bottom spines to zero position
    ax.spines['left'].set_position(('data', 0))
    ax.spines['bottom'].set_position(('data', 0))

    # Hide the top and right spines
    ax.spines['right'].set_color('none')
    ax.spines['top'].set_color('none')

    # Optional: set ticks position
    ax.xaxis.set_ticks_position('bottom')
    ax.yaxis.set_ticks_position('left')

    # Make axes arrows (ensure arrow tips are visible within figure bounds)
    arrowprops = dict(arrowstyle="->", linewidth=1.2, color='black', shrinkA=0, shrinkB=0)
    # Use slightly less than the axis limits for arrow tips
    ax.annotate('', xy=(xmax, 0), xytext=(xmin, 0), arrowprops=arrowprops, clip_on=False)
    ax.annotate('', xy=(0, ymax), xytext=(0, ymin), arrowprops=arrowprops, clip_on=False)

    # Place axis labels at arrow tips, just inside the bounds
    ax.annotate('x', xy=(xmax, 0), xytext=(xmax-0.15, -0.25), fontsize=16, fontweight='bold', clip_on=False)
    ax.annotate('y', xy=(0, ymax), xytext=(-0.35, ymax-0.15), fontsize=16, fontweight='bold', clip_on=False)

    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.05), ncol=2, fontsize=15)
    plt.tight_layout()
    plt.show()


def visualizehSiLU():
    plot = plt.figure(figsize=(12, 10)) 
    plt.rcParams["font.family"] = "Times New Roman"
    xmin = -4
    xmax = 4
    ymin = -4
    ymax = 4

    ax = plt.gca()
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.xticks(np.arange(xmin, xmax+1, 1), fontsize=14)
    plt.yticks(np.arange(ymin, ymax+1, 1), fontsize=14)

    colors = ['k', 'blue']

    # real SiLU
    x = np.linspace(xmin, xmax, 1000)
    exact_silu = x / (1 + np.exp(-x))
    plt.rcParams['text.usetex'] = True
    ax.plot(x, exact_silu, label='SiLU(x)', color=colors[0], linestyle='-', linewidth=1.5)

    # h-SiLU approximation: piecewise linear function
    np.maximum(0, np.minimum(6, x))
    approx_silu1 = (x * relu6(x+3)) / 6
    ax.plot(x, approx_silu1, label=r'h-SiLU(x)', color=colors[1], linestyle='--')

    # Move spines to center
    ax.spines['left'].set_position('center')
    ax.spines['bottom'].set_position('center')
    ax.spines['right'].set_color('none')
    ax.spines['top'].set_color('none')

    # Make axes arrows (ensure arrow tips are visible within figure bounds)
    arrowprops = dict(arrowstyle="->", linewidth=1.2, color='black', shrinkA=0, shrinkB=0)
    # Use slightly less than the axis limits for arrow tips
    ax.annotate('', xy=(xmax, 0), xytext=(xmin, 0), arrowprops=arrowprops, clip_on=False)
    ax.annotate('', xy=(0, ymax), xytext=(0, ymin), arrowprops=arrowprops, clip_on=False)

    # Place axis labels at arrow tips, just inside the bounds
    ax.annotate('x', xy=(xmax, 0), xytext=(xmax-0.15, -0.25), fontsize=16, fontweight='bold', clip_on=False)
    ax.annotate('y', xy=(0, ymax), xytext=(-0.35, ymax-0.15), fontsize=16, fontweight='bold', clip_on=False)

    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.05), ncol=2, fontsize=15)
    plt.tight_layout()
    plt.show()


def visualizeSigmoidAndFirstOrderApprox():
    plot = plt.figure(figsize=(12, 10)) 
    plt.rcParams["font.family"] = "Times New Roman"
    xmin = 0
    xmax = 6.02
    ymin = 0
    ymax = 1.01

    ax = plt.gca()
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.xticks(np.arange(xmin, xmax, 0.25), fontsize=14)
    plt.yticks(np.arange(ymin, ymax, 0.5), fontsize=14)

    colors = ['k', 'blue'] # black for official, blue for 20 segments of first-order approx. sigmoid

    # # real Sigmoid
    x = np.linspace(xmin, xmax, 1000)
    exact_sigmoid = 1/ (1 + np.exp(-x))
    plt.rcParams['text.usetex'] = True
    ax.plot(x, exact_sigmoid, label='Sigmoid', color=colors[0], linestyle='-', linewidth=1)

    # 20 segments of first-order approximation of SIGMOID in [0,6]: 4 of which in [0,2]; the other 16 in [2,6]
    segment_edges1 = np.linspace(0, 2, 5) 
    segment_edges2 = np.linspace(2, 6, 17)

    # For plotting, draw a linear line for each segment
    added_label_already = False
    for segmentgroup in [segment_edges1, segment_edges2]:
        for i in range(len(segmentgroup)-1):
            x0 = segmentgroup[i]
            x1 = segmentgroup[i+1]
            y0 = 1 / (1 + np.exp(-x0))
            y1 = 1 / (1 + np.exp(-x1))
            if not added_label_already:
                ax.plot([x0, x1], [y0, y1], color=colors[1], linestyle='-', marker='o', markersize=3, linewidth=0.5, label='Sigmoid first-order approx. using 20 segments in [0,6]' if i == 0 else "")
            else:
                ax.plot([x0, x1], [y0, y1], color=colors[1], linestyle='-', marker='o', markersize=3, linewidth=0.5, label= "")
        added_label_already = True
            
    # Move left and bottom spines to zero position
    ax.spines['left'].set_position(('data', 0))
    ax.spines['bottom'].set_position(('data', 0))

    # Hide the top and right spines
    ax.spines['right'].set_color('none')
    ax.spines['top'].set_color('none')

    # Optional: set ticks position
    ax.xaxis.set_ticks_position('bottom')
    ax.yaxis.set_ticks_position('left')

    # Make axes arrows (ensure arrow tips are visible within figure bounds)
    arrowprops = dict(arrowstyle="->", linewidth=1.2, color='black', shrinkA=0, shrinkB=0)
    # Use slightly less than the axis limits for arrow tips
    ax.annotate('', xy=(xmax, 0), xytext=(xmin, 0), arrowprops=arrowprops, clip_on=False)
    ax.annotate('', xy=(0, ymax), xytext=(0, ymin), arrowprops=arrowprops, clip_on=False)

    # Place axis labels at arrow tips, just inside the bounds
    ax.annotate('x', xy=(xmax, 0), xytext=(xmax-0.10, -0.05), fontsize=16, fontweight='bold', clip_on=False)
    ax.annotate('y', xy=(0, ymax), xytext=(-0.15, ymax-0.05), fontsize=16, fontweight='bold', clip_on=False)

    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.05), ncol=2, fontsize=15)
    plt.tight_layout()
    plt.show()

#### visualize derivatives of SiLU
def mantissaRounder(function_bytes):
    """
    Rounds the mantissa of a float32 to the nearest BF16 value, if tied round to even.
    """
    # Take the first 2 bytes (most significant bits) for BF16
    rounded_function_bf16_bytes = function_bytes[:2]
    # Convert the last 2 bytes to an integer for comparison
    mantissa_tail = int.from_bytes(function_bytes[2:4], byteorder='big')
    retained_mantissa = int.from_bytes(rounded_function_bf16_bytes, byteorder='big')
    if ((mantissa_tail > 0x8000) or (mantissa_tail==0x8000 and retained_mantissa&1)): # If (bit 15 is 1 and any lower bit is 1) or if (bit 15 is 1 and LSB of retained 7-bit mantissa is 1), round up
        # Convert the first 2 bytes to int, add 1, then back to bytes
        rounded_bf16_int = int.from_bytes(rounded_function_bf16_bytes, byteorder='big') + 1 # rounding up
        return rounded_bf16_int.to_bytes(2, byteorder='big')
    else:
        return rounded_function_bf16_bytes # else we can just truncate(=rounding down)

def createBreakpoints(min, max, step, function):
    breakpoints = []
    # Create breakpoints for the sigmoid function, or for the silu function
    j= min
    while j < max+step:
        if function ==  "sigmoid-uniform-x":
            function_float = 1 / (1 + math.exp(-j))
        elif function == "silu-uniform-x":
            function_float = j / (1 + math.exp(-j))
        elif function == "gelu-uniform-x":
            function_float = 0.5 * j * (1 + math.erf(j / math.sqrt(2)))
        function_float = round(function_float, (4+10))  # round to 14 decimal places
        # Convert float32 to 4-byte representation (big-endian)
        function_bytes = struct.pack('>f', np.float32(function_float))
        # Take the first 2 bytes (most significant bits) for BF16, but round-to-nearest-and-to-even-if-tied 
        function_bf16_bytes = mantissaRounder(function_bytes)
        # Convert to bit string
        function_bf16_bits = ''.join(f'{byte:08b}' for byte in function_bf16_bytes)
        # Convert the bf16 back to its float representation
        function_bf16_int = int(function_bf16_bits, 2) << 16  # Shift back to 32-bit float position
        function_bf16_float = struct.unpack('>f', struct.pack('>I', function_bf16_int))[0]
        # breakpoints.append((round(j,14), round(function_bf16_float, 14))) # round to 9 decimal places
        breakpoints.append((j, function_float))
        j += step
    return breakpoints

def calculateSlopesAndYIntercepts(breakpoints):
    slopes = []
    y_intercepts = []
    mirrored_y_intercepts = []
    for i in range(len(breakpoints) - 1):
        x0, y0 = breakpoints[i]
        x1, y1 = breakpoints[i + 1]
        slope = (y1 - y0) / (x1 - x0)
        slopes.append(slope)
        y_intercept = y0 - slope * x0
        y_intercepts.append(y_intercept)
        mirrored_y_intercepts.append(1 - y_intercept)  # For the mirrored function
    return slopes, y_intercepts, mirrored_y_intercepts

def visualizeSiLUAndDerivatives():
    plot = plt.figure(figsize=(12, 10)) 
    plt.rcParams["font.family"] = "Times New Roman"
    xmin = -4
    xmax = 4
    ymin = -4
    ymax = 4

    ax = plt.gca()
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.xticks(np.arange(xmin, xmax+1, 1), fontsize=14)
    plt.yticks(np.arange(ymin, ymax+1, 1), fontsize=14)

    colors = ['blue', 'black'] # blue for derivatives

    # real SiLU
    x = np.linspace(xmin, xmax, 1000)
    exact_silu = x / (1 + np.exp(-x))
    plt.rcParams['text.usetex'] = True
    ax.plot(x, exact_silu, label='SiLU', color=colors[0], linestyle='-', linewidth=1.5)

    # plot the derivatives for 24 input segments between (-6,6)
    brkpts = createBreakpoints(-4, 4, 1.0, "silu-uniform-x")  # show less segments for better visibility
    slopes, y_intercepts, mirrored_y_intercepts = calculateSlopesAndYIntercepts(brkpts) # calculate slopes and y-intercepts for the segments

    # plot the derivatives, showing that they intersect the y-axis at same locations for inputs left and right of the origin.
    print(len(slopes))
    for i in range(len(slopes)):
        if (i == len(slopes)/2 or i == len(slopes)/2 - 1):  # skip the middle segment to avoid double plotting
            continue  
        # stop at the intercept with y-axis
        if i < len(slopes) / 2:  # left side of the origin
            x = np.linspace(-4, 0, 100)
            plt.plot(x, slopes[i]*x + y_intercepts[i], color=colors[1], linestyle='--', linewidth=1, label='local derivative' if i == 0 else "")
        else:  # right side of the origin
            x = np.linspace(0, 4, 100)
            plt.plot(x, slopes[i]*x + y_intercepts[i], color=colors[1], linestyle='--', linewidth=1, label='local derivative' if i == 0 else "")
    # place dots on the y-axis at the intercepts
    for i in range(len(y_intercepts)):
        if (i == len(slopes)/2 or i == len(slopes)/2 - 1):  # skip the middle segment to avoid double plotting
            continue  
        else:
            plt.plot(0, y_intercepts[i], 'o', color=colors[1], markersize=3)

    # Move spines to center
    ax.spines['left'].set_position('center')
    ax.spines['bottom'].set_position('center')
    ax.spines['right'].set_color('none')
    ax.spines['top'].set_color('none')

    # Make axes arrows (ensure arrow tips are visible within figure bounds)
    arrowprops = dict(arrowstyle="->", linewidth=1.2, color='black', shrinkA=0, shrinkB=0)
    # Use slightly less than the axis limits for arrow tips
    ax.annotate('', xy=(xmax, 0), xytext=(xmin, 0), arrowprops=arrowprops, clip_on=False)
    ax.annotate('', xy=(0, ymax), xytext=(0, ymin), arrowprops=arrowprops, clip_on=False)

    # Place axis labels at arrow tips, just inside the bounds
    ax.annotate('x', xy=(xmax, 0), xytext=(xmax-0.15, -0.25), fontsize=16, fontweight='bold', clip_on=False)
    ax.annotate('y', xy=(0, ymax), xytext=(-0.35, ymax-0.15), fontsize=16, fontweight='bold', clip_on=False)

    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.05), ncol=2, fontsize=15)
    plt.tight_layout()
    plt.show()


if __name__ == "__main__":
    # visualizeSiLUAndApprox()
    # visualizeGELUAndApprox()
    # visualizeDyTAndApprox()
    ###########################

    # visualizeGELUAndSiLU()
    # visualizeSiLUAndZeroOrderApprox()
    # visualizeSigmoid()
    # visualizehSiLU()
    # visualizeSigmoidAndFirstOrderApprox()
    visualizeSiLUAndDerivatives()