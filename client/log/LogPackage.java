package client.log;

import java.text.SimpleDateFormat;

public class LogPackage {
    public SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    public long time;
    public byte level;
    public String message;
    public Object[] args;

    public LogPackage(long time, byte level, String message, Object[] args) {
        this.time = time;
        this.level = level;
        this.message = message;
        this.args = args;
    }

    public String formatLog() {
        for (Object arg : args) {
            message = message.replaceFirst("\\$", arg == null ? "null" : arg.toString());
        }
        return "[" + dateFormat.format(this.time) + "] " + message;
    }
}
