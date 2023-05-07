package top.dreamlike.base.util;

public class Util {

    public static int fromByteArray(byte[] bytes, int offset) {

        return ((bytes[0 + offset] & 0xFF) << 24) |
                ((bytes[1 + offset] & 0xFF) << 16) |
                ((bytes[2 + offset] & 0xFF) << 8) |
                ((bytes[3 + offset] & 0xFF) << 0);
    }
}
