import sys
import struct
def BF16_to_float(bitstring):
    # Convert the 16-bit BF16 bitstring to a 32-bit float
    if isinstance(bitstring, str):
        bitstring = int(bitstring, 2)
    bf16_bytes = struct.pack('>H', bitstring)  # Pack as big-endian unsigned short
    float_bytes = bf16_bytes + b'\x00\x00'  # Append two zero bytes to make it 4 bytes
    return struct.unpack('>f', float_bytes)[0]  # Unpack as big-endian float



def float_to_BF16(floatValue):
    # Convert a float to its BF16 representation
    float_bytes = struct.pack('>f', floatValue)  # Pack as big-endian float
    bf16_bytes = float_bytes[:2]  # Take the first 2 bytes (most significant bits)
    return int.from_bytes(bf16_bytes, 'big')  # Convert to integer

    
if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python bf16PrintFloat.py <toFloat/toBF16> <16-bit BF16 bitstring>")
        sys.exit(1)

    if sys.argv[1] == "toFloat":
        bitstring = sys.argv[2]
        if len(bitstring) != 16 or not all(c in '01' for c in bitstring):
            print("Error: Input must be a 16-bit binary string.")
            sys.exit(1)
        float_value = BF16_to_float(bitstring)
        print(f"BF16: {bitstring} -> Float: {float_value}")
    
    elif sys.argv[1] == "toBF16":
        float_string = sys.argv[2]
        try:
            float = float(float_string)
        except ValueError:
            print("Error: Input must be a valid floating-point number string.")
            sys.exit(1)
        BF16_value = float_to_BF16(float)
        print(f"Float: {float} -> BF16: {BF16_value:016b}")
    else:
        print("Error: Invalid command. Use 'toFloat <BF16 bitstring>' or 'toBF16 <float>'.")
        sys.exit(1)