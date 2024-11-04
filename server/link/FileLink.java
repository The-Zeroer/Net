package server.link;

import server.LinkTable;
import server.datapackage.DataPackage;
import server.datapackage.FilePackage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class FileLink extends Link {

    public FileLink(LinkTable linkTable) throws IOException {
        super(linkTable);
    }

    @Override
    protected void receiveReceive(SelectionKey key) {

        SocketChannel channel = (SocketChannel) key.channel();
        FilePackage FDP = new FilePackage();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == 0) {
                    cancel(key);
                    return;
                }
            }
        } catch (IOException e) {
            cancel(key);
        } finally {
            receiveFinish(key);
        }
    }

    @Override
    protected void sendReceive(SelectionKey key, DataPackage dataPackage) {
        SocketChannel channel = (SocketChannel) key.channel();
        FilePackage FDP = (FilePackage) dataPackage;

    }
}
