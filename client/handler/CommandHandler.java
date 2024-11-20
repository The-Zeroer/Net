package net.handler;

import net.Link;
import net.util.LinkTable;
import net.datapackage.CommandPackage;
import net.datapackage.DataPackage;
import net.datapackage.FilePackage;
import net.datapackage.MessagePackage;
import net.log.NetLog;
import net.util.NetTool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class CommandHandler extends Handler{
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
            ByteBuffer buffer = ByteBuffer.allocate(CommandPackage.HEADER_SIZE);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) {
                    link.cancel(key, false);
                    return;
                }
            }
            buffer.flip();
            CDP.setWay(buffer.get()).setType(buffer.get()).setAppendState(buffer.get())
                    .setTime(buffer.getLong()).setDataSize(buffer.getInt());
            byte[] taskIdBytes = new byte[buffer.getShort()];
            buffer = ByteBuffer.wrap(taskIdBytes);
            while (buffer.hasRemaining()) {
                channel.read(buffer);
            }
            buffer.flip();
            buffer.get(taskIdBytes);
            CDP.setTaskId(new String(taskIdBytes));
            int dataSize = CDP.getDataSize();
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
                                        NetTool.sleep();
                                    }
                                    link.putDataPackage(linkTable.getMessageKey(), new MessagePackage
                                            (DataPackage.WAY_TOKEN_VERIFY, linkTable.getToken()));
                                    linkTable.setMessageLinkState(LinkTable.VERIFY);
                                    while (true) {
                                        MessagePackage messagePackage = linkTable.getMessagePackage();
                                        if (messagePackage != null) {
                                            SelectionKey messageKey = linkTable.getMessageKey();
                                            if (messageKey != null) {
                                                link.putDataPackage(messageKey, messagePackage);
                                            }
                                        } else {
                                            linkTable.setMessageLinkState(LinkTable.READY);
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
                                        NetTool.sleep();
                                    }
                                    link.putDataPackage(linkTable.getFileKey(), new FilePackage(DataPackage.WAY_TOKEN_VERIFY
                                            , linkTable.getToken().getBytes()));
                                    linkTable.setFileLinkState(LinkTable.VERIFY);
                                    while (true) {
                                        FilePackage filePackage = linkTable.getFilePackage();
                                        if (filePackage != null) {
                                            SelectionKey fileKey = linkTable.getFileKey();
                                            if (fileKey != null) {
                                                link.putDataPackage(fileKey, filePackage);
                                            }
                                        } else {
                                            linkTable.setFileLinkState(LinkTable.READY);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        NetLog.error(e);
                    }
                }

                default -> {
                    link.addDataPackage(CDP);
                }
            }
        } catch (IOException e) {
            link.cancel(key, true);
        } finally {
            link.receiveFinish(key);
        }
    }

    @Override
    public void sendHandle(SelectionKey key, DataPackage dataPackage) {
        CommandPackage CDP = (CommandPackage) dataPackage;
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(CommandPackage.HEADER_SIZE + CDP.getTaskIdLength());
            buffer.put(CDP.getWay()).put(CDP.getType()).put(CDP.getAppendState()).putLong(CDP.getTime())
                    .putInt(CDP.getDataSize()).putShort(CDP.getTaskIdLength()).put(CDP.getTaskIdBytes());
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            int dataSize = CDP.getDataSize();
            if (dataSize > 0) {
                buffer = ByteBuffer.allocate(Math.min(dataSize, BUFFER_MAX_SIZE));
                byte[] data = CDP.getData();
                for (int residue = dataSize, writeCount = 0; residue > 0;residue -= writeCount, writeCount = 0) {
                    buffer.put(data, dataSize - residue, Math.min(residue, buffer.remaining()));
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
            link.cancel(key, true);
        } finally {
            link.sendFinish(key);
        }
    }
}