package net.datapackage;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;

public abstract class DataPackage {
    protected static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    public static final int HEADER_SIZE = 17;

    protected byte way;
    protected byte type;
    protected byte appendState;
    protected long time;
    protected int dataSize;
    protected short taskIdLength;
    protected byte[] taskId;
    protected byte[] data;

    protected DataPackage appendDataPackage;

    protected String UID;
    protected SelectionKey key;

    public DataPackage() {}

    public DataPackage(byte way) {
        this(way, TYPE_TEXT, null);
    }

    public DataPackage(byte way, byte type) {
        this(way, type, null);
    }

    public DataPackage(byte way, byte[] data) {
        this(way, TYPE_TEXT, data);
    }

    public DataPackage(byte way, byte type, byte[] data) {
        this.way = way;
        this.type = type;
        this.time = System.currentTimeMillis();
        if (data != null) {
            this.data = data;
            dataSize += data.length;
        }
    }

    public DataPackage addAppendDataPackage(DataPackage dataPackage) {
        this.appendState = APPEND_1;
        this.appendDataPackage = dataPackage;
        dataPackage.appendState = APPEND_2;
        return this;
    }
    public DataPackage removeAppendDataPackage() {
        this.appendState = 0;
        this.appendDataPackage = null;
        return this;
    }
    public DataPackage getAppendDataPackage() {
        return appendDataPackage;
    }
    public DataPackage setAppendState(byte appendState) {
        this.appendState = appendState;
        return this;
    }
    public byte getAppendState() {
        return appendState;
    }
    public String getContent() {
        if (data != null) {
            return new String(data);
        } else {
            return "";
        }
    }

    public byte getWay() {
        return way;
    }
    public DataPackage setWay(byte way) {
        this.way = way;
        return this;
    }
    public byte getType() {
        return type;
    }
    public DataPackage setType(byte type) {
        this.type = type;
        return this;
    }
    public long getTime() {
        return time;
    }
    public DataPackage setTime(long time) {
        this.time = time;
        return this;
    }
    public int getDataSize() {
        return dataSize;
    }
    public DataPackage setDataSize(int dataSize) {
        this.dataSize = dataSize;
        return this;
    }
    public byte[] getData() {
        return data;
    }
    public DataPackage setData(byte[] data) {
        this.data = data;
        return this;
    }
    public short getTaskIdLength() {
        return taskIdLength;
    }
    public String getTaskId() {
        if (taskId != null) {
            return new String(taskId);
        } else {
            return null;
        }
    }
    public DataPackage setTaskId(String taskId) {
        this.taskId = taskId.getBytes();
        taskIdLength = (short) taskId.length();
        return this;
    }
    public byte[] getTaskIdBytes() {
        return taskId;
    }

    public DataPackage setSelectionKey(SelectionKey key) {
        this.key = key;
        return this;
    }
    public SelectionKey getSelectionKey() {
        return key;
    }
    public DataPackage setUID(String UID) {
        this.UID = UID;
        return this;
    }
    public String getUID() {
        return UID;
    }

    @Override
    public String toString() {
        String address = null;
        if (key != null) {
            try {
                address = ((SocketChannel)key.channel()).getRemoteAddress().toString();
            } catch (IOException e) {
                address = key.channel().toString();
            }
        }
        return getClass().getSimpleName() + " [RemoteAddress=" + address + ", UID=" + UID
                + ", way=" + way + ", type=" + type + ", time=" + dateFormat.format(time)
                + ", dataSize=" + formatBytes(dataSize) + ", taskId=" + getTaskId() + "]";
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "0B";
        } else {
            String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
            int idx = (int) (Math.log(bytes) / Math.log(1024));
            return String.format("%.2f%s", bytes / Math.pow(1024, idx), units[idx]);
        }
    }

    // 注册
    public static final byte WAY_REGISTER = 1;
    // 注销
    public static final byte WAY_CANCEL = 2;
    // 登录
    public static final byte WAY_LOGIN = 3;
    // 登出
    public static final byte WAY_LOGOUT = 4;
    // 发送数据
    public static final byte WAY_SEND_DATA = 10;
    // 请求数据
    public static final byte WAY_REQUEST_DATA = 11;
    // 修改数据
    public static final byte WAY_CHANGE_DATA = 12;
    // 重置数据
    public static final byte WAY_RESET_DATA = 13;
    // 添加数据
    public static final byte WAY_ADD_DATA = 14;
    // 删除数据
    public static final byte WAY_DELETE_DATA = 15;
    // 更新数据
    public static final byte WAY_UPDATE_DATA = 16;
    // 发送聊天消息
    public static final byte WAY_SEND_MESSAGE = 20;
    // 请求聊天消息
    public static final byte WAY_REQUEST_MESSAGE = 21;
    // 在线聊天消息
    public static final byte WAY_ONLINE_MESSAGE = 22;
    // 离线聊天消息
    public static final byte WAY_OFFLINE_MESSAGE = 23;
    // 正常响应
    public static final byte WAY_OK = 100;
    // 错误响应
    public static final byte WAY_ERROR = 101;
    // 心跳包
    public static final byte WAY_HEART_BEAT = 110;
    // Token验证
    public static final byte WAY_TOKEN_VERIFY = 111;
    // 建立连接
    public static final byte WAY_BUILD_LINK = 112;
    // 检查更新
    public static final byte WAY_CHECK_UPDATE = 120;

    // 文本
    public static final byte TYPE_TEXT = 1;
    // 文件
    public static final byte TYPE_FILE = 2;
    // 原图
    public static final byte TYPE_IMAGE_ORIGINAL = 5;
    // 缩略图
    public static final byte TYPE_IMAGE_THUMBNAIL = 6;
    // 图标资源
    public static final byte TYPE_RESOURCE_ICON = 10;
    // 背景资源
    public static final byte TYPE_RESOURCE_BACKGROUND = 11;
    // 音频资源
    public static final byte TYPE_RESOURCE_AUDIO = 12;
    // 头像原图
    public static final byte TYPE_HEAD_ORIGINAL = 15;
    // 头像缩略图
    public static final byte TYPE_HEAD_THUMBNAIL = 16;
    // 用户基础信息
    public static final byte TYPE_USER_BASE_INFO = 20;
    // 用户密码
    public static final byte TYPE_USER_PASSWORD = 21;
    // 用户名
    public static final byte TYPE_USER_NAME = 22;
    // 用户联系人信息
    public static final byte TYPE_USER_CONTACTS_INFO = 23;
    // 用户群聊信息
    public static final byte TYPE_USER_GROUP_INFO = 24;
    // 群用户
    public static final byte TYPE_GROUP_USER = 30;
    // 组织架构
    public static final byte TYPE_ORG_STRUCTURE = 40;
    // 所有用户信息
    public static final byte TYPE_ALLUSER_INFO = 41;
    // 用户
    public static final byte TYPE_USER = 90;
    // 管理员
    public static final byte TYPE_ADMIN = 91;
    // 考勤
    public static final byte TYPE_ATTENDANCE = 95;
    // 消息地址
    public static final byte TYPE_MESSAGE_ADDRESS = 100;
    // 文件地址
    public static final byte TYPE_FILE_ADDRESS = 101;
    // 更新文件
    public static final byte TYPE_UPDATE_FILE = 120;

    /**
     * 未发送,正在等待的数据包
     */
    public static final byte SEND_1 = 1;
    /**
     * 发送中的数据包
     */
    public static final byte SEND_2 = 2;
    /**
     * 已发送的数据包
     */
    public static final byte SEND_3 = 3;

    // 被附加
    public static final byte APPEND_1 = 1;
    // 是附加
    public static final byte APPEND_2 = 2;
}