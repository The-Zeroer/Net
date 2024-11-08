package client;

import client.datapackage.CommandPackage;
import client.datapackage.DataPackage;
import client.datapackage.FilePackage;
import client.datapackage.MessagePackage;
import client.exception.link.AgainLinkTimeOutException;
import client.exception.link.ServerCloseLinkException;
import client.exception.token.TokenMissingException;
import client.handler.CommandHandler;
import client.handler.FileHandler;
import client.handler.MessageHandler;
import client.log.LogHandler;
import client.log.NetLog;
import client.util.TOOL;

import java.io.FileNotFoundException;
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
    private final HeartBeat heartBeat;

    public NetClient() throws IOException {
        linkTable = new LinkTable();
        link = new Link(linkTable);
        commandHandler = new CommandHandler(link);
        messageHandler = new MessageHandler(link);
        fileHandler = new FileHandler(link);
        commandHandler.setMessageHandler(messageHandler);
        commandHandler.setFileHandler(fileHandler);
        commandHandler.setLinkTable(linkTable);
        heartBeat = new HeartBeat(link, linkTable);
    }

    /**
     * 所有的set()请在调用此方法前完成
     */
    public void openLink(String host, int port) throws IOException {
        NetLog.info("正在连接服务器 [$:$]", host, port);
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
        link.register(socketChannel, commandHandler);
        heartBeat.start();
    }

    public synchronized void putCommandPackage(CommandPackage commandPackage) {
        for (int i = 0; (linkTable.getCommandKey() == null || (commandPackage.getWay() != DataPackage.WAY_LOGIN
                && linkTable.getToken() == null)) && i < 100; i++) {
            TOOL.sleep();
        }
        link.putDataPackage(linkTable.getCommandKey(), commandPackage);
    }
    public synchronized void putMessagePackage(MessagePackage messagePackage) {
        SelectionKey messageKey = linkTable.getMessageKey();
        switch (linkTable.getMessageLinkState()) {
            case LinkTable.READY -> {
                link.putDataPackage(messageKey, messagePackage);
            }
            case LinkTable.VERIFY -> {
                for (int i = 0; !linkTable.messageQueueEmpty() && i < 100; i++) {
                    TOOL.sleep();
                }
                if (linkTable.messageQueueEmpty()) {
                    link.putDataPackage(messageKey, messagePackage);
                } else {
                    linkTable.putMessagePackage(messagePackage);
                }
            }
            case LinkTable.LINK_2 -> {
                linkTable.putMessagePackage(messagePackage);
            }
            case LinkTable.LINK_1 -> {
                for (int i = 0; linkTable.getToken() == null && i < 100; i++) {
                    TOOL.sleep();
                }
                if (linkTable.getToken() != null) {
                    putCommandPackage(new CommandPackage(DataPackage.WAY_BUILD_LINK, DataPackage.TYPE_MESSAGE_ADDRESS));
                    linkTable.setMessageLinkState(LinkTable.LINK_2);
                    linkTable.putMessagePackage(messagePackage);
                } else {
                    link.addException(new TokenMissingException());
                }
            }
        }
    }
    public synchronized void putFilePackage(FilePackage filePackage) {
        SelectionKey fileKey = linkTable.getFileKey();
        switch (linkTable.getFileLinkState()) {
            case LinkTable.READY -> {
                link.putDataPackage(fileKey, filePackage);
            }
            case LinkTable.VERIFY -> {
                for (int i = 0; !linkTable.fileQueueEmpty() && i < 100; i++) {
                    TOOL.sleep();
                }
                if (linkTable.fileQueueEmpty()) {
                    link.putDataPackage(fileKey, filePackage);
                } else {
                    linkTable.putFilePackage(filePackage);
                }
            }
            case LinkTable.LINK_2 -> {
                linkTable.putFilePackage(filePackage);
            }
            case LinkTable.LINK_1 -> {
                for (int i = 0; linkTable.getToken() == null && i < 100; i++) {
                    TOOL.sleep();
                }
                if (linkTable.getToken() != null) {
                    putCommandPackage(new CommandPackage(DataPackage.WAY_BUILD_LINK, DataPackage.TYPE_FILE_ADDRESS));
                    linkTable.setFileLinkState(LinkTable.LINK_2);
                    linkTable.putFilePackage(filePackage);
                } else {
                    link.addException(new TokenMissingException());
                }
            }
        }
    }

    public DataPackage getDataPackage() throws AgainLinkTimeOutException, ServerCloseLinkException, TokenMissingException {
        return link.getDataPackage();
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

    /**
     * 单位(s)
     */
    public void setHeartBeatInterval(int interval) {
        heartBeat.setHeartBeatInterval(interval);
    }

    public void setTempFilePath(String tempFilePath) throws FileNotFoundException {
        fileHandler.setTempFilePath(tempFilePath);
    }
}