package net.datapackage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.SocketChannel;

public class FilePackage extends DataPackage {
    public static final int HEADER_SIZE = 21;
    private long fileSize;
    private File file;

    public FilePackage() {}

    public FilePackage(byte way, byte[] data) {
        this(way, TYPE_TEXT, data);
    }

    public FilePackage(byte way, byte type, byte[] data) {
        this.way = way;
        this.type = type;
        this.time = System.currentTimeMillis();
        if (data != null) {
            this.data = data;
            dataSize += data.length;
        }
    }

    public FilePackage(byte way, byte type, File file) {
        this.way = way;
        this.type = type;
        this.time = System.currentTimeMillis();

        if (file != null && file.exists()) {
            this.file = file;
            fileSize = file.length();
        }
    }

    public File getFile() {
        return file;
    }
    public long getFileSize() {
        return fileSize;
    }
    public FilePackage setFile(File file) {
        this.file = file;
        return this;
    }
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean moveFile(File destFile) throws IOException {
        return file.renameTo(destFile);
    }

    @Override
    public String toString() {
        String address = null;
        if (key != null) {
            try {
                address = ((SocketChannel)key.channel()).getRemoteAddress().toString();
            } catch (IOException e) {
                address = key.channel().toString();
            }
        }
        return getClass().getSimpleName() + " [RemoteAddress=" + address + ", UID=" + UID
                + ", way=" + way + ", type=" + type + ", time=" + dateFormat.format(time)
                + ", fileSize=" + formatBytes(fileSize) + ", taskId=" + new String(taskId) + "]";
    }
}