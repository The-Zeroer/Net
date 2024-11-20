package net.util;

import net.datapackage.DataPackage;
import net.datapackage.FilePackage;
import net.datapackage.MessagePackage;
import net.link.CommandLink;
import net.link.FileLink;
import net.link.MessageLink;
import net.log.NetLog;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LinkTable {
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, SelectionKey>> keyHashMap;
    private final ConcurrentHashMap<SelectionKey, ConcurrentHashMap<String, String>> tokenHashMap;
    private final ConcurrentHashMap<String, Byte> messageLinkStateHashMap;
    private final ConcurrentHashMap<String, Byte> fileLinkStateHashMap;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<MessagePackage>> messageQueueMap;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<FilePackage>> fileQueueMap;
    private final ConcurrentHashMap<String, DataPackage> receiveHashMap;
    private CommandLink commandLink;
    private MessageLink messageLink;
    private FileLink fileLink;

    // 未连接
    public static final byte LINK_1 = 1;
    // 连接中
    public static final byte LINK_2 = 2;
    // 已验证
    public static final byte VERIFY = 3;
    // 已就绪
    public static final byte READY = 4;

    public LinkTable() {
        keyHashMap = new ConcurrentHashMap<>();
        tokenHashMap = new ConcurrentHashMap<>();
        messageLinkStateHashMap = new ConcurrentHashMap<>();
        fileLinkStateHashMap = new ConcurrentHashMap<>();
        messageQueueMap = new ConcurrentHashMap<>();
        fileQueueMap = new ConcurrentHashMap<>();
        receiveHashMap = new ConcurrentHashMap<>();
    }

    public void setLink(CommandLink commandLink, MessageLink messageLink, FileLink fileLink) {
        this.commandLink = commandLink;
        this.messageLink = messageLink;
        this.fileLink = fileLink;
    }

    public void register(SelectionKey commandKey, String UID, String token) {
        ConcurrentHashMap<String, SelectionKey> tempkeyMap = new ConcurrentHashMap<>(){{put("commandKey", commandKey);}};
        ConcurrentHashMap<String, String> temptokenMap = new ConcurrentHashMap<>(){{put("UID", UID);put("Token", token);}};
        keyHashMap.put(UID, tempkeyMap);
        keyHashMap.put(token, tempkeyMap);
        tokenHashMap.put(commandKey, temptokenMap);
        setMessageLinkStata(UID, LinkTable.LINK_1);
        setFileLinkStata(UID, LinkTable.LINK_1);
        try {
            NetLog.info("UID [$] 已注册,并绑定连接 [$] (CommandLink) 及Token"
                    , UID, ((SocketChannel)commandKey.channel()).getRemoteAddress());
        } catch (IOException e) {
            NetLog.error(e);
        }
    }
    public void cancel(SelectionKey commandKey) {
        ConcurrentHashMap<String, String> temptokenMap = tokenHashMap.remove(commandKey);
        if (temptokenMap != null) {
            String UID = temptokenMap.get("UID");
            if (UID != null) {
                cancel(UID);
            }
        }
    }
    public void cancel(String UID) {
        messageLinkStateHashMap.remove(UID);
        fileLinkStateHashMap.remove(UID);
        ConcurrentHashMap<String, SelectionKey> tempkeyMap = keyHashMap.remove(UID);
        if (tempkeyMap != null) {
            SelectionKey commandKey = tempkeyMap.get("commandKey");
            if (commandKey != null) {
                String token = getToken(commandKey);
                if (token != null) {
                    keyHashMap.remove(token);
                }
                try {
                    NetLog.info("连接 [$] (CommandLink) 已解除关联UID [$]"
                            , ((SocketChannel)commandKey.channel()).getRemoteAddress(), UID);
                } catch (IOException e) {
                    NetLog.info("连接 [未知,已被关闭] (CommandLink) 已解除关联UID [$]", UID);
                }
                commandLink.cancel(commandKey);
                tokenHashMap.remove(commandKey);
            }
            SelectionKey messageKey = tempkeyMap.get("messageKey");
            if (messageKey != null) {
                try {
                    NetLog.info("连接 [$] (MessageLink) 已解除关联UID [$]"
                            , ((SocketChannel)messageKey.channel()).getRemoteAddress(), UID);
                } catch (IOException e) {
                    NetLog.info("连接 [未知,已被关闭] (MessageLink) 已解除关联UID [$]", UID);
                }
                messageLink.cancel(messageKey);
                tokenHashMap.remove(messageKey);
            }
            SelectionKey fileKey = tempkeyMap.get("fileKey");
            if (fileKey != null) {
                try {
                    NetLog.info("连接 [$] (FileLink) 已解除关联UID [$]"
                            , ((SocketChannel)fileKey.channel()).getRemoteAddress(), UID);
                } catch (IOException e) {
                    NetLog.info("连接 [未知,已被关闭] (FileLink) 已解除关联UID [$]", UID);
                }
                fileLink.cancel(fileKey);
                tokenHashMap.remove(fileKey);
            }
            NetLog.info("UID [$] 已注销", UID);
        }
    }

    public void addMessageKey(SelectionKey commandKey, SelectionKey messageKey) {
        ConcurrentHashMap<String, String> temptokenMap = tokenHashMap.get(commandKey);
        if (temptokenMap != null) {
            tokenHashMap.put(messageKey, temptokenMap);
            String UID = temptokenMap.get("UID");
            ConcurrentHashMap<String, SelectionKey> tempkeyMap = keyHashMap.get(UID);
            if (tempkeyMap != null) {
                tempkeyMap.put("messageKey", messageKey);
            }
            try {
                NetLog.info("连接 [$] (MessageLink) 已关联UID [$]"
                        , ((SocketChannel)messageKey.channel()).getRemoteAddress(), UID);
            } catch (IOException e) {
                NetLog.error(e);
            }
        }
    }
    public void addFileKey(SelectionKey commandKey, SelectionKey fileKey) {
        ConcurrentHashMap<String, String> temptokenMap = tokenHashMap.get(commandKey);
        if (temptokenMap != null) {
            tokenHashMap.put(fileKey, temptokenMap);
            String UID = temptokenMap.get("UID");
            ConcurrentHashMap<String, SelectionKey> tempkeyMap = keyHashMap.get(UID);
            if (tempkeyMap != null) {
                tempkeyMap.put("fileKey", fileKey);
            }
            try {
                NetLog.info("连接 [$] (FileLink) 已关联UID [$]"
                        , ((SocketChannel)fileKey.channel()).getRemoteAddress(), UID);
            } catch (IOException e) {
                NetLog.error(e);
            }
        }
    }
    public void removeMessageKey(SelectionKey messageKey) {
        ConcurrentHashMap<String, String> temptokenMap = tokenHashMap.remove(messageKey);
        if (temptokenMap != null) {
            String UID = temptokenMap.get("UID");
            messageLinkStateHashMap.put(UID, LINK_1);
            ConcurrentHashMap<String, SelectionKey> tempkeyMap = keyHashMap.get(UID);
            if (tempkeyMap != null) {
                tempkeyMap.remove("messageKey");
            }
            try {
                NetLog.info("连接 [$] (MessageLink) 已解除关联UID [$]"
                        , ((SocketChannel)messageKey.channel()).getRemoteAddress(), UID);
            } catch (IOException e) {
                NetLog.info("连接 [未知,已被关闭] (MessageLink) 已解除关联UID [$]", UID);
            }
        }
    }
    public void removeFileKey(SelectionKey fileKey) {
        ConcurrentHashMap<String, String> temptokenMap = tokenHashMap.remove(fileKey);
        if (temptokenMap != null) {
            String UID = temptokenMap.get("UID");
            fileLinkStateHashMap.put(UID, LINK_1);
            ConcurrentHashMap<String, SelectionKey> tempkeyMap = keyHashMap.get(UID);
            if (tempkeyMap != null) {
                tempkeyMap.remove("fileKey");
            }
            try {
                NetLog.info("连接 [$] (FileLink) 已解除关联UID [$]"
                        , ((SocketChannel)fileKey.channel()).getRemoteAddress(), UID);
            } catch (IOException e) {
                NetLog.info("连接 [未知,已被关闭] (FileLink) 已解除关联UID [$]", UID);
            }
        }
    }

    public String getUID(SelectionKey key) {
        return getUIDOrToken(key, "UID");
    }
    public String getToken(SelectionKey key) {
        return getUIDOrToken(key, "Token");
    }

    public SelectionKey getCommandKeyByUID(String UID) {
        return getKey(UID, "commandKey");
    }
    public SelectionKey getMessageKeyByUID(String UID) {
        return getKey(UID, "messageKey");
    }
    public SelectionKey getFileKeyByUID(String UID) {
        return getKey(UID, "fileKey");
    }

    public SelectionKey getCommandKeyByToken(String token) {
        return getKey(token, "commandKey");
    }
    public SelectionKey getMessageKeyByToken(String token) {
        return getKey(token, "messageKey");
    }
    public SelectionKey getFileKeyByToken(String token) {
        return getKey(token, "fileKey");
    }

    public void setMessageLinkStata(String UID, byte state) {
        messageLinkStateHashMap.put(UID, state);
    }
    public Byte getMessageLinkStata(String UID) {
        return messageLinkStateHashMap.get(UID);
    }
    public void setFileLinkStata(String UID, byte state) {
        fileLinkStateHashMap.put(UID, state);
    }
    public Byte getFileLinkStata(String UID) {
        return fileLinkStateHashMap.get(UID);
    }

    public void putMessagePackage(String UID, MessagePackage messagePackage) {
        messageQueueMap.computeIfAbsent(UID, k -> new ConcurrentLinkedQueue<>()).add(messagePackage);
    }
    public MessagePackage getMessagePackage(String UID) {
        ConcurrentLinkedQueue<MessagePackage> queue = messageQueueMap.get(UID);
        if (queue != null) {
            return queue.poll();
        } else {
            messageQueueMap.remove(UID);
            return null;
        }
    }
    public boolean messageQueueEmpty(String UID) {
        return messageQueueMap.get(UID) == null;
    }
    public void putFilePackage(String UID, FilePackage filePackage) {
        fileQueueMap.computeIfAbsent(UID, k -> new ConcurrentLinkedQueue<>()).add(filePackage);
    }
    public boolean fileQueueEmpty(String UID) {
        return fileQueueMap.get(UID) == null;
    }
    public FilePackage getFilePackage(String UID) {
        ConcurrentLinkedQueue<FilePackage> queue = fileQueueMap.get(UID);
        if (queue != null) {
            return queue.poll();
        } else {
            messageQueueMap.remove(UID);
            return null;
        }
    }

    public CommandLink getCommandLink() {
        return commandLink;
    }
    public MessageLink getMessageLink() {
        return messageLink;
    }
    public FileLink getFileLink() {
        return fileLink;
    }

    public void putAppendDataPackage(String taskId, DataPackage dataPackage) {
        receiveHashMap.put(taskId, dataPackage);
    }
    public DataPackage getAppendDataPackage(String taskId) {
        return receiveHashMap.remove(taskId);
    }

    private SelectionKey getKey(String UIDOrToken, String name) {
        ConcurrentHashMap<String, SelectionKey> tempkeyMap = keyHashMap.get(UIDOrToken);
        if (tempkeyMap != null) {
            return tempkeyMap.get(name);
        } else {
            return null;
        }
    }
    private String getUIDOrToken(SelectionKey key, String UIDOrToken) {
        ConcurrentHashMap<String, String> temptokenMap = tokenHashMap.get(key);
        if (temptokenMap != null) {
            return temptokenMap.get(UIDOrToken);
        } else {
            return null;
        }
    }
}