package net.datapackage;

import net.util.NetTool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class FilePackage extends DataPackage {
    public static final int HEADER_SIZE = 21;

    private long fileSize;
    private File file;

    public FilePackage() {}
    public FilePackage(byte way, byte[] data) {
        super(way, data);
    }
    public FilePackage(File file) throws FileNotFoundException {
        this(FilePackage.WAY_SEND_DATA, FilePackage.TYPE_FILE, file);
    }
    public FilePackage(byte way, byte type, File file) throws FileNotFoundException {
        this.way = way;
        this.type = type;
        this.time = System.currentTimeMillis();
        if (file != null && file.exists()) {
            this.file = file;
            fileSize = file.length();
        } else {
            throw new FileNotFoundException();
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

    public boolean moveFile(File destFile) {
        try {
            NetTool.moveFile(file, destFile);
            file = destFile;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+ " [way=" + way + ", type=" + type + ", time=" + dateFormat.format(time)
                + " ,fileSize=" + formatBytes(fileSize) + ", taskId=" + new String(taskId) + "]";
    }
}