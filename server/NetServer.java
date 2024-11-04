package server;

import server.datapackage.CommandPackage;
import server.datapackage.DataPackage;
import server.datapackage.FilePackage;
import server.datapackage.MessagePackage;
import server.link.CommandLink;
import server.link.FileLink;
import server.link.MessageLink;
import server.log.LogHandler;
import server.log.NetLog;
import server.util.TOOL;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

public class NetServer {
    private final Accept accept;
    private final CommandLink commandLink;
    private final MessageLink messageLink;
    private final FileLink fileLink;
    private final LinkTable linkTable;
    private ServerSocketChannel CSSC, MSSC, FSSC;
    private String messagAddress, fileAddress;

    public NetServer() throws IOException {
        accept = new Accept();
        accept.setName("Accept");
        linkTable = new LinkTable();
        commandLink = new CommandLink(linkTable);
        commandLink.setName("CommandLink");
        messageLink = new MessageLink(linkTable);
        messageLink.setName("MessageLink");
        fileLink = new FileLink(linkTable);
        fileLink.setName("FileLink");
    }

    public void accept() throws IOException {
        if (CSSC == null || !CSSC.isOpen()) {
            bindCommandPort(0);
        }
        if (MSSC == null || !MSSC.isOpen()) {
            bindMessagePort(0);
        }
        if (FSSC == null || !FSSC.isOpen()) {
            bindFilePort(0);
        }
        accept.addMonitor(CSSC, commandLink);
        accept.addMonitor(MSSC, messageLink);
        accept.addMonitor(FSSC, fileLink);
        accept.start();
    }

    public void bindCommandPort(int port) throws IOException {
        CSSC = ServerSocketChannel.open();
        CSSC.bind(new InetSocketAddress(port));
        NetLog.info("控制端口绑定 [$] ", CSSC.socket().getLocalPort());
    }
    public void bindMessagePort(int port) throws IOException {
        MSSC = ServerSocketChannel.open();
        MSSC.bind(new InetSocketAddress(port));
        messagAddress = InetAddress.getLocalHost().getHostAddress() + ":" + MSSC.socket().getLocalPort();
        commandLink.setMessageAddress(messagAddress);
        NetLog.info("消息端口绑定 [$] ", MSSC.socket().getLocalPort());
    }
    public void bindFilePort(int port) throws IOException {
        FSSC = ServerSocketChannel.open();
        FSSC.bind(new InetSocketAddress(port));
        fileAddress = InetAddress.getLocalHost().getHostAddress() + ":" + FSSC.socket().getLocalPort();
        commandLink.setFileAddress(fileAddress);
        NetLog.info("文件端口绑定 [$] ", FSSC.socket().getLocalPort());
    }

    public void setFlow(long capacity, long rate) {
        accept.setTokenBucket(capacity, rate);
    }
    public void setCommandMaxLinkCount(int maxLinkCount) {
        commandLink.setMaxLinkCount(maxLinkCount);
    }
    public void setMessageMaxLinkCount(int maxLinkCount) {
        messageLink.setMaxLinkCount(maxLinkCount);
    }
    public void setFileMaxLinkCount(int maxLinkCount) {
        fileLink.setMaxLinkCount(maxLinkCount);
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

    public boolean putCommandPackage(String UID, CommandPackage commandPackage) {
        SelectionKey commandKey = linkTable.getCommandKeyByUID(UID);
        if (commandKey != null) {
            commandLink.putDataPackage(commandKey, commandPackage.setSelectionKey(commandKey).setUID(UID));
            return true;
        } else {
            return false;
        }
    }
    public boolean putMessagePackage(String UID, MessagePackage messagePackage) {
        SelectionKey messageKey = linkTable.getMessageKeyByUID(UID);
        switch (linkTable.getMessageLinkStata(UID)) {
            case null -> {
                messageLink.putDataPackage(messageKey, messagePackage.setSelectionKey(messageKey).setUID(UID));
            }
            case LinkTable.MESSAGE_VERIFY -> {
                for (int i = 0; !linkTable.messageQueueEmpty(UID) && i < 100; i++) {
                    TOOL.sleep();
                }
                if (linkTable.messageQueueEmpty(UID)) {
                    messageLink.putDataPackage(messageKey, messagePackage.setSelectionKey(messageKey).setUID(UID));
                } else {
                    linkTable.putMessagePackage(UID, messagePackage);
                }
            }
            case LinkTable.MESSAGE_LINK_2 -> {
                linkTable.putMessagePackage(UID, messagePackage);
            }
            case LinkTable.MESSAGE_LINK_1 -> {
                linkTable.putMessagePackage(UID, messagePackage);
                linkTable.setMessageLinkStata(UID, LinkTable.MESSAGE_LINK_2);
                return putCommandPackage(UID, new CommandPackage(DataPackage.WAY_BUILD_LINK
                        , DataPackage.TYPE_MESSAGE_ADDRESS, messagAddress.getBytes()));
            }
            default -> throw new IllegalStateException();
        }
        return true;
    }
    public boolean putFilePackage(String UID, FilePackage filePackage) {
        SelectionKey fileKey = linkTable.getFileKeyByUID(UID);
        switch (linkTable.getFileLinkStata(UID)) {
            case null -> {
                fileLink.putDataPackage(fileKey, filePackage.setSelectionKey(fileKey).setUID(UID));
            }
            case LinkTable.FILE_VERIFY -> {
                for (int i = 0; !linkTable.fileQueueEmpty(UID) && i < 100; i++) {
                    TOOL.sleep();
                }
                if (linkTable.fileQueueEmpty(UID)) {
                    fileLink.putDataPackage(fileKey, filePackage.setSelectionKey(fileKey).setUID(UID));
                } else {
                    linkTable.putFilePackage(UID, filePackage);
                }
            }
            case LinkTable.FILE_LINK_2 -> {
                linkTable.putFilePackage(UID, filePackage);
            }
            case LinkTable.FILE_LINK_1 -> {
                linkTable.putFilePackage(UID, filePackage);
                linkTable.setFileLinkStata(UID, LinkTable.FILE_LINK_2);
                return putCommandPackage(UID, new CommandPackage(DataPackage.WAY_BUILD_LINK
                        , DataPackage.TYPE_FILE_ADDRESS, messagAddress.getBytes()));
            }
            default -> throw new IllegalStateException();
        }
        return true;
    }

    public CommandPackage getCommandPackage() {
        return (CommandPackage) commandLink.getDataPackage(true);
    }
    public MessagePackage getMessagePackage() {
        return (MessagePackage) messageLink.getDataPackage(false);
    }
    public FilePackage getFilePackage() {
        return (FilePackage) fileLink.getDataPackage(false);
    }

    public void register(SelectionKey key, String UID, String addition) {
        String token = TOOL.getToken(key, addition);
        linkTable.register(key, UID, token);
        commandLink.putDataPackage(key, new CommandPackage(DataPackage.WAY_TOKEN_VERIFY, token.getBytes())
                .setSelectionKey(key).setUID(UID));
    }
    public void cancel(String UID) {
        linkTable.cancel(UID);
    }
}