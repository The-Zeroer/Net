package client.handler;

import client.Link;
import client.datapackage.CommandPackage;
import client.datapackage.DataPackage;
import client.datapackage.FilePackage;
import client.log.NetLog;
import server.util.TOOL;

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

public class FileHandler extends Handler {
    private String tempFilePath;

    public FileHandler(Link link) {
        super(link);
        tempFilePath = ".";
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
    public void receiveHandler(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        FilePackage FDP = new FilePackage();
        File tempFile = new File(getTempFileName());
        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw"); FileChannel fileChannel = raf.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate(FilePackage.HEADER_SIZE);
            while (buffer.hasRemaining()) {
                if (socketChannel.read(buffer) == -1) {
                    link.cancel(key, false);
                    return;
                }
            }
            buffer.flip();
            FDP.setWay(buffer.get()).setType(buffer.get()).setTime(buffer.getLong());
            FDP.setFile(tempFile).setFileSize(buffer.getLong());
            long fileSize = FDP.getFileSize();
            for (long residue = fileSize, readCount = 0; residue > 0; residue -= readCount) {
                readCount = fileChannel.transferFrom(socketChannel, fileSize - residue, residue);
                if (readCount > 0) {
                    NetLog.debug("接收文件中,剩余 [$],本次接收 [$]"
                            , DataPackage.formatBytes(residue - readCount), DataPackage.formatBytes(readCount));
                }
            }
            link.addDataPackage(FDP);
            NetLog.debug("接收 {$}", FDP);
        } catch (IOException e) {
            NetLog.error(e);
            link.cancel(key, true);
        } finally {
            link.receiveFinish(key);
        }
    }

    @Override
    public void sendHandle(SelectionKey key, DataPackage dataPackage) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        FilePackage FDP = (FilePackage) dataPackage;
        RandomAccessFile raf = null;
        FileChannel fileChannel = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(FilePackage.HEADER_SIZE);
            if (FDP.getWay() == DataPackage.WAY_TOKEN_VERIFY) {
                buffer.put(FDP.getWay()).put(FDP.getType()).putLong(FDP.getTime()).putLong(FDP.getDataSize());
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
                buffer.put(FDP.getWay()).put(FDP.getType()).putLong(FDP.getTime()).putLong(FDP.getFileSize());
                buffer.flip();
                while (buffer.hasRemaining()) {
                    socketChannel.write(buffer);
                }
                raf = new RandomAccessFile(FDP.getFile(), "r");
                fileChannel = raf.getChannel();
                long fileSize = FDP.getFileSize();
                for (long residue = fileSize, writeCount = 0; residue > 0; residue -= writeCount) {
                    writeCount = fileChannel.transferTo(fileSize - residue, residue, socketChannel);
                    if (writeCount > 0) {
                        NetLog.debug("发送文件中,剩余 [$],本次发送 [$]"
                                , DataPackage.formatBytes(residue - writeCount), DataPackage.formatBytes(writeCount));
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
                server.log.NetLog.error(e);
            }
        }
    }

    private synchronized String getTempFileName() {
        try {
            return tempFilePath + TOOL.getHashValue((String.valueOf(System.currentTimeMillis()) + UUID.randomUUID()).getBytes(), "MD5");
        } catch (NoSuchAlgorithmException e) {
            NetLog.error(e);
            return String.valueOf(System.currentTimeMillis());
        }
    }
}