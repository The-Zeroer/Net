package server.link;

import server.LinkTable;
import server.datapackage.CommandPackage;
import server.datapackage.DataPackage;
import server.log.NetLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class CommandLink extends Link {
    private static final int BUFFER_MAX_SIZE = 8*1024;
    private String messageAddress, fileAddress;

    public CommandLink(LinkTable linkTable) throws IOException {
        super(linkTable);
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
            ByteBuffer buffer = ByteBuffer.allocate(14);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) {
                    cancel(key);
                    return;
                }
            }
            buffer.flip();
            CDP.setPackageSize(buffer.getInt()).setWay(buffer.get()).setType(buffer.get()).setTime(buffer.getLong());
            // 验证Token
            if (CDP.getWay() != DataPackage.WAY_LOGIN && linkTable.getTokenByCommandKey(key) == null) {
                NetLog.warn("连接 [$] 无Token,已断开", channel.getRemoteAddress());
                cancel(key);
                return;
            }
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

            CDP.setSelectionKey(key).setUID(linkTable.getUIDByCommandKey(key));
            NetLog.debug("接收 {$}", CDP);

            switch (CDP.getWay()) {
                case DataPackage.WAY_HEART_BEAT -> {

                }

                case DataPackage.WAY_BUILD_LINK -> {
                    switch (CDP.getType()) {
                        case DataPackage.TYPE_MESSAGE_ADDRESS -> {
                            putDataPackage(key, new CommandPackage(DataPackage.WAY_BUILD_LINK
                                    , DataPackage.TYPE_MESSAGE_ADDRESS, messageAddress.getBytes())
                                    .setSelectionKey(key).setUID(linkTable.getUIDByCommandKey(key)));
                        }
                        case DataPackage.TYPE_FILE_ADDRESS -> {
                            putDataPackage(key, new CommandPackage(DataPackage.WAY_BUILD_LINK
                                    , DataPackage.TYPE_FILE_ADDRESS, fileAddress.getBytes())
                                    .setSelectionKey(key).setUID(linkTable.getUIDByCommandKey(key)));
                        }
                    }
                }

                default -> {
                    addDataPackage(CDP);
                }
            }
        } catch (IOException e) {
            cancel(key);
        } finally {
            receiveFinish(key);
        }
    }

    @Override
    protected void sendReceive(SelectionKey key, DataPackage dataPackage) {
        SocketChannel channel = (SocketChannel) key.channel();
        CommandPackage CDP = (CommandPackage) dataPackage;
        try {
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
            NetLog.debug("发送成功 {$}", CDP);
        } catch (IOException e) {
            NetLog.debug("发送失败 {$}", CDP);
            cancel(key);
        } finally {
            sendFinish(key);
        }
    }
}
