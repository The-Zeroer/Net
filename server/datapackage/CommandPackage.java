package net.datapackage;

public class CommandPackage extends DataPackage{

    public CommandPackage(){}

    public CommandPackage(byte way) {
        super(way);
    }

    public CommandPackage(byte way, byte type) {
        super(way, type, null);
    }

    public CommandPackage(byte way, byte[] data) {
        super(way, DataPackage.TYPE_TEXT, data);
    }

    public CommandPackage(byte way, byte type, byte[] data){
        super(way, type, data);
    }
}