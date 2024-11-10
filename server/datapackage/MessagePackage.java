package server.datapackage;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class MessagePackage extends DataPackage {
    public static final int HEADER_SIZE = 20;
    private short senderLenght;
    private short receiverLenght;
    private byte[] sender;
    private byte[] receiver;

    public MessagePackage() {}

    public MessagePackage(byte way, byte type, String sender, String receiver, byte[] data) {
        this.way = way;
        this.type = type;
        this.sender = sender.getBytes();
        this.receiver = receiver.getBytes();
        if (data != null) {
            this.data = data;
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
        String address = null;
        if (key != null) {
            try {
                address = ((SocketChannel)key.channel()).getRemoteAddress().toString();
            } catch (IOException e) {
                address = key.channel().toString();
            }
        }
        return getClass().getSimpleName() + " [RemoteAddress=" + address + ", UID=" + UID
                + ", way=" + way + ", type=" + type + ", time=" + dateFormat.format(time)
                + ", dataSize=" + formatBytes(dataSize) + ", sender=" + getSender() + ", receiver=" + getReceiver()
                + ", taskId=" + getTaskId() + "]";
    }
}