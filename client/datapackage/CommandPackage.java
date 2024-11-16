package net.datapackage;

public class CommandPackage extends DataPackage {

    public CommandPackage() {}

    public CommandPackage(byte way) {
        super(way, CommandPackage.TYPE_TEXT, null);
    }

    public CommandPackage(byte way, byte type) {
        super(way, type);
    }

    public CommandPackage(byte way, byte[] data) {
        super(way, data);
    }

    public CommandPackage(byte way, byte type, byte[] data){
        super(way, type, data);
    }
}