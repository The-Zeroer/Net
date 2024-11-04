package client.handler;

import client.Link;
import client.datapackage.DataPackage;
import client.datapackage.FilePackage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class FileHandler extends Handler {
    public FileHandler(Link link) {
        super(link);
    }

    @Override
    public void receiveHandler(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        FilePackage FDP = new FilePackage();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(14);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) {
                    link.cancel(key);
                    return;
                }
            }
        } catch (IOException e) {
            link.cancel(key);
        } finally {
            link.receiveFinish(key);
        }
    }

    @Override
    public void sendHandle(SelectionKey key, DataPackage dataPackage) {

    }
}
