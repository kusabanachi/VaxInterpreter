package vax_interpreter;

import java.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

abstract class NumData {
    protected byte[] bytes;
    protected DataType type;

    public byte[] bytes() {
        return bytes;
    }

    public DataType dataType() {
        return type;
    }

    public int size() {
        return type.size;
    }

    public String hexString() {
        StringBuilder sb = new StringBuilder("0x");
        for (int i = bytes.length - 1; i >= 0; i--) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }
}

class IntData extends NumData {
    public IntData(byte[] bytes, DataType tp) {
        this.bytes = Arrays.copyOf(bytes, tp.size);
        this.type = tp;
    }

    public IntData(int val, DataType tp) {
        this(intToBytes(val), tp);
    }

    public IntData(int val) {
        this(val, DataType.L);
    }

    public IntData(long val, DataType tp) {
        this(longToBytes(val), tp);
    }

    public IntData(long val) {
        this(val, DataType.Q);
    }

    public int sint() {
        return bytesToInt(bytes);
    }

    public int uint() {
        int mask = ~(int)(0xffffffffL << (type.size << 3));
        return sint() & mask;
    }

    public long slong() {
        return bytesToLong(bytes);
    }

    public static IntData bitInvert(IntData src) {
        byte[] invBytes = new byte[src.bytes.length];
        for (int i = 0; i < src.bytes.length; i++) {
            invBytes[i] = (byte)(~src.bytes[i]);
        }
        return new IntData(invBytes, src.type);
    }

    public boolean isNegValue() {
        return (bytes[bytes.length - 1] & 0x80) != 0;
    }

    public boolean isZeroValue() {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isLargestNegativeInteger() {
        if ((bytes[bytes.length - 1] & 0xff) != 0x80) {
            return false;
        }
        for (int i = 0; i < bytes.length - 1; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static int bytesToInt(byte[] bytes) {
        ByteBuffer bbuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        switch (bytes.length) {
        case 1:
            return bbuf.get();
        case 2:
            return bbuf.getShort();
        default:
            return bbuf.getInt();
        }
    }

    private static long bytesToLong(byte[] bytes) {
        ByteBuffer bbuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        switch (bytes.length) {
        case 1:
            return bbuf.get();
        case 2:
            return bbuf.getShort();
        case 4:
            return bbuf.getInt();
        default:
            return bbuf.getLong();
        }
    }

    private static byte[] intToBytes(int val) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(val).array();
    }

    private static byte[] longToBytes(long val) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(val).array();
    }
}

class FloatData extends NumData {
    public FloatData(byte[] bytes, DataType type) {
        this.bytes = Arrays.copyOf(bytes, type.size);
        this.type = type;
    }

    public FloatData(double val, DataType type) {
        long longBits = Double.doubleToLongBits(val);
        long sign = longBits >> 63;
        long srcExponent = (longBits >> 52 & 0x7ff) - 1023;
        long significand = longBits & 0xfffffffffffffL;
        byte[] bytes;
        if (srcExponent == 0 && significand == 0) {
            bytes = new byte[type.size];
        } else {
            switch (type) {
            case F:
                long fexp = (srcExponent + 1 + 128) & 0xff;
                int fval = (int)(sign << 15 |
                                 fexp << 7 |
                                 significand >> 45 & 0x7f |
                                 significand >> 13 & 0xffff0000);
                bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(fval).array();
                break;
            case D:
                long dexp = (srcExponent + 1 + 128) & 0xff;
                long dval = sign << 15 |
                    dexp << 7 |
                    significand >> 45 & 0x7f |
                    significand >> 13 & 0xffff0000 |
                    significand << 19 & 0xffff00000000L |
                    significand << 51 & 0xffff000000000000L;
                bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(dval).array();
                break;
            case G:
                long gexp = (srcExponent + 1 + 1024) & 0x7ff;
                long gval = sign << 15 |
                    gexp << 4 |
                    significand >> 48 & 0xf |
                    significand >> 16 & 0xffff0000 |
                    significand << 16 & 0xffff00000000L |
                    significand << 48 & 0xffff000000000000L;
                bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(gval).array();
                break;
            case H:
                long hexp = (srcExponent + 1 + 16384) & 0x7fff;
                long hlower = sign << 15 |
                    hexp |
                    significand >> 20 & 0xffff0000 |
                    significand << 12 & 0xffff00000000L |
                    significand << 44 & 0xffff000000000000L;
                long hupper = significand << 12 & 0xffff;
                bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putLong(hlower).putLong(hupper).array();
                break;
            default:
                throw new RuntimeException("Invalid floating-point type");
            }
        }
        this.bytes = bytes;
        this.type = type;
    }

    public static FloatData negativeFloat(FloatData src) {
        byte[] negBytes = Arrays.copyOf(src.bytes, src.bytes.length);
        if (!src.isZeroValue()) {
            negBytes[1] ^= 0x80;
        }
        return new FloatData(negBytes, src.type);
    }

    public boolean isNegValue() {
        return (bytes[1] & 0x80) != 0;
    }

    public boolean isZeroValue() {
        long s_exp;
        switch (type) {
        case F:
        case D:
            s_exp = (bytes[0] | bytes[1] << 8) >> 7;
            break;
        case G:
            s_exp = (bytes[0] | bytes[1] << 8) >> 4;
            break;
        case H:
            s_exp = (bytes[0] | bytes[1] << 8);
            break;
        default:
            throw new RuntimeException("Invalid floating-point type");
        }
        return s_exp == 0;
    }

    public boolean isMinusZeroFloatValue() {
        return isNegValue() && isZeroValue();
    }
}
