package client;

import client.datapackage.CommandPackage;
import client.datapackage.DataPackage;
import client.exception.NetException;
import client.exception.link.AgainLinkTimeOutException;
import client.exception.link.ServerCloseLinkException;
import client.exception.token.TokenMissingException;
import client.handler.CommandHandler;
import client.handler.FileHandler;
import client.handler.Handler;
import client.handler.MessageHandler;
import client.log.NetLog;
import client.util.LinkTable;
import client.util.NetTool;

import java.io.IOException;
import java.net.InetSocketAddress;
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
    private final ExecutorService workPool;
    private final ConcurrentLinkedQueue<Runnable> eventQueue;
    private final ConcurrentLinkedQueue<NetException> exceptionQueue;
    private final ConcurrentHashMap<SelectionKey, Handler> relevancyHashMap;
    private final ConcurrentHashMap<SelectionKey, Queue<DataPackage>> sendHashMap;
    private final ConcurrentLinkedQueue<DataPackage> receiveQueue;
    private final Set<SelectionKey> cancelSet;
    private final Object sendLock = new Object(), receiveLock = new Object();
    private InetSocketAddress serverAddress;
    private boolean running;

    private final HeartBeat heartBeat;

    public Link(LinkTable linkTable) throws IOException {
        this.linkTable = linkTable;
        selector = Selector.open();
        eventQueue = new ConcurrentLinkedQueue<>();
        exceptionQueue = new ConcurrentLinkedQueue<>();
        relevancyHashMap = new ConcurrentHashMap<>();
        sendHashMap = new ConcurrentHashMap<>();
        receiveQueue = new ConcurrentLinkedQueue<>();
        cancelSet = ConcurrentHashMap.newKeySet();

        int poolSize = Runtime.getRuntime().availableProcessors();
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(128);
        RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
        workPool = new ThreadPoolExecutor(6, poolSize*2, 180, TimeUnit.SECONDS, queue, policy);

        heartBeat = new HeartBeat();

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
                NetLog.error(e);
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
                linkTable.updateLastActivityTime(key);
                if (handler instanceof CommandHandler) {
                    heartBeat.upDateLastActivityTime();
                }
                workPool.submit(() -> {handler.receiveHandler(key);});
            } else if (key.isWritable()) {
                Queue<DataPackage> sendQueue = sendHashMap.get(key);
                if (sendQueue != null) {
                    DataPackage dataPackage = sendQueue.poll();
                    if (dataPackage != null) {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                        if (handler instanceof CommandHandler) {
                            heartBeat.upDateLastActivityTime();
                        }
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

    public void setHeartBeatInterval(int heartBeatInterval) {
        heartBeat.setHeartBeatInterval(heartBeatInterval);
    }

    public synchronized void register(SocketChannel socketChannel, Handler handler) throws IOException {
        socketChannel.configureBlocking(false);
        eventQueue.add(() -> {
            try {
                SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ);
                relevancyHashMap.put(key, handler);
                switch (handler) {
                    case CommandHandler commandHandler -> {
                        linkTable.putCommandKey(key);
                        serverAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                        heartBeat.upDateLastActivityTime();
                        heartBeat.start(key);
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
                NetLog.error(e);
            }
        });
        selector.wakeup();
    }
    public synchronized void cancel(SelectionKey key, boolean againLink) {
        linkTable.removeLastActivityTime(key);
        sendHashMap.remove(key);
        Handler handler = relevancyHashMap.remove(key);
        if (handler != null) {
            cancelSet.add(key);
            switch (handler) {
                case CommandHandler commandHandler -> {
                    NetLog.info("连接 [CommandLink] 已断开");
                    linkTable.removeCommandKey();
                    heartBeat.stop();
                    if (againLink) {
                        if (againLink(commandHandler)) {
                            for (int i = 0; linkTable.getCommandKey() == null && i < 100; i++) {
                                NetTool.sleep();
                            }
                            putDataPackage(linkTable.getCommandKey()
                                    , new CommandPackage(DataPackage.WAY_TOKEN_VERIFY, linkTable.getToken().getBytes()));
                        } else {
                            linkTable.removeToken();
                            addException(new AgainLinkTimeOutException());
                        }
                    } else {
                        NetLog.info("服务器主动关闭了连接 [CommandLink]");
                        linkTable.removeToken();
                        addException(new ServerCloseLinkException());
                    }
                }
                case MessageHandler messageHandler -> {
                    NetLog.info("连接 [MessageLink] 已断开");
                    linkTable.removeMessageKey();
                    if (againLink) {
                        for (int i = 0; linkTable.getCommandKey() == null && linkTable.getToken() == null && i < 100; i++) {
                            NetTool.sleep();
                        }
                        if (linkTable.getCommandKey() != null && linkTable.getToken() != null) {
                            putDataPackage(linkTable.getCommandKey()
                                    , new CommandPackage(DataPackage.WAY_BUILD_LINK, DataPackage.TYPE_MESSAGE_ADDRESS));
                        }
                    } else {
                        NetLog.info("服务器主动关闭了连接 [MessageLink]");
                    }
                }
                case FileHandler fileHandler -> {
                    NetLog.info("连接 [FileLink] 已断开");
                    linkTable.removeFileKey();
                    if (againLink) {
                        for (int i = 0; linkTable.getCommandKey() == null && linkTable.getToken() == null && i < 100; i++) {
                            NetTool.sleep();
                        }
                        if (linkTable.getCommandKey() != null && linkTable.getToken() != null) {
                            putDataPackage(linkTable.getCommandKey()
                                    , new CommandPackage(DataPackage.WAY_BUILD_LINK, DataPackage.TYPE_FILE_ADDRESS));
                        }
                    } else {
                        NetLog.info("服务器主动关闭了连接 [FileLink]");
                    }
                }
                default -> {
                    throw new IllegalStateException();
                }
            }
            eventQueue.add(() -> {
                key.cancel();
                cancelSet.remove(key);
            });
            selector.wakeup();
        }
    }

    public DataPackage getDataPackage() throws AgainLinkTimeOutException, ServerCloseLinkException, TokenMissingException {
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
            if (key != null && key.isValid()) {
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
    public void addException(NetException netException) {
        exceptionQueue.add(netException);
        synchronized (receiveLock) {
            receiveLock.notify();
        }
    }
    private void throwException() throws AgainLinkTimeOutException, ServerCloseLinkException, TokenMissingException {
        NetException netException = exceptionQueue.poll();
        if (netException != null) {
            switch (netException) {
                case AgainLinkTimeOutException againLinkTimeOutException -> {
                    throw againLinkTimeOutException;
                }
                case ServerCloseLinkException serverCloseLinkException -> {
                    throw serverCloseLinkException;
                }
                case TokenMissingException tokenMissingException -> {
                    throw tokenMissingException;
                }
                default -> {}
            }
        }
    }

    private boolean againLink(CommandHandler commandHandler) {
        for (int i = 1; i <= 5; i++) {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ignored) {
            }
            NetLog.info("第[$]次尝试重新连接服务器", i);
            try {
                SocketChannel socketChannel = SocketChannel.open(serverAddress);
                register(socketChannel, commandHandler);
                NetLog.info("重新连接服务器成功");
                return true;
            } catch (IOException ignored) {
            }
        }
        NetLog.info("重新连接服务器失败");
        return false;
    }

    private class HeartBeat implements Runnable{
        private long HEARTBEAT_INTERVAL = 30000;
        private long lastActivityTime;
        private boolean running;
        private Thread heartBeatThread;
        private SelectionKey key;

        public HeartBeat() {
            lastActivityTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (System.currentTimeMillis() - lastActivityTime > HEARTBEAT_INTERVAL) {
                    sendHeartBeat(key);
                }
            }
        }

        public void start(SelectionKey key) {
            if (heartBeatThread == null || !heartBeatThread.isAlive()) {
                this.key = key;
                running = true;
                heartBeatThread = new Thread(this);
                heartBeatThread.start();
            }
            NetLog.info("已开启心跳,间隔 [$] 秒", HEARTBEAT_INTERVAL / 1000);
        }

        public void stop() {
            running = false;
            if (heartBeatThread != null) {
                heartBeatThread.interrupt();
                NetLog.info("已关闭心跳");
            }
        }

        public void setHeartBeatInterval(int interval) {
            HEARTBEAT_INTERVAL = interval * 1000L;
        }

        public void upDateLastActivityTime() {
            lastActivityTime = System.currentTimeMillis();
        }

        private void sendHeartBeat(SelectionKey key) {
            if (key.channel().isOpen()) {
                putDataPackage(key, new CommandPackage(DataPackage.WAY_HEART_BEAT));
            } else {
                linkTable.removeLastActivityTime(key);
            }
        }
    }
}