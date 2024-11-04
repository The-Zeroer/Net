package server.util;

public class TokenBucket {
    private final long capacity; // 桶的容量
    private final long rate; // 令牌生成速率（每秒生成的令牌数量）
    private long tokens; // 当前令牌数
    private long lastTimestamp; // 上次更新时间

    public TokenBucket(long capacity, long rate) {
        this.capacity = capacity;
        this.rate = rate;
        this.tokens = capacity; // 初始化为满桶
        this.lastTimestamp = System.currentTimeMillis();
    }

    public synchronized boolean acquire() {
        long now = System.currentTimeMillis();
        // 计算时间差
        long elapsedTime = now - lastTimestamp;
        // 计算生成的令牌数量
        long generatedTokens = elapsedTime * rate / 1000;
        // 更新桶中令牌数量
        if (generatedTokens > 0) {
            tokens = Math.min(capacity, tokens + generatedTokens);
            lastTimestamp = now;
        }

        // 如果有令牌可用，获取一个令牌
        if (tokens > 0) {
            tokens--;
            return true; // 获取令牌成功
        }

        // 否则，返回失败
        return false; // 获取令牌失败
    }
}