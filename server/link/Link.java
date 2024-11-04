package server.link;

import server.LinkTable;
import server.datapackage.DataPackage;
import server.log.NetLog;
import server.util.TokenBucket;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 负责管理连接的线程
 */

public abstract class Link extends Thread{
    protected final Selector selector;
    protected final LinkTable linkTable;
    protected final ConcurrentLinkedQueue<Runnable> eventQueue;
    protected final ExecutorService workPool;
    protected final ConcurrentHashMap<SelectionKey, AtomicBoolean> sendingStateHashMap;
    protected final ConcurrentHashMap<SelectionKey, Queue<DataPackage>> sendHashMap;
    protected final ConcurrentLinkedQueue<DataPackage> receiveQueue;
    protected final Set<SelectionKey> cancelSet;
    protected final TokenBucket tokenBucket;
    protected final Object linkLock = new Object(), sendLock = new Object(), receiveLock = new Object();
    protected int maxLinkCount, linkCount;
    protected boolean running;

    public Link(LinkTable linkTable) throws IOException {
        this.linkTable = linkTable;
        selector = Selector.open();
        eventQueue = new ConcurrentLinkedQueue<>();
        sendingStateHashMap = new ConcurrentHashMap<>();
        sendHashMap = new ConcurrentHashMap<>();
        receiveQueue = new ConcurrentLinkedQueue<>();
        cancelSet = ConcurrentHashMap.newKeySet();
        tokenBucket = new TokenBucket(2000, 1000);
        maxLinkCount = 1000;
        //初始化线程池
        int poolSize = Runtime.getRuntime().availableProcessors();
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1024);
        RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
        workPool = new ThreadPoolExecutor(poolSize, poolSize * 2, 180, TimeUnit.SECONDS, queue, policy);

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
            } catch (RejectedExecutionException e) {
                e.printStackTrace();
                NetLog.error(e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                NetLog.error(e.getMessage());
            }
        }
    }

    private void extracted() throws RejectedExecutionException, IOException {
        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            if (tokenBucket.acquire()) {
                if (key.isReadable()) {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_READ); // 清除 OP_READ 事件，但保留其他事件
                    workPool.submit(() -> {receiveReceive(key);});
                } else if (key.isWritable()) {
                    Queue<DataPackage> sendQueue = sendHashMap.get(key);
                    if (sendQueue != null) {
                        DataPackage dataPackage = sendQueue.poll();
                        if (dataPackage != null) {
                            if (sendingStateHashMap.get(key).compareAndSet(false, true)) { // 检查并设置状态
                                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE); // 清除 OP_WRITE 事件，但保留其他事件
                                workPool.submit(() -> {sendReceive(key, dataPackage);});
                            }
                        } else {
                            sendHashMap.remove(key);
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                        }
                    } else {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                    }
                }
                keys.remove();
            }
        }
    }

    public void setMaxLinkCount(int maxLinkCount) {
        this.maxLinkCount = maxLinkCount;
    }

    public synchronized void register(SocketChannel socketChannel) throws IOException {
        SocketAddress socketAddress = socketChannel.getRemoteAddress();
        int tempLinkCount;
        synchronized (linkLock) {
            tempLinkCount = linkCount + 1;
            if (tempLinkCount > maxLinkCount) {
                socketChannel.close();
                NetLog.warn("连接数到达最大值 [$] ,已断开连接 [$]", maxLinkCount, socketAddress);
                return;
            } else {
                linkCount++;
            }
        }
        socketChannel.configureBlocking(false);
        eventQueue.add(() -> {
            try {
                SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ);
                sendingStateHashMap.put(key, new AtomicBoolean(false));
                NetLog.info("连接 [$] 已注册至 [$] (当前注册数:$)", socketAddress, this.getName(), tempLinkCount);
            } catch (IOException e) {
                NetLog.error(e.getMessage());
            }
        });
        selector.wakeup();
    }
    protected synchronized void cancel(SelectionKey key) {
        cancelSet.add(key);
        switch (this.getName()) {
            case "CommandLink" -> {
                linkTable.cancel(key);
            }
            case "MessageLink" -> {
                linkTable.removeMessageKey(key);
            }
            case "FileLink" -> {
                linkTable.removeFileKey(key);
            }
        }
        int tempLinkCount;
        synchronized (linkLock) {
            linkCount--;
            tempLinkCount = linkCount;
        }
        SocketChannel socketChannel = (SocketChannel) key.channel();
        SocketAddress socketAddress = null;
        try {
            socketAddress = socketChannel.getRemoteAddress();
            socketChannel.close();
            NetLog.info("连接 [$] 已关闭", socketAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
        SocketAddress finalSocketAddress = socketAddress;
        eventQueue.add(() -> {
            key.cancel();
            cancelSet.remove(key);
            NetLog.info("连接 [$] 已从 [$] 中注销 (当前注册数:$)", finalSocketAddress, this.getName(), tempLinkCount);
        });
        selector.wakeup();
        sendingStateHashMap.remove(key);
        sendHashMap.remove(key);
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
                if (!sendingStateHashMap.get(key).get() && ((key.interestOps() & SelectionKey.OP_WRITE) == 0)) {
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

    protected void receiveFinish(SelectionKey key) {
        if (!cancelSet.contains(key) && key.isValid()) {
            eventQueue.add(() -> {
                if (key.isValid()) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                }
            });
            selector.wakeup();
        }
    }
    protected void sendFinish(SelectionKey key) {
        if (!cancelSet.contains(key) && key.isValid()) {
            sendingStateHashMap.get(key).set(false);
            if ((sendHashMap.get(key) != null) && ((key.interestOps() & SelectionKey.OP_WRITE) == 0)) {
                eventQueue.add(() -> {
                    if (key.isValid()) {
                        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    }
                });
                selector.wakeup();
            }
        }
    }
    protected void addDataPackage(DataPackage dataPackage) {
        receiveQueue.add(dataPackage);
        synchronized (receiveLock) {
            receiveLock.notify();
        }
    }

    protected abstract void receiveReceive(SelectionKey key);
    protected abstract void sendReceive(SelectionKey key, DataPackage dataPackage);
}