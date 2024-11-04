package client;

import client.datapackage.CommandPackage;
import client.datapackage.DataPackage;
import client.datapackage.FilePackage;
import client.datapackage.MessagePackage;
import client.handler.CommandHandler;
import client.handler.FileHandler;
import client.handler.MessageHandler;
import client.log.LogHandler;
import client.log.NetLog;
import client.util.TOOL;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NetClient {
    private final Link link;
    private final LinkTable linkTable;
    private final CommandHandler commandHandler;
    private final MessageHandler messageHandler;
    private final FileHandler fileHandler;

    public NetClient() throws IOException {
        linkTable = new LinkTable();
        link = new Link(linkTable);
        commandHandler = new CommandHandler(link);
        messageHandler = new MessageHandler(link);
        fileHandler = new FileHandler(link);
        commandHandler.setMessageHandler(messageHandler);
        commandHandler.setFileHandler(fileHandler);
        commandHandler.setLinkTable(linkTable);
    }

    public void openLink(String host, int port) throws IOException {
        NetLog.info("正在连接服务器 [$:$]", host, port);
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
        link.register(socketChannel, commandHandler);
    }

    public void setLogLevel(int level) {
        NetLog.setLevel(level);
    }
    public void setLogMaxCount(int maxCount) {
        NetLog.setMaxLogCount(maxCount);
    }
    public void setLogHandler(LogHandler logHandler) {
        NetLog.setLogHandler(logHandler);
    }

    public void putCommandPackage(CommandPackage commandPackage) {
        while (linkTable.getCommandKey() == null || commandPackage.getWay() != DataPackage.WAY_LOGIN && linkTable.getToken() == null) {
            TOOL.sleep();
        }
        link.putDataPackage(linkTable.getCommandKey(), commandPackage);
    }
    public synchronized void putMessagePackage(MessagePackage messagePackage) {
        SelectionKey messageKey = linkTable.getMessageKey();
        switch (linkTable.getMessageLinkState()) {
            case LinkTable.MESSAGE_READY -> {
                link.putDataPackage(messageKey, messagePackage);
            }
            case LinkTable.MESSAGE_VERIFY -> {
                for (int i = 0; !linkTable.messageQueueEmpty() && i < 100; i++) {
                    TOOL.sleep();
                }
                if (linkTable.messageQueueEmpty()) {
                    link.putDataPackage(messageKey, messagePackage);
                } else {
                    linkTable.putMessagePackage(messagePackage);
                }
            }
            case LinkTable.MESSAGE_LINK_2 -> {
                linkTable.putMessagePackage(messagePackage);
            }
            case LinkTable.MESSAGE_LINK_1 -> {
                String token = linkTable.getToken();
                if (token == null) {
                    for (int i = 0; linkTable.getToken() == null && i < 100; i++) {
                        TOOL.sleep();
                    }
                    if (linkTable.getToken() != null) {
                        token = linkTable.getToken();
                    } else {
                        token = "null";
                    }
                }
                putCommandPackage(new CommandPackage(DataPackage.WAY_BUILD_LINK, DataPackage.TYPE_MESSAGE_ADDRESS
                        , token.getBytes()));
                linkTable.setMessageLinkState(LinkTable.MESSAGE_LINK_2);
                linkTable.putMessagePackage(messagePackage);
            }
        }
    }
    public synchronized void putFilePackage(FilePackage filePackage) {
        SelectionKey fileKey = linkTable.getFileKey();
        switch (linkTable.getFileLinkState()) {
            case LinkTable.FILE_READY -> {
                link.putDataPackage(fileKey, filePackage);
            }
            case LinkTable.FILE_VERIFY -> {
                for (int i = 0; !linkTable.fileQueueEmpty() && i < 100; i++) {
                    TOOL.sleep();
                }
                if (linkTable.fileQueueEmpty()) {
                    link.putDataPackage(fileKey, filePackage);
                } else {
                    linkTable.putFilePackage(filePackage);
                }
            }
            case LinkTable.FILE_LINK_2 -> {
                linkTable.putFilePackage(filePackage);
            }
            case LinkTable.FILE_LINK_1 -> {
                String token = linkTable.getToken();
                if (token == null) {
                    for (int i = 0; linkTable.getToken() == null && i < 100; i++) {
                        TOOL.sleep();
                    }
                    if (linkTable.getToken() != null) {
                        token = linkTable.getToken();
                    } else {
                        token = "null";
                    }
                }
                putCommandPackage(new CommandPackage(DataPackage.WAY_BUILD_LINK, DataPackage.TYPE_FILE_ADDRESS
                        , token.getBytes()));
                linkTable.setFileLinkState(LinkTable.FILE_LINK_2);
                linkTable.putFilePackage(filePackage);
            }
        }
    }

    public CommandPackage getCommandPackage() {
        return (CommandPackage) link.getDataPackage(true);
    }
    public MessagePackage getMessagePackage() {
        return (MessagePackage) link.getDataPackage(false);
    }
    public FilePackage getFilePackage() {
        return (FilePackage) link.getDataPackage(false);
    }
}