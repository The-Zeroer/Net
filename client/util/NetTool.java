package net.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class NetTool {
    private static int taskId = 0;

    public static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static synchronized String produceTaskId() {
        taskId++;
        try {
            return getHashValue((String.valueOf(System.currentTimeMillis() + taskId)).getBytes(), "MD5");
        } catch (NoSuchAlgorithmException ignored) {
            return String.valueOf(System.currentTimeMillis() + taskId);
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

    public static String getFileHashValue(File file, String algorithm) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        FileInputStream fis = new FileInputStream(file);
        FileChannel fc = fis.getChannel();
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
        while (fc.read(buffer) != -1) {
            buffer.flip();
            digest.update(buffer);
            buffer.clear();
        }
        fis.close();
        fc.close();
        return new BigInteger(1, digest.digest()).toString(16);
    }
}