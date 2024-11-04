package client.handler;

import client.Link;
import client.LinkTable;
import client.datapackage.CommandPackage;
import client.datapackage.DataPackage;
import client.datapackage.FilePackage;
import client.datapackage.MessagePackage;
import client.log.NetLog;
import client.util.TOOL;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class CommandHandler extends Handler{
    private static final int BUFFER_MAX_SIZE = 8*1024;
    private MessageHandler messageHandler;
    private FileHandler fileHandler;
    private LinkTable linkTable;

    public CommandHandler(Link link) {
        super(link);
    }

    public void setMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }
    public void setFileHandler(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }
    public void setLinkTable(LinkTable linkTable) {
        this.linkTable = linkTable;
    }

    @Override
    public void receiveHandler(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        CommandPackage CDP = new CommandPackage();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(14);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) {
                    link.cancel(key);
                    return;
                }
            }
            buffer.flip();
            CDP.setPackageSize(buffer.getInt());
            CDP.setWay(buffer.get());
            CDP.setType(buffer.get());
            CDP.setTime(buffer.getLong());
            int dataSize = CDP.getPackageSize() - 14;
            if (dataSize > 0) {
                byte[] data = new byte[dataSize];
                buffer = ByteBuffer.allocate(Math.min(dataSize, BUFFER_MAX_SIZE));
                for (int residue = dataSize, readCount = 0; residue > 0;residue -= readCount, readCount = 0) {
                    if (residue < buffer.remaining()) {
                        buffer.limit(residue);
                    }
                    while (buffer.hasRemaining()) {
                        readCount += channel.read(buffer);
                    }
                    buffer.flip();
                    buffer.get(data, dataSize - residue,
                            (Math.min(residue, buffer.remaining())));
                    buffer.clear();
                }
                CDP.setData(data);
            }

            NetLog.debug("接收 {$}", CDP);

            switch (CDP.getWay()) {
                case DataPackage.WAY_HEART_BEAT -> {

                }

                case DataPackage.WAY_TOKEN_VERIFY -> {
                    linkTable.putToken(new String(CDP.getData()));
                    NetLog.info("获得Token");
                }

                case DataPackage.WAY_BUILD_LINK -> {
                    try {
                        switch (CDP.getType()) {
                            case DataPackage.TYPE_MESSAGE_ADDRESS -> {
                                if (linkTable.getMessageKey() == null) {
                                    String[] address = new String(CDP.getData()).split(":");
                                    SocketChannel socketChannel = SocketChannel.open(
                                            new InetSocketAddress(address[0], Integer.parseInt(address[1])));
                                    link.register(socketChannel, messageHandler);
                                    for (int i = 0; linkTable.getMessageKey() == null && i < 100; i++) {
                                        TOOL.sleep();
                                    }
                                    link.putDataPackage(linkTable.getMessageKey(), new MessagePackage(DataPackage.WAY_TOKEN_VERIFY
                                            , linkTable.getToken().getBytes()));
                                    linkTable.setMessageLinkState(LinkTable.MESSAGE_VERIFY);
                                    while (true) {
                                        MessagePackage messagePackage = linkTable.getMessagePackage();
                                        if (messagePackage != null) {
                                            SelectionKey messageKey = linkTable.getMessageKey();
                                            if (messageKey != null) {
                                                link.putDataPackage(messageKey, messagePackage);
                                            }
                                        } else {
                                            linkTable.setMessageLinkState(LinkTable.MESSAGE_READY);
                                            return;
                                        }
                                    }
                                }
                            }
                            case DataPackage.TYPE_FILE_ADDRESS -> {
                                if (linkTable.getFileKey() == null) {
                                    String[] address = new String(CDP.getData()).split(":");
                                    SocketChannel socketChannel = SocketChannel.open(
                                            new InetSocketAddress(address[0], Integer.parseInt(address[1])));
                                    link.register(socketChannel, fileHandler);
                                    for (int i = 0; linkTable.getFileKey() == null && i < 100; i++) {
                                        TOOL.sleep();
                                    }
                                    link.putDataPackage(linkTable.getFileKey(), new FilePackage(DataPackage.WAY_TOKEN_VERIFY
                                            , linkTable.getToken().getBytes()));
                                    while (true) {
                                        FilePackage filePackage = linkTable.getFilePackage();
                                        if (filePackage != null) {
                                            SelectionKey fileKey = linkTable.getFileKey();
                                            if (fileKey != null) {
                                                link.putDataPackage(fileKey, filePackage);
                                            }
                                        } else {
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                default -> {
                    link.addDataPackage(CDP);
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
        CommandPackage CDP = (CommandPackage) dataPackage;
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buffer = ByteBuffer.allocate(14);
            buffer.putInt(CDP.getPackageSize()).put(CDP.getWay()).put(CDP.getType()).putLong(CDP.getTime());
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            int dataSize = CDP.getPackageSize() - 14;
            if (dataSize > 0) {
                buffer = ByteBuffer.allocate(Math.min(dataSize, BUFFER_MAX_SIZE));
                for (int residue = dataSize, writeCount = 0; residue > 0;residue -= writeCount, writeCount = 0) {
                    buffer.put(CDP.getData(), dataSize - residue, Math.min(residue, buffer.remaining()));
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        writeCount += channel.write(buffer);
                    }
                    buffer.clear();
                }
            }
            NetLog.debug("发送 {$} 成功", CDP);
        } catch (IOException e) {
            NetLog.error("发送 {$} 失败", CDP);
            link.cancel(key);
        } finally {
            link.sendFinish(key);
        }
    }
}
