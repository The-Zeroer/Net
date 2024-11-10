package client.util;

import client.datapackage.FilePackage;
import client.datapackage.MessagePackage;

import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LinkTable {
    private final ConcurrentHashMap<String, Object> hashMap;
    private final ConcurrentLinkedQueue<MessagePackage> messageQueue;
    private final ConcurrentLinkedQueue<FilePackage> fileQueue;
    private final ConcurrentHashMap<SelectionKey, Long> lastActivityTime;
    private byte messageLinkState, fileLinkState;

    public LinkTable() {
        hashMap = new ConcurrentHashMap<>();
        messageQueue = new ConcurrentLinkedQueue<>();
        fileQueue = new ConcurrentLinkedQueue<>();
        lastActivityTime = new ConcurrentHashMap<>();
        messageLinkState = LINK_1;
        fileLinkState = LINK_1;
    }

    public void putToken(String token) {
        hashMap.put("token", token);
    }
    public void removeToken() {
        hashMap.remove("token");
    }
    public String getToken() {
        return (String) hashMap.get("token");
    }

    public void putCommandKey(SelectionKey commandKey) {
        hashMap.put("commandKey", commandKey);
    }
    public void removeCommandKey() {
        hashMap.remove("commandKey");

    }
    public SelectionKey getCommandKey() {
        return (SelectionKey) hashMap.get("commandKey");
    }

    public void putMessageKey(SelectionKey messageKey) {
        hashMap.put("messageKey", messageKey);
    }
    public void removeMessageKey() {
        messageLinkState = LINK_1;
        hashMap.remove("messageKey");
    }
    public SelectionKey getMessageKey() {
        return (SelectionKey) hashMap.get("messageKey");
    }

    public void putFileKey(SelectionKey fileKey) {
        hashMap.put("fileKey", fileKey);
    }
    public void removeFileKey() {
        fileLinkState = LINK_1;
        hashMap.remove("fileKey");
    }
    public SelectionKey getFileKey() {
        return (SelectionKey) hashMap.get("fileKey");
    }

    public void putMessagePackage(MessagePackage messagePackage) {
        messageQueue.add(messagePackage);
    }
    public MessagePackage getMessagePackage() {
        return messageQueue.poll();
    }
    public boolean messageQueueEmpty() {
        return messageQueue.isEmpty();
    }
    public void putFilePackage(FilePackage filePackage) {
        fileQueue.add(filePackage);
    }
    public FilePackage getFilePackage() {
        return fileQueue.poll();
    }
    public boolean fileQueueEmpty() {
        return fileQueue.isEmpty();
    }

    public void setMessageLinkState(byte messageLinkState) {
        this.messageLinkState = messageLinkState;
    }
    public byte getMessageLinkState() {
        return messageLinkState;
    }
    public void setFileLinkState(byte fileLinkState) {
        this.fileLinkState = fileLinkState;
    }
    public byte getFileLinkState() {
        return fileLinkState;
    }

    public void updateLastActivityTime(SelectionKey key) {
        lastActivityTime.put(key, System.currentTimeMillis());
    }
    public Set<Map.Entry<SelectionKey, Long>> getLastActivityTime() {
        return lastActivityTime.entrySet();
    }
    public void removeLastActivityTime(SelectionKey key) {
        lastActivityTime.remove(key);
    }

    // 未连接
    public static final byte LINK_1 = 1;
    // 连接中
    public static final byte LINK_2 = 2;
    // 已验证
    public static final byte VERIFY = 3;
    // 已就绪
    public static final byte READY = 4;
}