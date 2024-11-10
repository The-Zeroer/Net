package server.log;

import java.text.SimpleDateFormat;

public class LogPackage {
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private long time;
    private byte level;
    private String message;
    private Object[] args;
    private Exception exception;

    public LogPackage(long time, byte level, String message, Object[] args) {
        this.time = time;
        this.level = level;
        this.message = message;
        this.args = args;
    }

    public LogPackage(long time, Exception e) {
        this.time = time;
        this.exception = e;
    }

    public String formatLog() {
        StringBuilder builder = new StringBuilder();
        builder.append("[").append(dateFormat.format(time)).append("] ");
        if (exception == null) {
            for (Object arg : args) {
                message = message.replaceFirst("\\$", arg == null ? "null" : arg.toString());
            }
            builder.append(message);
        } else {
            String className = exception.getClass().getName();
            String exceptionMessage = exception.getMessage();
            StackTraceElement[] stackTrace = exception.getStackTrace();
            builder.append(className).append(": ").append(exceptionMessage);
            for (StackTraceElement stackTraceElement : stackTrace) {
                builder.append("\n").append(stackTraceElement.toString());
            }
        }
        return builder.toString();
    }

    public long getTime() {
        return time;
    }
    public byte getLevel() {
        return level;
    }
    public String getMessage() {
        return message;
    }
    public Object[] getArgs() {
        return args;
    }
}