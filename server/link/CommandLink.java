package net.link;

import net.NetServer;
import net.util.LinkTable;
import net.datapackage.CommandPackage;
import net.datapackage.DataPackage;
import net.log.NetLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class CommandLink extends Link {
    private static final int BUFFER_MAX_SIZE = 8*1024;
    private String messageAddress, fileAddress;

    public CommandLink(NetServer netServer, LinkTable linkTable) throws IOException {
        super(netServer, linkTable);
    }

    public void setMessageAddress(String messageAddress) {
        this.messageAddress = messageAddress;
    }
    public void setFileAddress(String fileAddress) {
        this.fileAddress = fileAddress;
    }

    @Override
    protected void receiveReceive(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        CommandPackage CDP = new CommandPackage();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(CommandPackage.HEADER_SIZE);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) {
                    cancelCommandLink(key);
                    return;
                }
            }
            buffer.flip();
            CDP.setWay(buffer.get()).setType(buffer.get()).setAppendState(buffer.get())
                    .setTime(buffer.getLong()).setDataSize(buffer.getInt());
            // 验证Token
            if (CDP.getWay() != DataPackage.WAY_LOGIN && CDP.getWay() != DataPackage.WAY_TOKEN_VERIFY
                    && CDP.getWay() != DataPackage.WAY_HEART_BEAT && linkTable.getTokenByCommandKey(key) == null) {
                NetLog.warn("连接 [$] 无Token,已断开", channel.getRemoteAddress());
                cancelCommandLink(key);
                return;
            }
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
            CDP.setSelectionKey(key).setUID(linkTable.getUIDByCommandKey(key));

            switch (CDP.getWay()) {
                case DataPackage.WAY_HEART_BEAT -> {}

                case DataPackage.WAY_BUILD_LINK -> {
                    String UID = linkTable.getUIDByCommandKey(key);
                    switch (CDP.getType()) {
                        case DataPackage.TYPE_MESSAGE_ADDRESS -> {
                            putDataPackage(key, new CommandPackage(DataPackage.WAY_BUILD_LINK
                                    , DataPackage.TYPE_MESSAGE_ADDRESS, messageAddress.getBytes())
                                    .setSelectionKey(key).setUID(UID));
                            linkTable.setMessageLinkStata(UID, LinkTable.LINK_2);
                        }
                        case DataPackage.TYPE_FILE_ADDRESS -> {
                            putDataPackage(key, new CommandPackage(DataPackage.WAY_BUILD_LINK
                                    , DataPackage.TYPE_FILE_ADDRESS, fileAddress.getBytes())
                                    .setSelectionKey(key).setUID(UID));
                            linkTable.setFileLinkStata(UID, LinkTable.LINK_2);
                        }
                    }
                }

                case DataPackage.WAY_TOKEN_VERIFY -> {
                    String serverToken = linkTable.getTokenByCommandKey(key);
                    String clientToken = new String(CDP.getData());
                    if (serverToken != null && serverToken.equals(clientToken)) {
                        NetLog.info("重新建立的连接 [$] Token验证成功", channel.getRemoteAddress());
                    } else {
                        NetLog.warn("重新建立的连接 [$] Token验证失败,已断开", channel.getRemoteAddress());
                        cancelCommandLink(key);
                    }
                }

                default -> {
                    addDataPackage(CDP);
                }
            }

            NetLog.debug("接收 {$}", CDP);
        } catch (IOException e) {
            cancelCommandLink(key);
        } finally {
            receiveFinish(key);
        }
    }

    @Override
    protected void sendReceive(SelectionKey key, DataPackage dataPackage) {
        SocketChannel channel = (SocketChannel) key.channel();
        CommandPackage CDP = (CommandPackage) dataPackage;
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
            cancelCommandLink(key);
        } finally {
            sendFinish(key);
        }
    }

    @Override
    protected void extraDisposeTimeOutLink(SelectionKey key) {
        linkTable.cancel(key);
    }

    private void cancelCommandLink(SelectionKey key) {
        linkTable.cancel(key);
        cancel(key);
    }
}