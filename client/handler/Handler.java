package client.handler;

import client.Link;
import client.datapackage.DataPackage;

import java.nio.channels.SelectionKey;

public abstract class Handler {
    protected static final int BUFFER_MAX_SIZE = 8*1024;
    protected Link link;

    public Handler(Link link) {
        this.link = link;
    }
    public abstract void receiveHandler(SelectionKey key);
    public abstract void sendHandle(SelectionKey key, DataPackage dataPackage);
}