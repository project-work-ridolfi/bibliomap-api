package it.unipegaso.service.otp;

public class Base32StringEncoding implements IBaseStringEncoding {

    private static final String base32Chars =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] base32Lookup =
    {
        0xFF, 0xFF, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, // '0', '1', '2', '3', '4', '5', '6', '7'
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // '8', '9', ':', ';', '<', '=', '>', '?'
        0xFF, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, // '@', 'A', 'B', 'C', 'D', 'E', 'F', 'G'
        0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, // 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O'
        0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, // 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W'
        0x17, 0x18, 0x19, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // 'X', 'Y', 'Z', '[', '\', ']', '^', '_'
        0xFF, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, // '`', 'a', 'b', 'c', 'd', 'e', 'f', 'g'
        0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, // 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o'
        0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, // 'p', 'q', 'r', 's', 't', 'u', 'v', 'w'
        0x17, 0x18, 0x19, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF  // 'x', 'y', 'z', '{', '|', '}', '~', 'DEL'
    };

    @Override
    public byte[] decode(String encoded) throws DecodingException {
        if (encoded == null) {
            return null;
        }
        if (encoded.length() == 0) {
            return new byte[0];
        }
        encoded = encoded.toUpperCase();

        byte[] b = new byte[encoded.length() * 5 / 8];
        int bIndex = 0;
        int i = 0;
        int lookup;
        int nextByte = 0;
        int bitsLeft = 8;

        while (i < encoded.length()) {
            char c = encoded.charAt(i);
            if (c >= '0' && c <= 'Z') {
                lookup = base32Lookup[c - '0'];
                if (lookup != 0xFF) {
                    nextByte |= lookup << (bitsLeft - 5);
                    bitsLeft -= 5;
                    if (bitsLeft <= 0) {
                        b[bIndex++] = (byte) nextByte;
                        nextByte = 0;
                        bitsLeft += 8;
                    }
                }
                i++;
            } else {
                throw new DecodingException("Invalid character in Base32 string.");
            }
        }

        byte[] result = new byte[bIndex];
        System.arraycopy(b, 0, result, 0, bIndex);
        return result;
    }

    @Override
    public String encode(byte[] data) {
        if (data == null) {
            return null;
        }
        if (data.length == 0) {
            return "";
        }

        int i = 0, index = 0, digit = 0;
        int currByte, nextByte;
        StringBuilder base32 = new StringBuilder((data.length + 7) * 8 / 5);

        while (i < data.length) {
            currByte = (data[i] >= 0) ? data[i] : (data[i] + 256); // unsign

            if (index > 3) {
                if ((i + 1) < data.length) {
                    nextByte = (data[i + 1] >= 0) ? data[i + 1] : (data[i + 1] + 256);
                } else {
                    nextByte = 0;
                }

                digit = currByte & (0xFF >> index);
                index = (index + 5) % 8;
                digit <<= index;
                digit |= nextByte >> (8 - index);
                i++;
            } else {
                digit = (currByte >> (8 - (index + 5))) & 0x1F;
                index = (index + 5) % 8;
                if (index == 0) {
                    i++;
                }
            }
            base32.append(base32Chars.charAt(digit));
        }

        return base32.toString();
    }
}
