package com.skillrental.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Admin decision must be within the actual held escrow; never trust browser totals. */
public final class AdminDisputeDecisionPolicy {
    private AdminDisputeDecisionPolicy() {}

    public static BigDecimal award(BigDecimal gross, BigDecimal held) {
        if (gross == null || gross.signum() < 0 || gross.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Số tiền chi Provider phải không âm và có tối đa 2 chữ số thập phân.");
        if (held == null || held.signum() <= 0 || held.stripTrailingZeros().scale() > 2)
            throw new IllegalStateException("Không còn khoản ký quỹ hợp lệ để xử lý tranh chấp.");
        if (gross.compareTo(held) > 0)
            throw new IllegalArgumentException("Số tiền phán quyết vượt khoản ký quỹ còn giữ.");
        return gross.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static String reason(String note) {
        String value = note == null ? "" : note.strip();
        if (value.length() < 20 || value.length() > 2000)
            throw new IllegalArgumentException("Lý do phán quyết phải từ 20 đến 2.000 ký tự.");
        return value;
    }
}
