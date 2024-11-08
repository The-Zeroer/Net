package client.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TOOL {

    public static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String getHashValue(byte[] data, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(data);
        byte[] hashValue = md.digest();
        StringBuilder result = new StringBuilder();
        for (byte b : hashValue) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
