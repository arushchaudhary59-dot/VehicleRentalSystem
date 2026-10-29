package vrs.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatting helpers (Indian currency grouping, short dates). */
public final class Fmt {
    private Fmt() {}

    private static final Locale IN = Locale.forLanguageTag("en-IN");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ENGLISH);
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    public static String money(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getNumberInstance(IN);
        boolean whole = v.stripTrailingZeros().scale() <= 0;
        nf.setMinimumFractionDigits(whole ? 0 : 2);
        nf.setMaximumFractionDigits(2);
        return (v.signum() < 0 ? "-" : "") + Theme.RUPEE + nf.format(v.abs());
    }

    /** 12,400 -> "₹12.4k" for chart labels. */
    public static String moneyShort(BigDecimal v) {
        double d = v.doubleValue();
        if (Math.abs(d) >= 100000) return Theme.RUPEE + trim(d / 100000) + "L";
        if (Math.abs(d) >= 1000) return Theme.RUPEE + trim(d / 1000) + "k";
        return Theme.RUPEE + (long) d;
    }

    private static String trim(double d) {
        String s = String.format(Locale.ROOT, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static String percent(BigDecimal p) { return p.stripTrailingZeros().toPlainString() + "%"; }

    public static String day(LocalDate d) { return d == null ? "" : DAY.format(d); }
    public static String dm(LocalDate d) { return d == null ? "" : DM.format(d); }
    public static String shortTime(LocalDateTime d) { return d == null ? "" : SHORT_TIME.format(d); }
    public static String date(LocalDate d) { return d == null ? "" : DATE.format(d); }
    public static String dateTime(LocalDateTime d) { return d == null ? "" : DATE_TIME.format(d); }
    public static String time(LocalDateTime d) { return d == null ? "" : TIME.format(d); }
    public static String month(YearMonth m) { return MONTH.format(m); }

    public static String days(int n) { return n + (n == 1 ? " day" : " days"); }

    /** "just now", "5 min ago", "3 h ago", "2 d ago" */
    public static String ago(LocalDateTime t) {
        if (t == null) return "";
        long s = Duration.between(t, LocalDateTime.now()).getSeconds();
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " d ago";
    }
}
