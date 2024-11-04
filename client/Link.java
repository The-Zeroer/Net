package client;

import client.datapackage.DataPackage;
import client.handler.CommandHandler;
import client.handler.FileHandler;
import client.handler.Handler;
import client.handler.MessageHandler;
import client.log.NetLog;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;

public class Link extends Thread{
    private final Selector selector;
    private final LinkTable linkTable;
    private final ConcurrentHashMap<SelectionKey, Handler> relevancyHashMap;
    private final ConcurrentLinkedQueue<Runnable> eventQueue;
    private final ExecutorService workPool;
    private final ConcurrentHashMap<SelectionKey, Queue<DataPackage>> sendHashMap;
    private final ConcurrentLinkedQueue<DataPackage> receiveQueue;
    private final Set<SelectionKey> cancelSet;
    protected final Object sendLock = new Object(), receiveLock = new Object();
    protected boolean running;

    public Link(LinkTable linkTable) throws IOException {
        this.linkTable = linkTable;
        selector = Selector.open();
        relevancyHashMap = new ConcurrentHashMap<>();
        eventQueue = new ConcurrentLinkedQueue<>();
        sendHashMap = new ConcurrentHashMap<>();
        receiveQueue = new ConcurrentLinkedQueue<>();
        cancelSet = ConcurrentHashMap.newKeySet();

        int poolSize = Runtime.getRuntime().availableProcessors();
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(128);
        RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
        workPool = new ThreadPoolExecutor(6, poolSize*2, 180, TimeUnit.SECONDS, queue, policy);

        running = true;
        this.start();
    }

    @Override
    public void run() {
        while (running) {
            try {
                while (eventQueue.isEmpty() && selector.select() > 0) {
                    extracted();
                }
                if (selector.selectNow() > 0) {
                    extracted();
                }

                Iterator<Runnable> tasks = eventQueue.iterator();
                while (tasks.hasNext()) {
                    tasks.next().run();
                    tasks.remove();
                }
            } catch (IOException e) {
                NetLog.error(e.getMessage());
            }
        }
    }

    private void extracted() {
        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            Handler handler = relevancyHashMap.get(key);
            if (key.isReadable()) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                workPool.submit(() -> {handler.receiveHandler(key);});
            } else if (key.isWritable()) {
                Queue<DataPackage> sendQueue = sendHashMap.get(key);
                if (sendQueue != null) {
                    DataPackage dataPackage = sendQueue.poll();
                    if (dataPackage != null) {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                        workPool.submit(() -> {handler.sendHandle(key, dataPackage);});
                    } else {
                        sendHashMap.remove(key);
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                    }
                } else {
                    sendHashMap.remove(key);
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                }
            }
            keys.remove();
        }
    }

    public void register(SocketChannel socketChannel, Handler handler) throws IOException {
        socketChannel.configureBlocking(false);
        eventQueue.add(() -> {
            try {
                SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ);
                relevancyHashMap.put(key, handler);
                switch (handler) {
                    case CommandHandler commandHandler -> {
                        linkTable.putCommandKey(key);
                        NetLog.info("连接 [commandLink] 已建立");
                    }
                    case MessageHandler messageHandler -> {
                        linkTable.putMessageKey(key);
                        NetLog.info("连接 [messageLink] 已建立");
                    }
                    case FileHandler fileHandler -> {
                        linkTable.putFileKey(key);
                        NetLog.info("连接 [fileLink] 已建立");
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + handler);
                }
            } catch (IOException e) {
                NetLog.error(e.getMessage());
            }
        });
        selector.wakeup();
    }
    public void cancel(SelectionKey key) {
        cancelSet.add(key);
        switch (relevancyHashMap.remove(key)) {
            case CommandHandler commandHandler -> {
                NetLog.info("连接 [commandLink] 已断开");
            }
            case MessageHandler messageHandler -> {
                linkTable.removeMessageKey();
                NetLog.info("连接 [messageLink] 已断开");
            }
            case FileHandler fileHandler -> {
                linkTable.removeFileKey();
                NetLog.info("连接 [fileLink] 已断开");
            }
            default -> {
                NetLog.warn("连接已注销");
                throw new IllegalStateException();
            }
        }
        eventQueue.add(() -> {
            key.cancel();
            cancelSet.remove(key);
        });
        selector.wakeup();
    }

    public DataPackage getDataPackage(boolean wait){
        synchronized (receiveLock) {
            if (wait) {
                while (receiveQueue.isEmpty()) {
                    try {
                        receiveLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            return receiveQueue.poll();
        }
    }
    public void putDataPackage(SelectionKey key, DataPackage dataPackage) {
        synchronized (sendLock) {
            if (key.isValid()) {
                Queue<DataPackage> sendQueue = sendHashMap.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
                sendQueue.add(dataPackage);
                if ((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
                    eventQueue.add(() -> {
                        if (key.isValid()) {
                            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                        }
                    });
                    selector.wakeup();
                }
            }
        }
    }

    public void receiveFinish(SelectionKey key) {
        if (!cancelSet.contains(key) && key.isValid()) {
            eventQueue.add(() -> {
                if (key.isValid()) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                }
            });
            selector.wakeup();
        }
    }
    public void sendFinish(SelectionKey key) {
        if (!cancelSet.contains(key) && key.isValid()) {
            eventQueue.add(() -> {
                if (key.isValid()) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                }
            });
            selector.wakeup();
        }
    }
    public void addDataPackage(DataPackage dataPackage) {
        receiveQueue.add(dataPackage);
        synchronized (receiveLock) {
            receiveLock.notify();
        }
    }
}