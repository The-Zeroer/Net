package server.link;

import server.LinkTable;
import server.datapackage.DataPackage;
import server.datapackage.MessagePackage;
import server.log.NetLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class MessageLink extends Link {
    private static final int BUFFER_MAX_SIZE = 8*1024;
    public MessageLink(LinkTable linkTable) throws IOException {
        super(linkTable);
    }

    @Override
    protected void receiveReceive(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        MessagePackage MDP = new MessagePackage();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(MessagePackage.HEADER_SIZE);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) {
                    canelMessageLink(key);
                    return;
                }
            }
            buffer.flip();
            MDP.setWay(buffer.get()).setType(buffer.get()).setTime(buffer.getLong()).setDataSize(buffer.getInt());
            if (MDP.getWay() != DataPackage.WAY_TOKEN_VERIFY && linkTable.getTokenByMessageKey(key) == null) {
                NetLog.warn("连接 [$] (MessageLink) 无Token,已断开", channel.getRemoteAddress());
                canelMessageLink(key);
                return;
            }
            int dataSize = MDP.getDataSize();
            if (dataSize > 0) {
                byte[] data = new byte[dataSize];
                buffer = ByteBuffer.allocate(Math.min(dataSize, BUFFER_MAX_SIZE));
                for (int residue = dataSize, readCount = 0; residue > 0; residue -= readCount, readCount = 0) {
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
                MDP.setData(data);
            }

            if (MDP.getWay() == DataPackage.WAY_TOKEN_VERIFY) {
                String token = new String(MDP.getData());
                SelectionKey commandKey = linkTable.getCommandKeyByToken(token);
                SelectionKey messageKey = linkTable.getMessageKeyByToken(token);
                if (commandKey != null) {
                    if (messageKey != null) {
                        NetLog.warn("连接 [$] (MessageLink) 已替换为 [$] ,原连接已断开"
                                , ((SocketChannel)messageKey.channel()).getRemoteAddress(), channel.getRemoteAddress());
                        canelMessageLink(messageKey);
                    }
                    String UID = linkTable.getUIDByCommandKey(commandKey);
                    linkTable.addMessageKey(commandKey, key);
                    linkTable.setMessageLinkStata(UID, LinkTable.VERIFY);
                    while (true) {
                        MessagePackage messagePackage = linkTable.getMessagePackage(UID);
                        if (messagePackage != null) {
                            putDataPackage(key, messagePackage.setSelectionKey(key).setUID(UID));
                        } else {
                            linkTable.setMessageLinkStata(UID, LinkTable.READY);
                            break;
                        }
                    }
                } else {
                    NetLog.warn("连接 [$] (MessageLink) 已断开,Token错误", channel.getRemoteAddress());
                    canelMessageLink(key);
                }
            } else {
                addDataPackage(MDP);
            }

            MDP.setSelectionKey(key).setUID(linkTable.getUIDByMessageKey(key));
            NetLog.debug("接收 {$}", MDP);
        } catch (IOException e) {
            canelMessageLink(key);
        } finally {
            receiveFinish(key);
        }
    }

    @Override
    protected void sendReceive(SelectionKey key, DataPackage dataPackage) {
        SocketChannel channel = (SocketChannel) key.channel();
        MessagePackage MDP = (MessagePackage) dataPackage;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(MessagePackage.HEADER_SIZE);
            buffer.put(MDP.getWay()).put(MDP.getType()).putLong(MDP.getTime()).putInt(MDP.getDataSize());
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            int dataSize = MDP.getDataSize();
            if (dataSize > 0) {
                buffer = ByteBuffer.allocate(Math.min(dataSize, BUFFER_MAX_SIZE));
                for (int residue = dataSize, writeCount = 0; residue > 0;residue -= writeCount, writeCount = 0) {
                    buffer.put(MDP.getData(), dataSize - residue, Math.min(residue, buffer.remaining()));
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        writeCount += channel.write(buffer);
                    }
                    buffer.clear();
                }
            }
            NetLog.debug("发送成功 {$}", MDP);
        } catch (IOException e) {
            NetLog.error("发送失败 {$}", MDP);
            canelMessageLink(key);
        } finally {
            sendFinish(key);
        }
    }

    private void canelMessageLink(SelectionKey key) {
        linkTable.removeMessageKey(key);
        cancel(key);
    }
}