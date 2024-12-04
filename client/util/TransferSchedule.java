package net.util;

public interface TransferSchedule {
    public void setSize(long size);
    public void updateSchedule(long portion);
    public void transFinish();
}