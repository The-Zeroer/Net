package client.datapackage;

import java.io.File;
import java.io.FileNotFoundException;

public class FilePackage extends DataPackage {
    public static final int HEADER_SIZE = 20;

    private long fileSize;
    private File file;

    public FilePackage() {}


    public FilePackage(byte way, byte[] data) {
        super(way, data);
    }

    public FilePackage(byte way, byte type, byte[] data) {
        super(way, type, data);
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

    @Override
    public String toString() {
        return getClass().getSimpleName()+ " [way=" + way + ", type=" + type + ", time=" + dateFormat.format(time)
                + " ,fileSize=" + formatBytes(fileSize) + ", taskId=" + new String(taskId) + "]";
    }
}