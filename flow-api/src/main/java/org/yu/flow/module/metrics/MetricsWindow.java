package org.yu.flow.module.metrics;

import java.time.LocalDateTime;

/**
 * 查询时间窗。
 */
public enum MetricsWindow {
    M15(15),
    H1(60),
    H24(24 * 60),
    D7(7 * 24 * 60),
    D30(30 * 24 * 60);

    private final int minutes;

    MetricsWindow(int minutes) {
        this.minutes = minutes;
    }

    public int minutes() {
        return minutes;
    }

    public LocalDateTime from(LocalDateTime nowMinute) {
        return nowMinute.minusMinutes(minutes);
    }

    /**
     * series 粒度：15m/1h 按分钟（点数少、便于看瞬时）；
     * 24h/7d/30d 按小时，避免 24h 上千分钟柱挤成色块。
     */
    public boolean hourlySeries() {
        return this == H24 || this == D7 || this == D30;
    }

    public static MetricsWindow fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return H24;
        }
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "15m", "m15" -> M15;
            case "1h", "h1" -> H1;
            case "24h", "1d", "h24" -> H24;
            case "7d", "d7" -> D7;
            case "30d", "d30" -> D30;
            default -> H24;
        };
    }

    public String label() {
        return switch (this) {
            case M15 -> "15m";
            case H1 -> "1h";
            case H24 -> "24h";
            case D7 -> "7d";
            case D30 -> "30d";
        };
    }
}
