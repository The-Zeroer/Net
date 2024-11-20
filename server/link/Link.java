package net.link;

import net.NetServer;
import net.datapackage.CommandPackage;
import net.datapackage.FilePackage;
import net.datapackage.MessagePackage;
import net.util.LinkTable;
import net.datapackage.DataPackage;
import net.exception.NetException;
import net.log.NetLog;
import net.util.NetTool;
import net.util.TokenBucket;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 负责管理连接的线程
 */

public abstract class Link extends Thread{
    protected final NetServer netServer;
    protected final Selector selector;
    protected final LinkTable linkTable;
    protected final ExecutorService workPool;
    protected final ConcurrentLinkedQueue<Runnable> eventQueue;
    protected final ConcurrentLinkedQueue<NetException> exceptionQueue;
    protected final ConcurrentHashMap<SelectionKey, AtomicBoolean> sendingStateHashMap;
    protected final ConcurrentHashMap<SelectionKey, Queue<DataPackage>> sendHashMap;
    protected final ConcurrentLinkedQueue<DataPackage> receiveQueue;
    protected final Set<SelectionKey> cancelSet;
    protected final TokenBucket tokenBucket;
    protected final Object linkLock = new Object(), sendLock = new Object(), receiveLock = new Object();
    protected int maxLinkCount, linkCount;
    protected boolean running;

    protected final HeartBeat heartBeat;

    public Link(NetServer netServer, LinkTable linkTable) throws IOException {
        this.netServer = netServer;
        this.linkTable = linkTable;
        selector = Selector.open();
        eventQueue = new ConcurrentLinkedQueue<>();
        exceptionQueue = new ConcurrentLinkedQueue<>();
        sendingStateHashMap = new ConcurrentHashMap<>();
        sendHashMap = new ConcurrentHashMap<>();
        receiveQueue = new ConcurrentLinkedQueue<>();
        cancelSet = ConcurrentHashMap.newKeySet();
        tokenBucket = new TokenBucket(2000, 1000);
        maxLinkCount = 1000;

        int poolSize = Runtime.getRuntime().availableProcessors();
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1024);
        RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
        workPool = new ThreadPoolExecutor(poolSize, poolSize * 2, 180, TimeUnit.SECONDS, queue, policy);

        heartBeat = new HeartBeat();
    }

    @Override
    public void start() {
        running = true;
        super.start();
        heartBeat.start();
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
            } catch (RejectedExecutionException | IOException e) {
                NetLog.error(e);
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
                    heartBeat.updateLastActivityTime(key);
                    workPool.submit(() -> {receiveReceive(key);});
                } else if (key.isWritable()) {
                    Queue<DataPackage> sendQueue = sendHashMap.get(key);
                    if (sendQueue != null) {
                        DataPackage dataPackage = sendQueue.poll();
                        if (dataPackage != null) {
                            if (sendingStateHashMap.get(key).compareAndSet(false, true)) { // 检查并设置状态
                                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE); // 清除 OP_WRITE 事件，但保留其他事件
                                heartBeat.updateLastActivityTime(key);
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
    public void setHeartBeatInterval(int heartBeatInterval) {
        heartBeat.setHeartBeatInterval(heartBeatInterval);
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
                heartBeat.updateLastActivityTime(key);
                NetLog.info("连接 [$] 已注册至 [$] (当前注册数:$)", socketAddress, getName(), tempLinkCount);
            } catch (IOException e) {
                NetLog.error(e);
            }
        });
        selector.wakeup();
    }
    public synchronized void cancel(SelectionKey key) {
        if (sendingStateHashMap.remove(key) != null) {
            cancelSet.add(key);
            sendHashMap.remove(key);
            heartBeat.removeLastActivityTime(key);
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
                NetLog.error(e);
            }
            SocketAddress finalSocketAddress = socketAddress;
            eventQueue.add(() -> {
                key.cancel();
                cancelSet.remove(key);
                NetLog.info("连接 [$] 已从 [$] 中注销 (当前注册数:$)", finalSocketAddress, getName(), tempLinkCount);
            });
            selector.wakeup();
        }
    }

    public DataPackage getDataPackage() throws NetException {
        synchronized (receiveLock) {
            while (receiveQueue.isEmpty()) {
                try {
                    receiveLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    throwException();
                }
            }
            return receiveQueue.poll();
        }
    }
    public void putDataPackage(SelectionKey key, DataPackage dataPackage) {
        if (dataPackage.getTaskId() == null) {
            dataPackage.setTaskId(NetTool.produceTaskId());
        }
        synchronized (sendLock) {
            if (key != null && key.isValid() && sendingStateHashMap.containsKey(key)) {
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
        if (dataPackage.getAppendState() == DataPackage.APPEND_1) {
            DataPackage DP = dataPackage.getAppendDataPackage();
            String taskId = dataPackage.getTaskId();
            if (!taskId.equals(DP.getTaskId())) {
                DP.setTaskId(taskId);
            }
            switch (DP) {
                case CommandPackage commandPackage -> netServer.putCommandPackage(dataPackage.getUID(), commandPackage);
                case MessagePackage messagePackage -> netServer.putMessagePackage(dataPackage.getUID(), messagePackage);
                case FilePackage filePackage -> netServer.putFilePackage(dataPackage.getUID(), filePackage);
                default -> throw new IllegalStateException();
            }
        }
    }

    protected void receiveFinish(SelectionKey key) {
        if (key != null && key.isValid() && !cancelSet.contains(key)) {
            eventQueue.add(() -> {
                if (key.isValid()) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                }
            });
            selector.wakeup();
        }
    }
    protected void sendFinish(SelectionKey key) {
        if (key != null && key.isValid() && !cancelSet.contains(key)) {
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
        if (dataPackage.getAppendState() == DataPackage.APPEND_1 || dataPackage.getAppendState() == DataPackage.APPEND_2) {
            synchronized (dataPackage.getUID()) {
                DataPackage tempDataPackage = linkTable.getAppendDataPackage(dataPackage.getTaskId());
                if (tempDataPackage != null) {
                    DataPackage DP;
                    if (tempDataPackage.getAppendState() == DataPackage.APPEND_1) {
                        DP = tempDataPackage.addAppendDataPackage(dataPackage);
                    } else {
                        DP = dataPackage.addAppendDataPackage(tempDataPackage);
                    }
                    switch (DP) {
                        case CommandPackage commandPackage -> {
                            linkTable.getCommandLink().receiveQueue.add(commandPackage);
                            synchronized (linkTable.getCommandLink().receiveLock) {
                                linkTable.getCommandLink().receiveLock.notify();
                            }
                        }
                        case MessagePackage messagePackage -> {
                            linkTable.getMessageLink().receiveQueue.add(messagePackage);
                            synchronized (linkTable.getMessageLink().receiveLock) {
                                linkTable.getMessageLink().receiveLock.notify();
                            }
                        }
                        case FilePackage filePackage -> {
                            linkTable.getFileLink().receiveQueue.add(filePackage);
                            synchronized (linkTable.getFileLink().receiveLock) {
                                linkTable.getFileLink().receiveLock.notify();
                            }
                        }
                        default -> throw new IllegalStateException();
                    }
                } else {
                    linkTable.putAppendDataPackage(dataPackage.getTaskId(), dataPackage);
                }
            }
        } else {
            receiveQueue.add(dataPackage);
            synchronized (receiveLock) {
                receiveLock.notify();
            }
        }
    }

    public void addException(NetException netException) {
        exceptionQueue.add(netException);
        synchronized (receiveLock) {
            receiveLock.notify();
        }
    }
    protected void throwException() throws NetException {
        NetException netException = exceptionQueue.poll();
        if (netException != null) {
            throw netException;
        }
    }

    protected abstract void receiveReceive(SelectionKey key);
    protected abstract void sendReceive(SelectionKey key, DataPackage dataPackage);
    protected void extraDisposeTimeOutLink(SelectionKey key) {}

    protected class HeartBeat {
        private final ConcurrentHashMap<SelectionKey, Long> lastActivityTime;
        private final Timer timer;
        private long HEARTBEAT_INTERVAL = 90000;
        private String name;

        protected HeartBeat() {
            lastActivityTime = new ConcurrentHashMap<>();
            timer = new Timer(true);
        }

        public void setHeartBeatInterval(int interval) {
            HEARTBEAT_INTERVAL = interval * 1000L;
        }

        protected void start() {
            name = Link.this.getName();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    long nowTime = System.currentTimeMillis();
                    for (Map.Entry<SelectionKey, Long> entry : lastActivityTime.entrySet()) {
                        if (nowTime - entry.getValue() > HEARTBEAT_INTERVAL) {
                            breakLink(entry.getKey());
                            lastActivityTime.remove(entry.getKey());
                        }
                    }
                }
            }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL);
            NetLog.info("已开启心跳检测 [$] ,间隔 [$] 秒",name, HEARTBEAT_INTERVAL / 1000);
        }

        protected void updateLastActivityTime(SelectionKey key) {
            lastActivityTime.put(key, System.currentTimeMillis());
        }
        protected void removeLastActivityTime(SelectionKey key) {
            lastActivityTime.remove(key);
        }

        private void breakLink(SelectionKey key) {
            if (key.channel().isOpen()) {
                try {
                    NetLog.debug("超时未收到心跳包 [$] ,已断开连接 [$]",name, ((SocketChannel)key.channel()).getRemoteAddress());
                } catch (IOException e) {
                    NetLog.debug("超时未收到心跳包 [$] ,已断开连接 [$]",name, key.channel());
                }
                cancel(key);
                extraDisposeTimeOutLink(key);
            } else {
                lastActivityTime.remove(key);
            }
        }
    }
}