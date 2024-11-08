package server;

import server.datapackage.FilePackage;
import server.datapackage.MessagePackage;
import server.link.CommandLink;
import server.link.FileLink;
import server.link.MessageLink;
import server.log.NetLog;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LinkTable {
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, SelectionKey>> keyHashMap;
    private final ConcurrentHashMap<SelectionKey, ConcurrentHashMap<String, String>> tokenHashMap;
    private final ConcurrentHashMap<String, Byte> messageLinkStateHashMap;
    private final ConcurrentHashMap<String, Byte> fileLinkStateHashMap;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<MessagePackage>> messageQueueMap;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<FilePackage>> fileQueueMap;
    private final ConcurrentHashMap<SelectionKey, Long> lastActivityTime;
    private CommandLink commandLink;
    private MessageLink messageLink;
    private FileLink fileLink;

    public LinkTable() {
        keyHashMap = new ConcurrentHashMap<>();
        tokenHashMap = new ConcurrentHashMap<>();
        messageLinkStateHashMap = new ConcurrentHashMap<>();
        fileLinkStateHashMap = new ConcurrentHashMap<>();
        messageQueueMap = new ConcurrentHashMap<>();
        fileQueueMap = new ConcurrentHashMap<>();
        lastActivityTime = new ConcurrentHashMap<>();
    }

    public void setLink(CommandLink commandLink, MessageLink messageLink, FileLink fileLink) {
        this.commandLink = commandLink;
        this.messageLink = messageLink;
        this.fileLink = fileLink;
    }

    public void register(SelectionKey commandKey, String UID, String token) {
        ConcurrentHashMap<String, SelectionKey> tempkeyMap = new ConcurrentHashMap<>(){{put("commandKey", commandKey);}};
        ConcurrentHashMap<String, String> temptokenMap = new ConcurrentHashMap<>(){{put("UID", UID);put("token", token);}};
        keyHashMap.put(UID, tempkeyMap);
        keyHashMap.put(token, tempkeyMap);
        tokenHashMap.put(commandKey, temptokenMap);
        setMessageLinkStata(UID, LinkTable.LINK_1);
        setFileLinkStata(UID, LinkTable.LINK_1);
        lastActivityTime.put(commandKey, System.currentTimeMillis());
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
                String token = getTokenByCommandKey(commandKey);
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
                lastActivityTime.remove(commandKey);
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
                lastActivityTime.remove(messageKey);
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
                lastActivityTime.remove(fileKey);
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
                lastActivityTime.put(messageKey, System.currentTimeMillis());
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
                lastActivityTime.put(fileKey, System.currentTimeMillis());
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
                lastActivityTime.remove(messageKey);
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
                lastActivityTime.remove(fileKey);
            }
            try {
                NetLog.info("连接 [$] (FileLink) 已解除关联UID [$]"
                        , ((SocketChannel)fileKey.channel()).getRemoteAddress(), UID);
            } catch (IOException e) {
                NetLog.info("连接 [未知,已被关闭] (FileLink) 已解除关联UID [$]", UID);
            }
        }
    }

    public String getUIDByCommandKey(SelectionKey commandKey) {
        return getUIDOrToken(commandKey, "UID");
    }
    public String getUIDByMessageKey(SelectionKey messageKey) {
        return getUIDOrToken(messageKey, "UID");
    }
    public String getUIDByFileKey(SelectionKey fileKey) {
        return getUIDOrToken(fileKey, "UID");
    }

    public String getTokenByCommandKey(SelectionKey commandKey) {
        return getUIDOrToken(commandKey, "token");
    }
    public String getTokenByMessageKey(SelectionKey messageKey) {
        return getUIDOrToken(messageKey, "token");
    }
    public String getTokenByFileKey(SelectionKey fileKey) {
        return getUIDOrToken(fileKey, "token");
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
        MessagePackage messagePackage = messageQueueMap.get(UID).poll();
        if (messagePackage == null) {
            messageQueueMap.remove(UID);
        }
        return messagePackage;
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
        FilePackage filePackage = fileQueueMap.get(UID).poll();
        if (filePackage == null) {
            fileQueueMap.remove(UID);
        }
        return filePackage;
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

    public CommandLink getCommandLink() {
        return commandLink;
    }
    public MessageLink getMessageLink() {
        return messageLink;
    }
    public FileLink getFileLink() {
        return fileLink;
    }

    // 未连接
    public static final byte LINK_1 = 1;
    // 连接中
    public static final byte LINK_2 = 2;
    // 已验证
    public static final byte VERIFY = 3;
    // 已就绪
    public static final byte READY = 4;

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