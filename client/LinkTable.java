package client;

import client.datapackage.FilePackage;
import client.datapackage.MessagePackage;

import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LinkTable {
    private ConcurrentHashMap<String, Object> hashMap;
    private ConcurrentLinkedQueue<MessagePackage> messageQueue;
    private ConcurrentLinkedQueue<FilePackage> fileQueue;
    private byte messageLinkState, fileLinkState;

    public LinkTable() {
        hashMap = new ConcurrentHashMap<>();
        messageQueue = new ConcurrentLinkedQueue<>();
        fileQueue = new ConcurrentLinkedQueue<>();
        messageLinkState = MESSAGE_LINK_1;
        fileLinkState = FILE_LINK_1;
    }

    public void putToken(String token) {
        hashMap.put("token", token);
    }
    public String getToken() {
        return (String) hashMap.get("token");
    }

    public void putCommandKey(SelectionKey commandKey) {
        hashMap.put("commandKey", commandKey);
    }
    public SelectionKey getCommandKey() {
        return (SelectionKey) hashMap.get("commandKey");
    }

    public void putMessageKey(SelectionKey messageKey) {
        hashMap.put("messageKey", messageKey);
    }
    public void removeMessageKey() {
        messageLinkState = MESSAGE_LINK_1;
        hashMap.remove("messageKey");
    }
    public SelectionKey getMessageKey() {
        return (SelectionKey) hashMap.get("messageKey");
    }

    public void putFileKey(SelectionKey fileKey) {
        hashMap.put("fileKey", fileKey);
    }
    public void removeFileKey() {
        fileLinkState = FILE_LINK_1;
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

    // 未连接
    public static final byte MESSAGE_LINK_1 = 1;
    // 连接中
    public static final byte MESSAGE_LINK_2 = 2;
    // 已验证
    public static final byte MESSAGE_VERIFY = 3;
    // 已就绪
    public static final byte MESSAGE_READY = 4;

    public static final byte FILE_LINK_1 = 11;
    public static final byte FILE_LINK_2 = 12;
    public static final byte FILE_VERIFY = 13;
    public static final byte FILE_READY = 14;
}