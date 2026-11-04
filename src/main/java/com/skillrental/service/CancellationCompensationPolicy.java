package com.skillrental.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Checks compensation amount and explanations before any change to escrow. */
public final class CancellationCompensationPolicy {
    private CancellationCompensationPolicy() {}

    public static BigDecimal amount(BigDecimal proposed, BigDecimal held) {
        if (proposed == null || proposed.signum() < 0
                || proposed.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Chi phí đề xuất phải là số tiền hợp lệ, tối đa 2 chữ số thập phân.");
        }
        if (held == null || held.signum() <= 0 || held.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException("Đơn không còn khoản tiền ký quỹ hợp lệ để bồi hoàn.");
        }
        if (proposed.compareTo(held) > 0) {
            throw new IllegalArgumentException("Chi phí đề xuất không được vượt số tiền Customer đang ký quỹ.");
        }
        return proposed.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static String description(String value, int min, int max, String label) {
        String result = value == null ? "" : value.strip();
        if (result.length() < min || result.length() > max) {
            throw new IllegalArgumentException(label + " phải có từ " + min + " đến " + max + " ký tự.");
        }
        return result;
    }
}
