package client.datapackage;

public class MessagePackage extends DataPackage {
    private short tokenLenght;
    private short senderLenght;
    private short receiverLenght;
    private byte[] token;
    private byte[] sender;
    private byte[] receiver;

    public MessagePackage() {}

    public MessagePackage(byte way, byte[] data) {
        this(way, DataPackage.TYPE_TEXT, data);
    }

    public MessagePackage(byte way, byte type, byte[] data) {
        packageSize = 14;
        this.way = way;
        this.type = type;
        this.time = System.currentTimeMillis();
        if (data != null) {
            this.data = data;
            packageSize += data.length;
        }
    }

    public MessagePackage(byte way, byte type, String token, String sender, String receiver, byte[] data) {

    }
}
