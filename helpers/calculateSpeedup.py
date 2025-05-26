# cpu_silu_cycles = 2621555 # for 32x32x32 input tensor.
# assume 16by16 systolic array, and thus 16 hardware units in parallel, to compute SiLU activation, each with 1 cycle latency

# create a bar chart that compares amount of cycles for CPU versus hardware SiLU activation for different sized input tensors [X,Y,C], and plot the relative speedup
import numpy as np
import matplotlib.pyplot as plt
# Data for the bar chart
input_sizes = ['32x32x32', '64x64x320']
cpu_cycles = [2621555, 5000000]  # Replace actual CPU cycles for 64x64x320
hardware_cycles = [(32*32*32)/16, (64*64*320)/16]  # Assuming 16 hardware units in parallel
speedups = [cpu / hardware for cpu, hardware in zip(cpu_cycles, hardware_cycles)]
# Create the bar chart
plt.figure(figsize=(10, 6))
x = np.arange(len(input_sizes))  # the label locations
width = 0.35  # the width of the bars
plt.bar(x - width/2, cpu_cycles, width, label='CPU Cycles', color='blue')
plt.bar(x + width/2, hardware_cycles, width, label='Hardware Cycles', color='orange')
# Calculate the center x-position between the two bars
center_x = (x[0] + x[1]) / 2
speedup_x_positions = [x[0] + center_x/2.5, x[1] + center_x/2.5]  # Adjusted x-positions for speedup text

# Place the speedup value above the bars
for cpu, hardware, speedup, speedup_x_position in zip(cpu_cycles, hardware_cycles, speedups, speedup_x_positions):
    plt.text(speedup_x_position, max(cpu, hardware) * 0.7, f"Speedup: {speedup:.2f}x", ha='center', va='bottom', fontsize=12, color='red')
plt.xlabel('Input Tensor Size (XxYxC)')
plt.ylabel('Clock Cycles')
plt.title('SiLU Activation: CPU cycles vs Hardware Cycles')
plt.xticks(x, input_sizes)
plt.legend()
plt.tight_layout()
plt.show()