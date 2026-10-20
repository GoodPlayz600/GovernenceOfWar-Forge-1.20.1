package io.github.geopoliticalwar.domain.war;

public final class CaptureProgressPolicy {
    private CaptureProgressPolicy() {
    }

    public static long advance(long current, long elapsed, long required) {
        if (current < 0 || elapsed < 0 || required <= 0) {
            throw new IllegalArgumentException("Invalid capture progress input");
        }
        return Math.min(required, current > Long.MAX_VALUE - elapsed ? Long.MAX_VALUE : current + elapsed);
    }

    public static long decay(long current, long elapsed, long perSecond) {
        if (current < 0 || elapsed < 0 || perSecond < 0) {
            throw new IllegalArgumentException("Invalid capture decay input");
        }
        long reduction = perSecond > 0 && elapsed > Long.MAX_VALUE / perSecond
                ? Long.MAX_VALUE
                : elapsed * perSecond;
        return Math.max(0L, current - reduction);
    }
}
