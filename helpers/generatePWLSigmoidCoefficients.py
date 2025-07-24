import struct
import numpy as np
import math
# This file generates PWL (1st order approximation) coefficients for approximating segments ofthe sigmoid function:
#
#                                         y
#                                      1.0|                     **************         
#                                      0.9|           **********                  
#                                      0.8|      *****                 
#                                      0.7|  ****                    
#                                      0.6| *                
#                                      0.5|
#                                     *0.4|                 
#                                  *** 0.3|                         
#                             *****    0.2|                         
#                   **********         0.1|                            
#     **************                   0.0|                    
#     ------------------------------------|------------------------------------ x
#     -4       -3       -2       -1       0        1        2        3        4
#
#
# Zoomed-in view for y-values between 0.5 and 1.0:
#  1.00   |                           
#         |                                              *****************
#         |                             ***************** 
#  0.98 __|                *************       
#         |             ***      
#       __|           **         
#         |          *          
#       __|         *              
#         |        *                      
#       __|       *                            
#         |      *              
#       __|     *                  
#         |    *                            
#       __|   *                      
#         |  *                          
#       __| *                           
#         | *                       
#       __|*                                  
#         |*
#  0.50 __|----------------------------------------------------------------
#         0       1       2       3       4       5       6       7       8
#                                         |       |       |              
#
# 8 segments: equally spaced y-values between 0.500000 and 0.982014
# 2 segments: equally spaced x-values between 4 and 6
# each segment is linearly interpolated as y = m*x + q, using the local slope m and y-intercept q, which are saved in a LUT: storing 10*2=20 values

def createBreakpoints(min, max, step, function):
    breakpoints = []
    # Create breakpoints for the sigmoid function
    j= min
    while j < max+step:
        if function ==  "sigmoid-uniform-x":
            function_float = 1 / (1 + math.exp(-j))
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
        breakpoints.append((round(j,14), round(function_bf16_float, 14))) # round to 9 decimal places
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


def printBF16AndFPValues(values):
    for i in range(len(values)):
        try:
            x0, y0 = values[i]
        except TypeError:
            x0 = values[i]
        # Convert float32 to 4-byte representation (big-endian)
        value_bytes = struct.pack('>f', np.float32(x0))
        # Take the first 2 bytes (most significant bits) for BF16, but round-to-nearest-and-to-even-if-tied 
        value_bf16_bytes = mantissaRounder(value_bytes)
        # Convert to bit string
        value_bf16_bits = ''.join(f'{byte:08b}' for byte in value_bf16_bytes)
        # convert the bf16 bits to an integer representation with 3 integer bits and 9 fractional bits
        value_FP3intfrac9 = f"{int(x0):03b}_{int((x0 - int(x0)) * 2**9):09b}"
        # Convert the bf16 back to its float representation
        function_bf16_int = int(value_bf16_bits, 2) << 16  # Shift back to 32-bit float position
        function_bf16_float = struct.unpack('>f', struct.pack('>I', function_bf16_int))[0]
        # Print the values
        print(f"({x0:.6f}, {i + 1}, {value_bf16_bits}, {value_FP3intfrac9}, {function_bf16_float:.6f})")

def printSegmentsStuff(n_equalYsegments, breakpointsEqualYsegmentsBF16, breakpointsEqualXsegments):
    FirstEightSegmentsDerivatives, FirstEightSegmentsYIntercepts, FirstEightSegmentsmirrored_y_intercepts = calculateSlopesAndYIntercepts(breakpointsEqualYsegmentsBF16)
    print(f"First {n_equalYsegments} segments derivatives (equal y segments):", [round(m, 6) for m in FirstEightSegmentsDerivatives])
    print(f"First {n_equalYsegments} segments y-intercepts (equal y segments):", [round(q, 6) for q in FirstEightSegmentsYIntercepts])
    print(f"First {n_equalYsegments} segments mirrored y-intercepts (equal y segments):", [round(q, 6) for q in FirstEightSegmentsmirrored_y_intercepts])
    SecondTwoSegmentsDerivatives, SecondTwoSegmentsYIntercepts, dummy = calculateSlopesAndYIntercepts(breakpointsEqualXsegments)
    print("Second 2 segments derivatives (equal x segments):", [round(m, 6) for m in SecondTwoSegmentsDerivatives])
    print("Second 2 segments y-intercepts (equal x segments):", [round(q, 6) for q in SecondTwoSegmentsYIntercepts])
    print(f"\nBF16 and FP values for the first {n_equalYsegments} segments: slopes")
    printBF16AndFPValues(FirstEightSegmentsDerivatives)
    print(f"\nBF16 and FP values for the first {n_equalYsegments} segments: y-intercepts:")
    printBF16AndFPValues(FirstEightSegmentsYIntercepts)
    print(f"\nBF16 and FP values for the first {n_equalYsegments} segments: mirrored y-intercepts:")
    printBF16AndFPValues(FirstEightSegmentsmirrored_y_intercepts)
    print("\nBF16 and FP values for the second 2 segments: slopes:")
    printBF16AndFPValues(SecondTwoSegmentsDerivatives)
    print("\nBF16 and FP values for the second 2 segments: y-intercepts:")
    printBF16AndFPValues(SecondTwoSegmentsYIntercepts)


if __name__ == "__main__":
    breakpoints2EqualXsegments = [(4, 0.982014), (5, 0.993307), (6, 0.997527)]
    # printBF16AndFPValues(breakpoints2EqualXsegments)

    #################################  10 segments total ######################################
    # breakpoints8EqualYsegments = [(0, 0.500000), (0.242184, 0.560252), (0.491686, 0.620503), (0.757244, 0.680755),
                                #   (1.051208, 0.741007), (1.394179, 0.801259), (1.827891, 0.861510), (2.466533, 0.921762), (4.000000, 0.982014)]
    breakpoints8EqualYsegmentsBF16 = [(0, 0.500000), (0.242188, 0.560253), (0.492188, 0.620622), (0.757812, 0.680879),
                                      (1.054688, 0.741674), (1.390625, 0.800692), (1.828125, 0.861538), (2.468750, 0.921922), (4.000000, 0.982014)]
    # printSegmentsStuff(8, breakpoints8EqualYsegmentsBF16, breakpoints2EqualXsegments)
    

    #################################  18 segments total  ######################################
    # breakpoints16EqualYsegments = [(0, 0.500000), (0.120650, 0.530126), (0.242184, 0.560252), (0.365526, 0.590378), (0.491686, 0.620503), (0.621807, 0.650629), (0.757244, 0.680755),
                                #   (0.899667, 0.710881), (1.051208, 0.741007), (1.214718, 0.771133), (1.394179, 0.801259), (1.595471, 0.831384), (1.827891, 0.861510), (2.107564, 0.891636),
                                    # (2.466533, 0.921762), (2.984914, 0.951888), (4.000000, 0.982014)]
    breakpoints16EqualYsegmentsBF16 = [(0, 0.500000), (0.120605, 0.530115), (0.242188, 0.560253), (0.365234, 0.590307), (0.492188, 0.620622), (0.621094, 0.650467), (0.757812, 0.680879),
                                  (0.898438, 0.710628), (1.054688, 0.741674), (1.210938, 0.770465), (1.390625, 0.800692), (1.593750, 0.831143), (1.828125, 0.861538), (2.109375, 0.891811),
                                    (2.468750, 0.921922), (2.984375, 0.951863), (4.000000, 0.982014)]
    # printBF16AndFPValues(breakpoints16EqualYsegmentsBF16)
    # printSegmentsStuff(16, breakpoints16EqualYsegmentsBF16, breakpoints2EqualXsegments)

    #################################  20 segments total, 16 and 4 segments  ######################################
    # breakpoints4EqualXsegments = [(4, 0.982014), (4.5, 0.989013), (5, 0.993307), (5.5, 0.995930), (6, 0.997527)]
    # printBF16AndFPValues(breakpoints4EqualXsegments)
    # SecondFourSegmentsDerivatives, SecondFourSegmentsYIntercepts, SecondFourSegmentsmirrored_y_intercepts = calculateSlopesAndYIntercepts(breakpoints4EqualXsegments)
    # print("Second 4 segments derivatives (equal x segments):", [round(m, 6) for m in SecondFourSegmentsDerivatives])
    # print("Second 4 segments y-intercepts (equal x segments):", [round(q, 6) for q in SecondFourSegmentsYIntercepts])
    # print("\nBF16 and FP values for the second 4 segments: slopes:")
    # printBF16AndFPValues(SecondFourSegmentsDerivatives)
    # print("\nBF16 and FP values for the second 4 segments: y-intercepts:")
    # printBF16AndFPValues(SecondFourSegmentsYIntercepts)
    # print("\nBF16 and FP values for the second 4 segments: mirrored y-intercepts:")
    # printBF16AndFPValues(SecondFourSegmentsmirrored_y_intercepts)

    breakpoints16EqualYsegmentsBF16 = [(0, 0.500000), (0.120605, 0.530115), (0.242188, 0.560253), (0.365234, 0.590307), (0.492188, 0.620622), (0.621094, 0.650467), (0.757812, 0.680879),
                                  (0.898438, 0.710628), (1.054688, 0.741674), (1.210938, 0.770465), (1.390625, 0.800692), (1.593750, 0.831143), (1.828125, 0.861538), (2.109375, 0.891811),
                                    (2.468750, 0.921922), (2.984375, 0.951863), (4.000000, 0.982014)]
    # printBF16AndFPValues(breakpoints16EqualYsegmentsBF16)
    # printSegmentsStuff(16, breakpoints16EqualYsegmentsBF16, breakpoints2EqualXsegments)

    #################################  20 segments total, first 4 then 16 segments  ######################################
    breakpoints4EqualXsegments = createBreakpoints(0, 2, 0.5, "sigmoid-uniform-x") # 5 brkpts
    breakpoints16EqualXsegments = createBreakpoints(2, 6, (6-2)/16, "sigmoid-uniform-x") # 17 brkpts
    slopes, y_intercepts, mirrored_y_intercepts = calculateSlopesAndYIntercepts(breakpoints4EqualXsegments)
    printBF16AndFPValues(breakpoints4EqualXsegments)
    print(f"\nBF16 and FP values for the first 4 segments: slopes")
    printBF16AndFPValues(slopes)
    print(f"\nBF16 and FP values for the first 4 segments: y-intercepts:")
    printBF16AndFPValues(y_intercepts)
    print(f"\nBF16 and FP values for the first 4 segments: mirrored y-intercepts:")
    printBF16AndFPValues(mirrored_y_intercepts)
    print("\n=========================================================\n")
    slopes, y_intercepts, mirrored_y_intercepts = calculateSlopesAndYIntercepts(breakpoints16EqualXsegments)
    printBF16AndFPValues(breakpoints16EqualXsegments)
    print("\nBF16 and FP values for the second 16 segments: slopes:")
    printBF16AndFPValues(slopes)
    print("\nBF16 and FP values for the second 16 segments: y-intercepts:")
    printBF16AndFPValues(y_intercepts)
    print("\nBF16 and FP values for the second 16 segments: mirrored y-intercepts:")
    printBF16AndFPValues(mirrored_y_intercepts)
