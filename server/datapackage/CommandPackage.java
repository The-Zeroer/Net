package server.datapackage;

public class CommandPackage extends DataPackage{

    public CommandPackage(){}

    public CommandPackage(byte way, byte[] data) {
        this(way, DataPackage.TYPE_TEXT, data);
    }

    public CommandPackage(byte way, byte type, byte[] data){
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