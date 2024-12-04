package net.util;

import net.datapackage.DataPackage;

public abstract class Task implements Runnable{
    private final Object lock = new Object();
    protected final String taskId;
    private DataPackage dataPackage;

    public Task() {
        this.taskId = NetTool.produceTaskId();
    }
    public Task(String taskId) {
        this.taskId = taskId;
    }

    public abstract void run();

    public DataPackage waitDataPackage(int seconds) {
        synchronized (lock) {
            try {
                lock.wait(seconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return dataPackage;
        }
    }
    public void handleDataPackage(DataPackage dataPackage) {
        synchronized (lock) {
            this.dataPackage = dataPackage;
            lock.notifyAll();
        }
    }

    public String getTaskId() {
        return taskId;
    }
}