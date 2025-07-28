import math
import numpy as np
import struct
from typing import List



# calculate the unit in last place for every same-exponent segment of brainfloat16 numbers(8bit exponent, 7bit mantissa) between 0 and 8.
def calculate_ulp_brainfloat16(start: float, end: float, num) -> List[float]:
    ulps = []
    for i in np.linspace(start, end, num):
        # Convert the float to brainfloat16 representation
        exp = math.floor(math.log2(abs(i)))
        # bf16 = struct.pack('>e', i)
        # exp = struct.unpack('>H', bf16)[0] >> 7  # Extract exponent bits
        # mantissa = struct.unpack('>H', bf16)[0] & 0x3FF  # Extract mantissa bits
        ulp = 2 ** exp * 2 ** (-7)  # Calculate ULP
        ulps.append(ulp)
    # for exp in range(-126, 2): # inputs range from [2^-126 * (1+2^-7), 8) 8 exclusive
    #     # Calculate the ULP for every same-exponent segment
    #     ulp = 2 ** exp * 2**(-7)
    #     # Append the ULP to the list if it's within the range
    #     ulps.append(ulp)
    average_ulp = sum(ulps) / len(ulps)  # Calculate the average ULP
    return ulps, average_ulp

# # calculate the unit in last place for every same-exponent segment of float16 numbers(5bit exponent, 10bit mantissa) between 0 and 8.
# def calculate_ulp_float16(start: float, end: float) -> List[float]:
#     ulps = []
#     average_ulp_divided_by_segment_width = [] # calculate the average ULP in the range [0,8], but keep in mind that some segments are wider than others
#     for exp in range(-30, 2): # inputs range from [2^-30 * (1+2^-11), 8) 8 exclusive
#         # Calculate the ULP for every same-exponent segment
#         ulp = 2 ** exp * 2**(-7)
#         # Append the ULP to the list if it's within the range
#         ulps.append(ulp)
#     average_ulp = sum(ulps) / len(ulps)  # Calculate the average ULP
#     return ulps, average_ulp

if __name__ == "__main__":
    start = 1.0
    end = 2.0
    num = 200
    ulps, average_ulp = calculate_ulp_brainfloat16(start, end, num)
    print(f"ULPs in the range [{start}, {end}]")
    print(f"Average ULP: {average_ulp}")
    print("len:", len(ulps))

