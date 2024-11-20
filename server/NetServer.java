package net;

import net.datapackage.CommandPackage;
import net.datapackage.DataPackage;
import net.datapackage.FilePackage;
import net.datapackage.MessagePackage;
import net.exception.NetException;
import net.link.CommandLink;
import net.link.FileLink;
import net.link.MessageLink;
import net.log.NetLogHandler;
import net.log.NetLog;
import net.util.LinkTable;
import net.util.NetTool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.List;

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
        commandLink = new CommandLink(this, linkTable);
        commandLink.setName("CommandLink");
        messageLink = new MessageLink(this, linkTable);
        messageLink.setName("MessageLink");
        fileLink = new FileLink(this, linkTable);
        fileLink.setName("FileLink");
        linkTable.setLink(commandLink, messageLink, fileLink);
        messageLink.setHeartBeatInterval(300);
        fileLink.setHeartBeatInterval(600);
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
        commandLink.start();
        messageLink.start();
        fileLink.start();
        accept.start();
    }

    public void register(SelectionKey key, String UID, String addition) {
        String token = NetTool.getToken(key, addition);
        linkTable.register(key, UID, token);
        commandLink.putDataPackage(key, new CommandPackage(DataPackage.WAY_TOKEN_VERIFY, token.getBytes())
                .setSelectionKey(key).setUID(UID));
    }
    public void cancel(String UID) {
        linkTable.cancel(UID);
    }

    public void putCommandPackage(SelectionKey key, CommandPackage commandPackage) {
        commandLink.putDataPackage(key, commandPackage);
    }

    public boolean putCommandPackage(String UID, CommandPackage commandPackage) {
        SelectionKey commandKey = linkTable.getCommandKeyByUID(UID);
        if (commandKey != null) {
            commandLink.putDataPackage(commandKey, commandPackage.setSelectionKey(commandKey).setUID(UID));
            return true;
        } else {
            NetLog.warn("发送 [CommandPackage] 时,目标UID [$] 不存在", UID);
            return false;
        }
    }
    public boolean putMessagePackage(String UID, MessagePackage messagePackage) {
        SelectionKey messageKey = linkTable.getMessageKeyByUID(UID);
        switch (linkTable.getMessageLinkStata(UID)) {
            case LinkTable.READY -> {
                messageLink.putDataPackage(messageKey, messagePackage.setSelectionKey(messageKey).setUID(UID));
            }
            case LinkTable.VERIFY -> {
                for (int i = 0; !linkTable.messageQueueEmpty(UID) && i < 100; i++) {
                    NetTool.sleep();
                }
                if (linkTable.messageQueueEmpty(UID)) {
                    messageLink.putDataPackage(messageKey, messagePackage.setSelectionKey(messageKey).setUID(UID));
                } else {
                    linkTable.putMessagePackage(UID, messagePackage);
                }
            }
            case LinkTable.LINK_2 -> {
                linkTable.putMessagePackage(UID, messagePackage);
            }
            case LinkTable.LINK_1 -> {
                linkTable.putMessagePackage(UID, messagePackage);
                linkTable.setMessageLinkStata(UID, LinkTable.LINK_2);
                return putCommandPackage(UID, new CommandPackage(DataPackage.WAY_BUILD_LINK
                        , DataPackage.TYPE_MESSAGE_ADDRESS, messagAddress.getBytes()));
            }
            case null -> {
                NetLog.debug("发送 [MessagePackage] 时,目标UID [$] 不存在", UID);
                return false;
            }
            default -> NetLog.error(new IllegalStateException());
        }
        return true;
    }
    public boolean putFilePackage(String UID, FilePackage filePackage) {
        SelectionKey fileKey = linkTable.getFileKeyByUID(UID);
        switch (linkTable.getFileLinkStata(UID)) {
            case LinkTable.READY -> {
                fileLink.putDataPackage(fileKey, filePackage.setSelectionKey(fileKey).setUID(UID));
            }
            case LinkTable.VERIFY -> {
                for (int i = 0; !linkTable.fileQueueEmpty(UID) && i < 100; i++) {
                    NetTool.sleep();
                }
                if (linkTable.fileQueueEmpty(UID)) {
                    fileLink.putDataPackage(fileKey, filePackage.setSelectionKey(fileKey).setUID(UID));
                } else {
                    linkTable.putFilePackage(UID, filePackage);
                }
            }
            case LinkTable.LINK_2 -> {
                linkTable.putFilePackage(UID, filePackage);
            }
            case LinkTable.LINK_1 -> {
                linkTable.putFilePackage(UID, filePackage);
                linkTable.setFileLinkStata(UID, LinkTable.LINK_2);
                return putCommandPackage(UID, new CommandPackage(DataPackage.WAY_BUILD_LINK
                        , DataPackage.TYPE_FILE_ADDRESS, fileAddress.getBytes()));
            }
            case null -> {
                NetLog.debug("发送 [FilePackage] 时,目标UID [$] 不存在", UID);
                return false;
            }
            default -> NetLog.error(new IllegalStateException());
        }
        return true;
    }

    public CommandPackage getCommandPackage() throws NetException {
        return (CommandPackage) commandLink.getDataPackage();
    }
    public MessagePackage getMessagePackage() throws NetException {
        return (MessagePackage) messageLink.getDataPackage();
    }
    public FilePackage getFilePackage() throws NetException {
        return (FilePackage) fileLink.getDataPackage();
    }

    public boolean isOnline(String UID) {
        return linkTable.getCommandKeyByUID(UID) != null;
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
    public void setLogHandler(NetLogHandler netLogHandler) {
        NetLog.setLogHandler(netLogHandler);
    }

    /**
     * 单位(s)
     */
    public void setHeartBeatInterval(int interval) {
        commandLink.setHeartBeatInterval(interval);
    }

    public void setTempFilePath(String tempFilePath) throws FileNotFoundException {
        fileLink.setTempFilePath(tempFilePath);
    }
}