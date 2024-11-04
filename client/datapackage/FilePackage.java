package client.datapackage;

public class FilePackage extends DataPackage {

    public FilePackage() {}


    public FilePackage(byte way, byte[] data) {
        this(way, TYPE_TEXT, data);
    }

    public FilePackage(byte way, byte type, byte[] data) {
        packageSize = 14;
        this.way = way;
        this.type = type;
        this.time = System.currentTimeMillis();
        if (data != null) {
            this.data = data;
            packageSize += data.length;
        }
    }
}
