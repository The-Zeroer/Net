package net.link;

import net.NetServer;
import net.util.LinkTable;
import net.datapackage.DataPackage;
import net.datapackage.FilePackage;
import net.log.NetLog;
import net.util.NetTool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class FileLink extends Link {
    private static final int BUFFER_MAX_SIZE = 8*1024;
    private String tempFilePath;

    public FileLink(NetServer netServer, LinkTable linkTable) throws IOException {
        super(netServer, linkTable);
        tempFilePath = ".\\";
    }

    public void setTempFilePath(String tempFilePath) throws FileNotFoundException {
        this.tempFilePath = tempFilePath + "\\";
        File tempFile = new File(tempFilePath);
        if (!tempFile.exists()) {
            if (!tempFile.mkdir()) {
                throw new FileNotFoundException(tempFilePath);
            }
        }
    }

    @Override
    protected void receiveReceive(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        FilePackage FDP = new FilePackage();
        File tempFile = new File(getTempFileName(key));
        RandomAccessFile raf = null;
        FileChannel fileChannel = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(FilePackage.HEADER_SIZE);
            while (buffer.hasRemaining()) {
                if (socketChannel.read(buffer) == -1) {
                    canelFileLink(key);
                    return;
                }
            }
            buffer.flip();
            FDP.setWay(buffer.get()).setType(buffer.get()).setAppendState(buffer.get()).setTime(buffer.getLong());
            if (FDP.getWay() != DataPackage.WAY_TOKEN_VERIFY && linkTable.getTokenByMessageKey(key) == null) {
                NetLog.warn("连接 [$] (FileLink) 无Token,已断开", socketChannel.getRemoteAddress());
                canelFileLink(key);
                return;
            }
            int dataSize = (int) buffer.getLong();
            byte[] taskIdBytes = new byte[buffer.getShort()];
            buffer = ByteBuffer.wrap(taskIdBytes);
            while (buffer.hasRemaining()) {
                socketChannel.read(buffer);
            }
            buffer.flip();
            buffer.get(taskIdBytes);
            FDP.setTaskId(new String(taskIdBytes));
            FDP.setSelectionKey(key).setUID(linkTable.getUIDByFileKey(key));

            if (FDP.getWay() == DataPackage.WAY_TOKEN_VERIFY) {
                if (dataSize > 0) {
                    byte[] data = new byte[dataSize];
                    buffer = ByteBuffer.allocate(Math.min(dataSize, BUFFER_MAX_SIZE));
                    for (int residue = dataSize, readCount = 0; residue > 0; residue -= readCount, readCount = 0) {
                        if (residue < buffer.remaining()) {
                            buffer.limit(residue);
                        }
                        while (buffer.hasRemaining()) {
                            readCount += socketChannel.read(buffer);
                        }
                        buffer.flip();
                        buffer.get(data, dataSize - residue, (Math.min(residue, buffer.remaining())));
                        buffer.clear();
                    }
                    FDP.setDataSize(dataSize).setData(data);
                }
                String token = new String(FDP.getData());
                SelectionKey commandKey = linkTable.getCommandKeyByToken(token);
                SelectionKey fileKey = linkTable.getFileKeyByToken(token);
                if (commandKey != null) {
                    if (fileKey != null) {
                        NetLog.warn("连接 [$] (FileLink) 已替换为 [$] ,原连接已断开"
                                , ((SocketChannel)fileKey.channel()).getRemoteAddress(), socketChannel.getRemoteAddress());
                        canelFileLink(fileKey);
                    }
                    String UID = linkTable.getUIDByCommandKey(commandKey);
                    linkTable.addFileKey(commandKey, key);
                    linkTable.setFileLinkStata(UID, LinkTable.VERIFY);
                    while (true) {
                        FilePackage filePackage = linkTable.getFilePackage(UID);
                        if (filePackage != null) {
                            putDataPackage(key, filePackage.setSelectionKey(key).setUID(UID));
                        } else {
                            linkTable.setFileLinkStata(UID, LinkTable.READY);
                            break;
                        }
                    }
                } else {
                    NetLog.warn("连接 [$] (FileLink) 已断开,Token错误", socketChannel.getRemoteAddress());
                    canelFileLink(key);
                }
            } else {
                raf = new RandomAccessFile(tempFile, "rw");
                fileChannel = raf.getChannel();
                long fileSize = dataSize;
                for (long residue = fileSize, readCount = 0; residue > 0; residue -= readCount) {
                    readCount = fileChannel.transferFrom(socketChannel, fileSize - residue, residue);
                }
                FDP.setFile(tempFile).setFileSize(fileSize);
                addDataPackage(FDP);
            }

            NetLog.debug("接收 {$}", FDP);
        } catch (IOException e) {
            canelFileLink(key);
        } finally {
            receiveFinish(key);
            try {
                if (raf != null) {
                    raf.close();
                }
                if (fileChannel != null) {
                    fileChannel.close();
                }
            } catch (IOException e) {
                NetLog.error(e);
            }
        }
    }

    @Override
    protected void sendReceive(SelectionKey key, DataPackage dataPackage) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        FilePackage FDP = (FilePackage) dataPackage;
        try (RandomAccessFile raf = new RandomAccessFile(FDP.getFile(), "r"); FileChannel fileChannel = raf.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate(FilePackage.HEADER_SIZE + FDP.getTaskIdLength());
            buffer.put(FDP.getWay()).put(FDP.getType()).put(FDP.getAppendState()).putLong(FDP.getTime())
                    .putLong(FDP.getFileSize()).putShort(FDP.getTaskIdLength()).put(FDP.getTaskIdBytes());
            buffer.flip();
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer);
            }
            long fileSize = FDP.getFileSize();
            for (long residue = fileSize, writeCount = 0; residue > 0; residue -= writeCount) {
                writeCount = fileChannel.transferTo(fileSize - residue, residue, socketChannel);
            }
            NetLog.debug("发送 {$} 成功", FDP);
        } catch (IOException e) {
            NetLog.error("发送 {$} 失败", FDP);
            canelFileLink(key);
        } finally {
            sendFinish(key);
        }
    }

    private void canelFileLink(SelectionKey key) {
        linkTable.removeFileKey(key);
        cancel(key);
    }

    private synchronized String getTempFileName(SelectionKey key) {
        try {
            return tempFilePath + NetTool.getHashValue((String.valueOf(System.currentTimeMillis()) + UUID.randomUUID() + key).getBytes(), "MD5");
        } catch (NoSuchAlgorithmException e) {
            NetLog.error(e);
            return String.valueOf(System.currentTimeMillis());
        }
    }
}