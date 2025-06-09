import numpy as np
import matplotlib.pyplot as plt

def bar_chart_silu_speedup():
    # cpu_silu_cycles = 2621555 # for 32x32x32 input tensor.
    # assume 16by16 systolic array, and thus 16 hardware units in parallel, to compute SiLU activation, each with 1 cycle latency
    # create a bar chart that compares amount of cycles for CPU versus hardware SiLU activation for different sized input tensors [X,Y,C], and plot the relative speedup
    # Data for the bar chart
    # 32x32x32: 2621555 
    # 32x32x64: 5299805 -> 32x32x640: 5299805*10
    # 64x64x32: 10632921 -> 64x64x320: 10632921*10
    input_sizes = ['64x64x320', '32x32x640', '16x16x1280', '8x8x1280']  # Input tensor sizes
    cpu_cycles = [10632921*10, 5299805*10, 5299805*5, 5299805*5/4]  # Replace actual CPU cycles for 64x64x320
    hardware_cycles = [(64*64*320)/16, (32*32*640)/16, (16*16*1280)/16, (8*8*1280)/16]  # Assuming 16 hardware units in parallel
    speedups = [cpu / hardware for cpu, hardware in zip(cpu_cycles, hardware_cycles)]
    average_speedup = sum(speedups) / len(speedups)
    # Create the bar chart
    plt.figure(figsize=(10, 6))
    x = np.arange(len(input_sizes))  # the label locations
    width = 0.35  # the width of the bars
    plt.bar(x - width/2, cpu_cycles, width, label='CPU Cycles', color='blue')
    plt.bar(x + width/2, hardware_cycles, width, label='Hardware Cycles', color='orange')

    plt.yscale('log')  # Make y-axis logarithmic
    # plt.grid(axis='y', linestyle='--', which='both')  # Add horizontal gridlines

    # Calculate the center x-position between the two bars
    center_x = (x[0] + x[1]) / 2
    speedup_x_positions = [x[0] + center_x/2.5, x[1] + center_x/2.5]  # Adjusted x-positions for speedup text

    center_x_position = (x[0] + x[1] + x[2] + x[3]) / 4
    y_position_just_under_top_of_chart = max(max(cpu_cycles), max(hardware_cycles)) * 1.0  # Position text just under the top of the chart

    # Place the speedup value above the bars
    # for cpu, hardware, speedup, speedup_x_position in zip(cpu_cycles, hardware_cycles, speedups, speedup_x_positions):
    #     plt.text(speedup_x_position, max(0, hardware) * 1.4, f"Speedup: {speedup:.2f}x", ha='center', va='bottom', fontsize=12, color='red')
    plt.text(center_x_position,  y_position_just_under_top_of_chart, f"Average Speedup: {average_speedup:.2f}x", ha='center', va='bottom', fontsize=12, color='red')
    plt.xlabel('Input Tensor Size (XxYxC)')
    plt.ylabel('Clock Cycles')
    plt.title('SiLU Activation: CPU cycles vs Hardware Cycles')
    plt.xticks(x, input_sizes)
    plt.legend()
    plt.tight_layout()
    plt.show()

def bar_chart_conv_matmul():
    input_sizes = ['CONV3 | 64x64x320', 'static MatMul | 64x64x320']  # Input tensor sizes for level 0 of UNET.
    hardware_cycles_ws = [15874989, 5795875] # CONV3 WS, static MatMul WS
    hardware_cycles_os = [0, 9448426] # CONV3 OS does not exist, static MatMul OS
    # Create the bar chart
    plt.figure(figsize=(10, 6))
    x = np.arange(len(input_sizes))  # the label locations
    width = 0.35  # the width of the bars
    plt.bar(x - width/2, hardware_cycles_os, width, label='OS Dataflow', color='blue')
    plt.bar(x + width/2, hardware_cycles_ws, width, label='WS Dataflow', color='orange')

    # plt.yscale('log')  # Make y-axis logarithmic
    # # Set more y-axis ticks
    # yticks = [1e6, 2e6, 5e6, 1e7, 2e7, 3e7, 5e7, 1e8]
    # plt.yticks(yticks, [f"{int(y):,}" for y in yticks])

    plt.xlabel('Operation | Input Tensor Size (XxYxC)')
    plt.ylabel('Clock Cycles')
    plt.title('Clock Cycles for a 16x16 systolic array Gemmini accelerator')
    plt.xticks(x, input_sizes)
    plt.legend()
    plt.tight_layout()
    plt.show()

def cumulative_bar_chart_resnet_block(systolic_array_size=16, conv3_ws_cycles_l0_l1_l2_l3=[15874989,0,0,0]):
    # we need data on GroupNorm cpu cycles vs hardware cycles
    input_sizes = ['Level0: 64x64x320', 'Level1: 32x32x640', 'Level2: 16x16x1280', 'Level3: 8x8x1280'] # Input tensor sizes for level 0, level 1 of UNET.
    cycles_silu_cpu = [1063292100, 52998050, 26469290, 6586850]
    cycles_gn_cpu = [153660170, 86730940, 38327380, 9563740]
    # Create the bar chart
    plt.figure(figsize=(8, 4))
    y = np.arange(len(input_sizes))
    width = 0.15  # the width of the bars
    plt.barh(y, conv3_ws_cycles_l0_l1_l2_l3, width, label=f'Weight-Stationary CONV3 on ${systolic_array_size}x${systolic_array_size} systolic array', color='blue')
    # Plot the second bar, stacked on top of the first
    plt.barh(y, cycles_silu_cpu, width, left=conv3_ws_cycles_l0_l1_l2_l3, label='SiLU on 1-core Rocket CPU', color='yellow')
    plt.barh(y, cycles_gn_cpu, width, left=cycles_silu_cpu, label='GroupNorm on 1-core Rocket CPU', color='orange')

    plt.ylabel('UNET Level: Input Tensor Size (XxYxC)')
    plt.xlabel('Clock Cycles')
    plt.title(f'Cumulative Clock Cycles for ResNet block layers')
    plt.yticks(y, input_sizes)
    plt.legend()
    plt.tight_layout()
    plt.show()

def cumulative_bar_chart_transfo_block(systolic_array_size=16, staticmm_ws_cycles_l0_l1_l2_l3 = [0,0,0,0], dynamicmm_attnV_ws_cycles_l0_l1_l2_l3=[0,0,0,0], dynamicmm_QKt_ws_cycles_l0_l1_l2_l3=[0,0,0,0]):
    # we need some data on the SoftMax, GELU activation and LayerNorm and GroupNorm CPU cycles, vs hardware cycles.
    # so we can compare those and say they dominate compared to the staticmm_ws_cycles
    input_sizes = ['Level0: 4096x40x4096', 'Level1: 1024x80x1024', 'Level2: 256x160x256', 'Level3: 64x160x64'] # Input tensor sizes for level 0,1,2,3 of UNET.
    total_dynamicmm_ws_cycles = [a + b for a, b in zip(dynamicmm_attnV_ws_cycles_l0_l1_l2_l3, dynamicmm_QKt_ws_cycles_l0_l1_l2_l3)]
    cycles_SoftMax_cpu = [0, 0, 0, 0]  # Placeholder for SoftMax CPU cycles, replace with actual data if available
    # Create the bar chart
    plt.figure(figsize=(8, 4))
    y = np.arange(len(input_sizes))
    width = 0.15  # the width of the bars
    plt.barh(y, staticmm_ws_cycles_l0_l1_l2_l3, width, label=f'Three Weight-Stationary static MatMuls on ${systolic_array_size}x${systolic_array_size} systolic array', color='blue')
    # Plot the second bar, stacked on top of the first
    plt.barh(y, total_dynamicmm_ws_cycles, width, label=f'Two Weight-Stationary dynamic MatMuls on ${systolic_array_size}x${systolic_array_size} systolic array', color='yellow')
    # Plot the third bar, stacked on top of the second
    plt.barh(y, cycles_SoftMax_cpu, width, left=total_dynamicmm_ws_cycles, label='SoftMax on CPU', color='orange')

    plt.ylabel('UNET Level: Input Tensor Size (XxYxC)')
    plt.xlabel('Clock Cycles')
    plt.title(f'Cumulative Clock Cycles for Transformer blocks at different UNET levels')
    plt.yticks(y, input_sizes)
    # place legend top right of plot
    plt.legend(loc='upper right')
    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    # bar_chart_silu_speedup()
    # bar_chart_conv_matmul()
    # cumulative_bar_chart_resnet_block(systolic_array_size=16, conv3_ws_cycles_l0_l1_l2_l3=[15874989,17004826,16289651,5354450])
    # cumulative_bar_chart_resnet_block(systolic_array_size=32, conv3_ws_cycles=[8447062,0,0,0])
    cumulative_bar_chart_transfo_block(systolic_array_size=16, staticmm_ws_cycles_l0_l1_l2_l3=[5795875, 5636573, 7168535, 1496028],
                                        dynamicmm_attnV_ws_cycles_l0_l1_l2_l3=[5719444,387302,45260,4853],
                                        dynamicmm_QKt_ws_cycles_l0_l1_l2_l3=[3835741,383162,45135,4941])
