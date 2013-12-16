package nl.mplatvoet.waitless.misc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author M Platvoet
 * @since 3/17/11 8:23 AM
 * <p/>
 * Absolutely NOT THREAD SAFE
 */
public class Stopwatch {
    private static final Logger DEFAULT_LOG = LoggerFactory.getLogger(Stopwatch.class);

    private final Logger log;


    private long startStamp = System.currentTimeMillis();
    private long intervalStamp = startStamp;

    public Stopwatch() {
        this(DEFAULT_LOG);
    }

    public Stopwatch(Logger log) {
        if (log == null) {
            throw new IllegalArgumentException("log may not be null");
        }
        this.log = log;
    }

    public Stopwatch reset() {
        startStamp = System.currentTimeMillis();
        intervalStamp = startStamp;
        return this;
    }

    public Stopwatch interval() {
        intervalUpdate();
        return this;
    }

    public long getIntervalStamp() {
        return intervalUpdate();
    }

    public long getElapsedStamp() {
        return elapsedInternal();
    }

    public Stopwatch interval(String message, Object... args) {
        long interval = intervalUpdate();
        log("I ", interval, message, args);
        return this;
    }

    public Stopwatch elapsed(String message, Object... args) {
        long interval = elapsedInternal();
        log("E ", interval, message, args);
        return this;
    }

    private long elapsedInternal() {
        return System.currentTimeMillis() - startStamp;
    }

    private void log(String type, long ms, String message, Object... args) {
        if (!log.isDebugEnabled()) return;

        StringBuilder messageBuilder = format(type, ms);
        messageBuilder.append(" ").append(message);
        if (args == null || args.length < 1) {
            log.debug(messageBuilder.toString());
        } else {
            log.debug(messageBuilder.toString(), args);
        }
    }


    private long intervalUpdate() {
        long currentIntervalStamp = intervalStamp;
        intervalStamp = System.currentTimeMillis();
        return intervalStamp - currentIntervalStamp;
    }

    public static String format(long ms) {
        return format("", ms).toString();
    }


    private static StringBuilder format(String type, long ms) {
        if (ms < 1) return new StringBuilder(type).append("00:00.000");
        long milliSeconds = ms % 1000L;
        long seconds = ms / 1000L;
        long minutes = seconds > 0L ? seconds / 60 : 0L;
        minutes = minutes > 99 ? 99 : minutes;
        seconds = seconds > 0L ? seconds % 60L : 0L;

        StringBuilder sb = new StringBuilder(type);
        if (minutes < 10L) sb.append("0");
        sb.append(minutes).append(":");
        if (seconds < 10L) sb.append("0");
        sb.append(seconds).append(".");
        if (milliSeconds < 100L) sb.append("0");
        if (milliSeconds < 10L) sb.append("0");
        sb.append(milliSeconds);
        return sb;
    }


    public Stopwatch average(int divider, String message, Object... args) {
        long interval = intervalUpdate() / divider;
        log("A ", interval, message, args);
        return this;
    }

    public String getIntervalFormatted() {
        return format(getIntervalStamp());
    }

    public String getElapsedFormatted() {
        return format(getElapsedStamp());
    }
}
