package net.log;

import java.util.concurrent.ConcurrentLinkedQueue;

public class NetLog extends Thread {
    public static final byte debug = 1;
    public static final byte info = 2;
    public static final byte error = 3;
    public static final byte warn = 4;
    public static final byte off = 5;

    private static final NetLog instance = new NetLog();
    private static ConcurrentLinkedQueue<NetLogPackage> logQueue;
    private static NetLogHandler netLogHandler;
    private static int maxLogCount, logCount;
    private static byte level;
    private static final Object lock = new Object();
    private static boolean handling = false;

    private NetLog(){
        maxLogCount = Integer.MAX_VALUE;
        logQueue = new ConcurrentLinkedQueue<>();
        start();
    }

    public static void setLevel(int level){
        NetLog.level = (byte)level;
    }

    public static void setMaxLogCount(int maxLogCount){
        NetLog.maxLogCount = maxLogCount;
    }

    public static void setLogHandler(NetLogHandler netLogHandler){
        NetLog.netLogHandler = netLogHandler;
    }

    public static void debug(String msg, Object... args){
        if (level <= debug){
            log(debug, msg, args);
        }
    }

    public static void info(String msg, Object... args){
        if (level <= info){
            log(info, msg, args);
        }
    }

    public static void warn(String msg, Object... args){
        if (level <= warn){
            log(warn, msg, args);
        }
    }

    public static void error(String msg, Object... args){
        if (level <= error){
            log(error, msg, args);
        }
    }

    public static void error(Exception e){
        if (level <= error){
            synchronized (NetLog.class){
                if (logCount < maxLogCount){
                    logCount++;
                } else {
                    return;
                }
            }
            logQueue.add(new NetLogPackage(System.currentTimeMillis(), e));
            if (!handling) {
                synchronized (lock) {
                    lock.notify();
                }
            }
        }
    }

    private static void log(byte level, String msg, Object... args){
        synchronized (NetLog.class){
            if (logCount < maxLogCount){
                logCount++;
            } else {
                return;
            }
        }
        logQueue.add(new NetLogPackage(System.currentTimeMillis(), level, msg, args));
        if (!handling) {
            synchronized (lock) {
                lock.notify();
            }
        }
    }

    @Override
    public void run(){
        while(true){
            while(logQueue.isEmpty()){
                synchronized (lock){
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            while(!logQueue.isEmpty()){
                if (netLogHandler != null) {
                    NetLogPackage netLogPackage = logQueue.poll();
                    if (netLogPackage != null) {
                        netLogHandler.handle(netLogPackage);
                        synchronized (NetLog.class) {
                            logCount--;
                        }
                    }
                }
            }
        }
    }
}