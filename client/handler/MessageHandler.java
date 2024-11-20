package net.handler;

import net.Link;
import net.datapackage.DataPackage;
import net.datapackage.MessagePackage;
import net.log.NetLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class MessageHandler extends Handler{

    public MessageHandler(Link link) {
        super(link);
    }

    @Override
    public void receiveHandler(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        MessagePackage MDP = new MessagePackage();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(MessagePackage.HEADER_SIZE);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) {
                    link.cancel(key, false);
                    return;
                }
            }
            buffer.flip();
            MDP.setWay(buffer.get()).setType(buffer.get()).setAppendState(buffer.get())
                    .setTime(buffer.getLong()).setDataSize(buffer.getInt());
            byte[] senderBytes = new byte[buffer.getShort()];
            byte[] receiverBytes = new byte[buffer.getShort()];
            byte[] taskIdBytes = new byte[buffer.getShort()];
            buffer = ByteBuffer.allocate(senderBytes.length + receiverBytes.length + taskIdBytes.length);
            while (buffer.hasRemaining()) {
                channel.read(buffer);
            }
            buffer.flip();
            buffer.get(senderBytes).get(receiverBytes).get(taskIdBytes);
            MDP.setSender(new String(senderBytes)).setReceiver(new String(receiverBytes)).setTaskId(new String(taskIdBytes));
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

            NetLog.debug("接收 {$}", MDP);

            link.addDataPackage(MDP);
        } catch (IOException e) {
            link.cancel(key, true);
        } finally {
            link.receiveFinish(key);
        }
    }

    @Override
    public void sendHandle(SelectionKey key, DataPackage dataPackage) {
        MessagePackage MDP = (MessagePackage) dataPackage;
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buffer = ByteBuffer.allocate(MessagePackage.HEADER_SIZE
                    + MDP.getTaskIdLength() + MDP.getSenderLenght() + MDP.getReceiverLenght());
            buffer.put(MDP.getWay()).put(MDP.getType()).put(MDP.getAppendState()).putLong(MDP.getTime()).putInt(MDP.getDataSize())
                    .putShort(MDP.getSenderLenght()).putShort(MDP.getReceiverLenght()).putShort(MDP.getTaskIdLength())
                    .put(MDP.getSenderBytes()).put(MDP.getReceiverBytes()).put(MDP.getTaskIdBytes());
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            int dataSize = MDP.getDataSize();
            if (dataSize > 0) {
                buffer = ByteBuffer.allocate(Math.min(dataSize, BUFFER_MAX_SIZE));
                byte[] data = MDP.getData();
                for (int residue = dataSize, writeCount = 0; residue > 0;residue -= writeCount, writeCount = 0) {
                    buffer.put(data, dataSize - residue, Math.min(residue, buffer.remaining()));
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        writeCount += channel.write(buffer);
                    }
                    buffer.clear();
                }
            }
            NetLog.debug("发送 {$} 成功", MDP);
        } catch (IOException e) {
            NetLog.error("发送 {$} 失败", MDP);
            link.cancel(key, true);
        } finally {
            link.sendFinish(key);
        }
    }
}