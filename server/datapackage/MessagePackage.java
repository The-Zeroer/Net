package server.datapackage;

public class MessagePackage extends DataPackage {
    private short senderLenght;
    private short receiverLenght;
    private byte[] sender;
    private byte[] receiver;

    public MessagePackage() {}

    public MessagePackage(byte way, byte[] data) {
        this(way, client.datapackage.DataPackage.TYPE_TEXT, data);
    }

    public MessagePackage(byte way, byte type, byte[] data) {
        this.way = way;
        this.type = type;
        this.time = System.currentTimeMillis();
        if (data != null) {
            this.data = data;
            dataSize += data.length;
        }
    }
}