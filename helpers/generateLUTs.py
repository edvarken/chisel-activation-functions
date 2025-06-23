import math
import numpy as np
import struct

def printIndexedFunctionTableExtensive(function="silu", intBits=2, fracBits=4):
    error_tolerance = 0.032
    if intBits == 2 and fracBits == 4: # -4 to +4 with a step of 0.0625; 128 entries total
        error_tolerance = 0.016
    elif intBits == 2 and fracBits == 5: # -4 to +4 with a step of 0.03125; 256 entries total
        error_tolerance = 0.016
    elif intBits == 3 and fracBits == 4: # -8 to +8 with a step of 0.0625; 256 entries total
        error_tolerance = 0.032
    elif intBits == 3 and fracBits == 5: # -8 to +8 with a step of 0.03125; 512 entries total
        error_tolerance = 0.0312

    step = float(pow(2, -fracBits))
    max = float(pow(2, intBits))
    min = -max + step
    # heading for the table
    if function == "silu":
        print("(j        index      silu       silu_bf16         silu_bf16_float  error)")
    elif function == "tanh":
        print("(j        index      tanh       tanh_bf16         tanh_bf16_float  error)")
    elif function == "gelu":
        print("(j        index      gelu       gelu_bf16         gelu_bf16_float  error)")
    else:
        raise ValueError("Unsupported function. Use 'silu', 'tanh', or 'gelu'.")

    for j in np.arange(min, max, step): # from -3.9375 up to 4.0 with a step of 0.0625 for intBits=2 and fracBits=4
        if function == "silu":
            function_float = round(j/(1+math.exp(-j)), (fracBits+intBits))
        elif function == "tanh":
            function_float = round(math.tanh(j), (fracBits+intBits))
        elif function == "gelu":
            # GELU approximation: x * 0.5 * (1 + math.tanh((math.sqrt(2 / math.pi) * (x + 0.044715 * x**3))))
            # exact GELU:
            function_float = round(j*0.5*(1+math.erf(j/math.sqrt(2))), (fracBits+intBits))
        else:
            raise ValueError("Unsupported function. Use 'silu', 'tanh', or 'gelu'.")

        j_float = f"{j:.4f}" # round to 4 decimal places
        # Convert float32 to 4-byte representation (big-endian)
        function_bytes = struct.pack('>f', np.float32(function_float))
        # Take the first 2 bytes (most significant bits) for BF16
        function_bf16_bytes = function_bytes[:2]
        # Convert to bit string
        function_bf16_bits = ''.join(f'{byte:08b}' for byte in function_bf16_bytes)
        # Convert the bf16 back to its float representation
        function_bf16_int = int(function_bf16_bits, 2) << 16  # Shift back to 32-bit float position
        function_bf16_float = struct.unpack('>f', struct.pack('>I', function_bf16_int))[0]
        # difference between function_float and function_bf16_float
        diff = function_float - function_bf16_float
        assert(abs(diff) < error_tolerance), f"Error too large: {diff:.4f} for j={j_float}, {function}_float={function_float}, {function}_bf16_float={function_bf16_float}"

        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        # Print everything
        print(f"({j_float}, {int(j < 0)}_{int(abs(j)):0{intBits}b}.{frac_part:0{fracBits}b}, {function_float:.6f}, {function_bf16_bits}, {function_bf16_float:.6f},       {diff:.4f})")


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


def printOrderedIndexedFunctionTableInChiselSyntax(function="silu", intBits=2, fracBits=4):
    step = float(pow(2, -fracBits))
    max = float(pow(2, intBits))
    for j in np.arange(0.0000, max, +step):
        if function == "silu":
            function_float = round(j/(1+math.exp(-j)), (fracBits+intBits))
        elif function == "tanh":
            function_float = round(math.tanh(j), (fracBits+intBits))
        elif function == "gelu":
            function_float = round(j*0.5*(1+math.erf(j/math.sqrt(2))), (fracBits+intBits))
        # Convert float32 to 4-byte representation (big-endian)
        function_bytes = struct.pack('>f', np.float32(function_float))
        # Take the first 2 bytes (most significant bits) for BF16
        function_bf16_bytes = function_bytes[:2]
        # Convert to bit string
        function_bf16_bits = ''.join(f'{byte:08b}' for byte in function_bf16_bytes)
        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        print(f"\"b{function_bf16_bits}\".U,")
    print("\"b0000000000000000\".U,") # add zero entry for -0.0
    for j in np.arange(-step, -max, -step):
        if function == "silu":
            function_float = round(j/(1+math.exp(-j)), (fracBits+intBits))
        elif function == "tanh":
            function_float = round(math.tanh(j), (fracBits+intBits))
        elif function == "gelu":
            function_float = round(j*0.5*(1+math.erf(j/math.sqrt(2))), (fracBits+intBits))
        # Convert float32 to 4-byte representation (big-endian)
        function_bytes = struct.pack('>f', np.float32(function_float))
        # Take the first 2 bytes (most significant bits) for BF16
        function_bf16_bytes = function_bytes[:2]
        # Convert to bit string
        function_bf16_bits = ''.join(f'{byte:08b}' for byte in function_bf16_bytes)
        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        print(f"\"b{function_bf16_bits}\".U,")


def printIndices(intBits=2, fracBits=4):
    step = float(pow(2, -fracBits))
    max = float(pow(2, intBits))
    min = -max + step
    for j in np.arange(min, max, +step): # -3.9375 to 4.0 with a step of 0.0625 for intBits=2 and fracBits=4
        frac_part = int(abs(j) * 2**fracBits) & ((1 << fracBits) - 1)
        print(f"({j}, {int(j < 0)}_{int(abs(j)):0{intBits}b}.{frac_part:0{fracBits}b})")

if __name__ == "__main__":
    # printIndexedFunctionTableExtensive(function="gelu", intBits=2, fracBits=4)

    # printIndexedSiluTableSimple()

    printOrderedIndexedFunctionTableInChiselSyntax(function="gelu", intBits=3, fracBits=5)
    
    # printIndices(intBits=2, fracBits=4)
