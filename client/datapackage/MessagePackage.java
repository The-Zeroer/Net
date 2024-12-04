package net.datapackage;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class MessagePackage extends DataPackage {
    public static final int HEADER_SIZE = 21;
    private short senderLenght;
    private short receiverLenght;
    private byte[] sender;
    private byte[] receiver;

    private static String hostAddress;
    static {
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            hostAddress = "0.0.0.0";
        }
    }

    public MessagePackage() {}

    public MessagePackage(byte way, byte type) {
        this(way, type, hostAddress, "0", null);
    }

    public MessagePackage(byte way, String text) {
        this(way, MessagePackage.TYPE_TEXT, hostAddress, "0", text);
    }

    public MessagePackage(byte way, byte type, String text) {
        this(way, type, hostAddress, "0", text);
    }

    public MessagePackage(byte way, String sender, String receiver, String text) {
        this(way, MessagePackage.TYPE_TEXT, sender, receiver, text);
    }

    public MessagePackage(byte way, byte type, String sender, String receiver, String text) {
        this.way = way;
        this.type = type;
        this.sender = sender.getBytes();
        this.receiver = receiver.getBytes();
        if (text != null) {
            this.data = text.getBytes();
            dataSize = data.length;
        } else {
            dataSize = 0;
        }
        time = System.currentTimeMillis();
        senderLenght = (short) this.sender.length;
        receiverLenght = (short) this.receiver.length;
    }

    public short getSenderLenght() {
        return senderLenght;
    }
    public short getReceiverLenght() {
        return receiverLenght;
    }
    public String getSender() {
        if (sender != null) {
            return new String(sender);
        } else {
            return null;
        }
    }
    public MessagePackage setSender(String sender) {
        this.sender = sender.getBytes();
        senderLenght = (short) this.sender.length;
        return this;
    }
    public String getReceiver() {
        if (receiver != null) {
            return new String(receiver);
        } else {
            return null;
        }
    }
    public MessagePackage setReceiver(String receiver) {
        this.receiver = receiver.getBytes();
        receiverLenght = (short) this.receiver.length;
        return this;
    }
    public byte[] getSenderBytes() {
        return sender;
    }
    public byte[] getReceiverBytes() {
        return receiver;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+ " [way=" + way + ", type=" + type + ", time=" + dateFormat.format(time)
                + ", dataSize=" + formatBytes(dataSize) + ", sender=" + getSender() + ", receiver=" + getReceiver()
                + ", taskId=" + getTaskId() + "]";
    }
}