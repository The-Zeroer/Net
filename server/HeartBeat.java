package server;

import server.link.CommandLink;
import server.link.FileLink;
import server.link.MessageLink;
import server.log.NetLog;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class HeartBeat {
    private Timer timer;
    private long HEARTBEAT_INTERVAL = 60000;

    private final LinkTable linkTable;
    private CommandLink commandLink;
    private MessageLink messageLink;
    private FileLink fileLink;

    public HeartBeat(LinkTable linkTable) {
        this.linkTable = linkTable;
        this.commandLink = linkTable.getCommandLink();
        this.messageLink = linkTable.getMessageLink();
        this.fileLink = linkTable.getFileLink();
        timer = new Timer(true);
    }

    public void start() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                long nowTime = System.currentTimeMillis();
                for (Map.Entry<SelectionKey, Long> entry : linkTable.getLastActivityTime()) {
                    if (nowTime - entry.getValue() > HEARTBEAT_INTERVAL) {
                        breakLink(entry.getKey());
                    }
                }
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL);
        NetLog.info("已开启心跳检测,间隔 [$] 秒", HEARTBEAT_INTERVAL / 1000);
    }

    public void stop() {
        timer.cancel();
    }

    public void setHeartBeatInterval(int interval) {
        HEARTBEAT_INTERVAL = interval * 1000L;
    }

    private void breakLink(SelectionKey key) {
        if (key.channel().isOpen()) {
            try {
                NetLog.debug("超时未收到心跳包,已断开连接 [$]", ((SocketChannel)key.channel()).getRemoteAddress());
            } catch (IOException e) {
                NetLog.debug("超时未收到心跳包,已断开连接 [$]", key.channel());
            }
            commandLink.cancel(key);
            messageLink.cancel(key);
            fileLink.cancel(key);
        } else {
            linkTable.removeLastActivityTime(key);
        }
    }
}