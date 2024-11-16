package net.util;

import net.datapackage.DataPackage;

public abstract class Task implements Runnable{
    private final Object lock = new Object();
    private final String taskId;
    private DataPackage data;

    public Task(String taskId) {
        this.taskId = taskId;
    }

    public abstract void run();

    public DataPackage waitData(int seconds) {
        synchronized (lock) {
            try {
                lock.wait(seconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return data;
        }
    }
    public void handleData(DataPackage data) {
        synchronized (lock) {
            this.data = data;
            lock.notifyAll();
        }
    }

    public String getTaskId() {
        return taskId;
    }
}