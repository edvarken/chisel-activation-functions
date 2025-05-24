import math
import numpy as np
import struct

def printIndexedSiluTableExtensive(intBits=2, fracBits=4):
    error_tolerance = 0.032
    step = float(pow(2, -fracBits))
    max = float(pow(2, intBits))
    min = -max + step
    # heading for the table
    print("(j        index      silu       silu_bf16         silu_bf16_float  error)")
    for j in np.arange(min, max, step): # from -3.9375 up to 4.0 with a step of 0.0625 for intBits=2 and fracBits=4
        silu_float = round(j/(1+math.exp(-j)), (fracBits+intBits))
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
    step = float(pow(2, -fracBits))
    max = float(pow(2, intBits))
    min = -max + step
    # heading for the table
    print("(index      silu_bf16)")
    for j in np.arange(min, max, step): # -3.9375 up to +3.9375
        silu_float = round(j/(1+math.exp(-j)), 6)
        # Convert float32 to 4-byte representation (big-endian)
        silu_bytes = struct.pack('>f', np.float32(silu_float))
        # Take the first 2 bytes (most significant bits) for BF16
        silu_bf16_bytes = silu_bytes[:2]
        # Convert to bit string
        silu_bf16_bits = ''.join(f'{byte:08b}' for byte in silu_bf16_bytes)
        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        print(f"({int(j < 0)}{int(abs(j)):0{intBits}b}{frac_part:0{fracBits}b}, {silu_bf16_bits})")


def printOrderedIndexedSiluTableInChiselSyntax(intBits=2, fracBits=4):
    step = float(pow(2, -fracBits))
    max = float(pow(2, intBits))
    for j in np.arange(0.0000, max, +step):
        silu_float = round(j/(1+math.exp(-j)), 6)
        # Convert float32 to 4-byte representation (big-endian)
        silu_bytes = struct.pack('>f', np.float32(silu_float))
        # Take the first 2 bytes (most significant bits) for BF16
        silu_bf16_bytes = silu_bytes[:2]
        # Convert to bit string
        silu_bf16_bits = ''.join(f'{byte:08b}' for byte in silu_bf16_bytes)
        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        print(f"\"b{silu_bf16_bits}\".U,")
    print("\"b0000000000000000\".U,") # add zero entry for -0.0
    for j in np.arange(-step, -max, -step):
        silu_float = round(j/(1+math.exp(-j)), 6)
        # Convert float32 to 4-byte representation (big-endian)
        silu_bytes = struct.pack('>f', np.float32(silu_float))
        # Take the first 2 bytes (most significant bits) for BF16
        silu_bf16_bytes = silu_bytes[:2]
        # Convert to bit string
        silu_bf16_bits = ''.join(f'{byte:08b}' for byte in silu_bf16_bytes)
        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        print(f"\"b{silu_bf16_bits}\".U,")


def printIndexedTanhTableExtensive(intBits=2, fracBits=4):
    error_tolerance = 0.02
    step = float(pow(2, -fracBits))
    max = float(pow(2, intBits))
    min = -max + step
    # heading for the table
    print("(j        index      Tanh       Tanh_bf16         Tanh_bf16_float  error)")
    for j in np.arange(min, max, step): # -3.9375
        tanh_float = round(math.tanh(j), 6)
        j_float = f"{j:.4f}" # round to 4 decimal places

        # Convert float32 to 4-byte representation (big-endian)
        tanh_bytes = struct.pack('>f', np.float32(tanh_float))
        # Take the first 2 bytes (most significant bits) for BF16
        tanh_bf16_bytes = tanh_bytes[:2]
        # Convert to bit string
        tanh_bf16_bits = ''.join(f'{byte:08b}' for byte in tanh_bf16_bytes)
        # Convert the bf16 back to its float representation
        tanh_bf16_int = int(tanh_bf16_bits, 2) << 16  # Shift back to 32-bit float position
        tanh_bf16_float = struct.unpack('>f', struct.pack('>I', tanh_bf16_int))[0]
        # difference between silu_float and silu_bf16_float
        diff = tanh_float - tanh_bf16_float
        assert(abs(diff) < error_tolerance), f"Error too large: {diff:.4f} for j={j_float}, tanh_float={tanh_float}, tanh_bf16_float={tanh_bf16_float}"

        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        # Print everything
        print(f"({j_float}, {int(j < 0)}_{int(abs(j)):0{intBits}b}.{frac_part:0{fracBits}b}, {tanh_float:.6f}, {tanh_bf16_bits}, {tanh_bf16_float:.6f},       {diff:.4f})")


def printOrderedIndexedTanhTableInChiselSyntax(intBits=2, fracBits=4):
    step = float(pow(2, -fracBits))
    max = float(pow(2, intBits))
    for j in np.arange(0.0000, max, +step):
        tanh_float = round(math.tanh(j), 6)
        # Convert float32 to 4-byte representation (big-endian)
        tanh_bytes = struct.pack('>f', np.float32(tanh_float))
        # Take the first 2 bytes (most significant bits) for BF16
        tanh_bf16_bytes = tanh_bytes[:2]
        # Convert to bit string
        tanh_bf16_bits = ''.join(f'{byte:08b}' for byte in tanh_bf16_bytes)
        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        print(f"\"b{tanh_bf16_bits}\".U,")
    print("\"b0000000000000000\".U,") # add zero entry for -0.0
    for j in np.arange(-step, -max, -step):
        tanh_float = round(math.tanh(j), 6)
        # Convert float32 to 4-byte representation (big-endian)
        tanh_bytes = struct.pack('>f', np.float32(tanh_float))
        # Take the first 2 bytes (most significant bits) for BF16
        tanh_bf16_bytes = tanh_bytes[:2]
        # Convert to bit string
        tanh_bf16_bits = ''.join(f'{byte:08b}' for byte in tanh_bf16_bytes)
        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        print(f"\"b{tanh_bf16_bits}\".U,")


def printIndices(intBits=2, fracBits=4):
    step = float(pow(2, -fracBits))
    max = float(pow(2, intBits))
    min = -max + step
    for j in np.arange(min, max, +step): # -3.9375 to 4.0 with a step of 0.0625 for intBits=2 and fracBits=4
        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        print(f"({j}, {int(j < 0)}_{int(abs(j)):0{intBits}b}.{frac_part:0{fracBits}b})")

if __name__ == "__main__":
    printIndexedSiluTableExtensive(intBits=3, fracBits=4)
    # printIndexedSiluTableSimple()
    # printOrderedIndexedSiluTableInChiselSyntax(intBits=3, fracBits=4)

    # printIndexedTanhTableExtensive()
    # printOrderedIndexedTanhTableInChiselSyntax()
    
    # printIndices(intBits=2, fracBits=4)
