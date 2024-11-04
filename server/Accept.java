package server;

import server.link.Link;
import server.log.NetLog;
import server.util.TokenBucket;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Accept extends Thread{
    private final Selector selector;
    private final ConcurrentHashMap<ServerSocketChannel, Link> relevancyHashMap;
    private final ConcurrentLinkedQueue<Runnable> eventQueue;
    private TokenBucket tokenBucket;
    private boolean running;

    public Accept() throws IOException {
        selector = Selector.open();
        relevancyHashMap = new ConcurrentHashMap<>();
        eventQueue = new ConcurrentLinkedQueue<>();
        tokenBucket = new TokenBucket(3000, 1000);
        running = true;
    }

    @Override
    public void run() {
        NetLog.info("开始监听端口");
        while(running){
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
        NetLog.info("监听结束");
    }

    private void extracted() throws IOException {
        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            keys.remove();
            if (key.isAcceptable()) {
                ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                SocketChannel socketChannel = serverSocketChannel.accept();
                SocketAddress socketAddress = socketChannel.getRemoteAddress();
                if (tokenBucket.acquire()) {
                    Link link = relevancyHashMap.get(serverSocketChannel);
                    if (link != null) {
                        NetLog.info("连接 [$] 已建立,正常", socketAddress);
                        link.register(socketChannel);
                    } else {
                        socketChannel.close();
                        NetLog.warn("连接 [$] 已建立,没有与之对应的服务,已断开", socketAddress);
                    }
                } else {
                    socketChannel.close();
                    NetLog.warn("连接 [$] 已建立,流量超标,已断开", socketAddress);
                }
            }
        }
    }

    public void addMonitor(ServerSocketChannel serverSocketChannel, Link link) throws IOException {
        relevancyHashMap.put(serverSocketChannel, link);
        serverSocketChannel.configureBlocking(false);
        if (running) {
            eventQueue.add(() -> {
                try {
                    serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                } catch (ClosedChannelException e) {
                    NetLog.error(e.getMessage());
                }
            });
            selector.wakeup();
        } else {
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        }
    }

    public void setTokenBucket(long capacity, long rate) {
        tokenBucket = new TokenBucket(capacity, rate);
    }
}