package net.handler;

import net.Link;
import net.datapackage.DataPackage;
import net.datapackage.FilePackage;
import net.log.NetLog;
import net.util.NetTool;
import net.util.TransferSchedule;

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
import java.util.concurrent.ConcurrentHashMap;

public class FileHandler extends Handler {
    private final ConcurrentHashMap<String, TransferSchedule> sendScheduleHashMap;
    private final ConcurrentHashMap<String, TransferSchedule> receiveScheduleHashMap;
    private String tempFilePath;

    public FileHandler(Link link) {
        super(link);
        tempFilePath = ".\\";
        sendScheduleHashMap = new ConcurrentHashMap<>();
        receiveScheduleHashMap = new ConcurrentHashMap<>();
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
    public void putSendTransferSchedule(String taskId, TransferSchedule schedule) {
        sendScheduleHashMap.put(taskId, schedule);
    }
    public void putReceiveTransferSchedule(String taskId, TransferSchedule schedule) {
        receiveScheduleHashMap.put(taskId, schedule);
    }

    @Override
    public void receiveHandler(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        FilePackage FDP = new FilePackage();
        File tempFile = new File(getTempFileName());
        RandomAccessFile raf = null;
        FileChannel fileChannel = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(FilePackage.HEADER_SIZE);
            while (buffer.hasRemaining()) {
                if (socketChannel.read(buffer) == -1) {
                    link.cancel(key, false);
                    return;
                }
            }
            buffer.flip();
            FDP.setWay(buffer.get()).setType(buffer.get()).setAppendState(buffer.get()).setTime(buffer.getLong());
            long fileSize = buffer.getLong();
            byte[] taskIdBytes = new byte[buffer.getShort()];
            buffer = ByteBuffer.wrap(taskIdBytes);
            while (buffer.hasRemaining()) {
                socketChannel.read(buffer);
            }
            buffer.flip();
            buffer.get(taskIdBytes);
            FDP.setTaskId(new String(taskIdBytes));
            if (fileSize > 0) {
                raf = new RandomAccessFile(tempFile, "rw");
                fileChannel = raf.getChannel();
                TransferSchedule transferSchedule = receiveScheduleHashMap.remove(FDP.getTaskId());
                if (transferSchedule != null) {
                    transferSchedule.showTransferSchedule(fileSize, fileSize);
                    for (long residue = fileSize, readCount = 0; residue > 0; residue -= readCount) {
                        readCount = fileChannel.transferFrom(socketChannel, fileSize - residue, residue);
                        if (readCount > 0) {
                            transferSchedule.showTransferSchedule(fileSize, residue);
                        }
                    }
                    transferSchedule.showTransferSchedule(fileSize, 0);
                } else {
                    for (long residue = fileSize, readCount = 0; residue > 0; residue -= readCount) {
                        readCount = fileChannel.transferFrom(socketChannel, fileSize - residue, residue);
                    }
                }
                FDP.setFile(tempFile).setFileSize(fileSize);
            }
            link.addDataPackage(FDP);
            NetLog.debug("接收 {$}", FDP);
        } catch (IOException e) {
            NetLog.error(e);
            link.cancel(key, true);
        } finally {
            link.receiveFinish(key);
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
    public void sendHandle(SelectionKey key, DataPackage dataPackage) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        FilePackage FDP = (FilePackage) dataPackage;
        RandomAccessFile raf = null;
        FileChannel fileChannel = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(FilePackage.HEADER_SIZE + FDP.getTaskIdLength());
            if (FDP.getWay() == DataPackage.WAY_TOKEN_VERIFY) {
                buffer.put(FDP.getWay()).put(FDP.getType()).put(FDP.getAppendState()).putLong(FDP.getTime())
                        .putLong(FDP.getDataSize()).putShort(FDP.getTaskIdLength()).put(FDP.getTaskIdBytes());
                buffer.flip();
                while (buffer.hasRemaining()) {
                    socketChannel.write(buffer);
                }
                int dataSize = FDP.getDataSize();
                if (dataSize > 0) {
                    buffer = ByteBuffer.allocate(Math.min(dataSize, BUFFER_MAX_SIZE));
                    for (int residue = dataSize, writeCount = 0; residue > 0;residue -= writeCount, writeCount = 0) {
                        buffer.put(FDP.getData(), dataSize - residue, Math.min(residue, buffer.remaining()));
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            writeCount += socketChannel.write(buffer);
                        }
                        buffer.clear();
                    }
                }
            } else {
                buffer.put(FDP.getWay()).put(FDP.getType()).put(FDP.getAppendState()).putLong(FDP.getTime())
                        .putLong(FDP.getFileSize()).putShort(FDP.getTaskIdLength()).put(FDP.getTaskIdBytes());
                buffer.flip();
                while (buffer.hasRemaining()) {
                    socketChannel.write(buffer);
                }
                raf = new RandomAccessFile(FDP.getFile(), "r");
                fileChannel = raf.getChannel();
                long fileSize = FDP.getFileSize();
                TransferSchedule transferSchedule = receiveScheduleHashMap.remove(FDP.getTaskId());
                if (transferSchedule != null) {
                    transferSchedule.showTransferSchedule(fileSize, fileSize);
                    for (long residue = fileSize, writeCount = 0; residue > 0; residue -= writeCount) {
                        writeCount = fileChannel.transferTo(fileSize - residue, residue, socketChannel);
                        if (writeCount > 0) {
                            transferSchedule.showTransferSchedule(fileSize, residue);
                        }
                    }
                    transferSchedule.showTransferSchedule(fileSize, 0);
                } else {
                    for (long residue = fileSize, writeCount = 0; residue > 0; residue -= writeCount) {
                        writeCount = fileChannel.transferTo(fileSize - residue, residue, socketChannel);
                    }
                }
            }

            NetLog.debug("发送 {$} 成功", FDP);
        } catch (IOException e) {
            NetLog.error("发送 {$} 失败", FDP);
            link.cancel(key, true);
        } finally {
            link.sendFinish(key);
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

    private synchronized String getTempFileName() {
        try {
            return tempFilePath + NetTool.getHashValue((String.valueOf(System.currentTimeMillis()) + UUID.randomUUID()).getBytes(), "MD5");
        } catch (NoSuchAlgorithmException e) {
            NetLog.error(e);
            return String.valueOf(System.currentTimeMillis());
        }
    }
}