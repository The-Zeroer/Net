package client;

import client.datapackage.CommandPackage;
import client.datapackage.DataPackage;
import client.log.NetLog;

import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class HeartBeat {
    private final Link link;
    private final LinkTable linkTable;
    private final Timer timer;
    private long HEARTBEAT_INTERVAL = 15000;

    public HeartBeat(Link link, LinkTable linkTable) {
        this.link = link;
        this.linkTable = linkTable;
        timer = new Timer(true);
    }

    public void start() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                long nowTime = System.currentTimeMillis();
                for (Map.Entry<SelectionKey, Long> entry : linkTable.getLastActivityTime()) {
                    if (nowTime - entry.getValue() > HEARTBEAT_INTERVAL) {
                        sendHeartBeat(entry.getKey());
                    }
                }
            }
        }, 0, HEARTBEAT_INTERVAL);
        NetLog.info("已开启心跳,间隔 [$] 秒", HEARTBEAT_INTERVAL / 1000);
    }

    public void stop() {
        timer.cancel();
    }

    public void setHeartBeatInterval(int interval) {
        HEARTBEAT_INTERVAL = interval * 1000L;
    }

    private void sendHeartBeat(SelectionKey key) {
        if (key.channel().isOpen()) {
            link.putDataPackage(key, new CommandPackage(DataPackage.WAY_HEART_BEAT, null));
        } else {
            linkTable.removeLastActivityTime(key);
        }
    }
}