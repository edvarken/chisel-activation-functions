import math
import numpy as np
import struct

def printIndexedSiluTableExtensive(intBits=2, fracBits=4):
    error_tolerance = 0.02
    # heading for the table
    print("(j        index      silu       silu_bf16         silu_bf16_float  error)")
    for j in np.arange(-3.9375, 4.0, 0.0625): # -3.9375
        silu_float = round(j/(1+math.exp(-j)), 6)
        j_float = f"{j:.4f}" # round to 4 decimal places

        # Convert float32 to 4-byte representation (big-endian)
        silu_bytes = struct.pack('>f', np.float32(silu_float))
        # Take the first 2 bytes (most significant bits) for BF16
        silu_bf16_bytes = silu_bytes[:2]
        # Convert to bit string
        silu_bf16_bits = ''.join(f'{byte:08b}' for byte in silu_bf16_bytes)
        # Convert the bf16 back to its float representation
        silu_bf16_int = int(silu_bf16_bits, 2) << 16  # Shift back to 32-bit float position
        silu_bf16_float = struct.unpack('>f', struct.pack('>I', silu_bf16_int))[0]
        # difference between silu_float and silu_bf16_float
        diff = silu_float - silu_bf16_float
        assert(abs(diff) < error_tolerance), f"Error too large: {diff:.4f} for j={j_float}, silu_float={silu_float}, silu_bf16_float={silu_bf16_float}"

        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        # Print everything
        print(f"({j_float}, {int(j < 0)}_{int(abs(j)):0{intBits}b}.{frac_part:0{fracBits}b}, {silu_float:.6f}, {silu_bf16_bits}, {silu_bf16_float:.6f},       {diff:.4f})")

def printIndexedSiluTableSimple(intBits=2, fracBits=4):
    # heading for the table
    print("(index      silu_bf16)")
    for j in np.arange(-3.9375, 4.0, 0.0625): # -3.9375
        silu_float = round(j/(1+math.exp(-j)), 6)

        # Convert float32 to 4-byte representation (big-endian)
        silu_bytes = struct.pack('>f', np.float32(silu_float))
        # Take the first 2 bytes (most significant bits) for BF16
        silu_bf16_bytes = silu_bytes[:2]
        # Convert to bit string
        silu_bf16_bits = ''.join(f'{byte:08b}' for byte in silu_bf16_bytes)

        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)

        print(f"({int(j < 0)}{int(abs(j)):0{intBits}b}{frac_part:0{fracBits}b}, {silu_bf16_bits})")


def printIndices(intBits, fracBits):
    for j in np.arange(-3.9375, 4.0, 0.0625): # -3.9375
        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        print(f"({j}, {int(j < 0)}_{int(abs(j)):0{intBits}b}.{frac_part:0{fracBits}b})")

if __name__ == "__main__":
    # printIndexedSiluTableExtensive()
    printIndexedSiluTableSimple()
    # printIndices(intBits=2, fracBits=4)
