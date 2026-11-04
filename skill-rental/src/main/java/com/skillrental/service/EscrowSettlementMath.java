package com.skillrental.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure calculation: held = gross + refund; gross = fee + Provider net. */
public record EscrowSettlementMath(BigDecimal held, BigDecimal gross, BigDecimal refund,
                                   BigDecimal fee, BigDecimal providerNet, BigDecimal rate) {
    public static EscrowSettlementMath calculate(BigDecimal held, BigDecimal gross, BigDecimal rate) {
        BigDecimal h = currency(held, "Tiền ký quỹ");
        BigDecimal g = currency(gross, "Số tiền quyết toán");
        if (h.signum() <= 0) throw new IllegalStateException("Không có khoản ký quỹ hợp lệ để quyết toán.");
        if (g.compareTo(h) > 0) throw new IllegalStateException("Số tiền chi trả vượt khoản ký quỹ.");
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0
                || rate.stripTrailingZeros().scale() > 8) {
            throw new IllegalStateException("Tỷ lệ phí nền tảng phải nằm trong khoảng 0 đến 1, tối đa 8 chữ số thập phân.");
        }
        BigDecimal fee = g.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = g.subtract(fee);
        BigDecimal returned = h.subtract(g);
        if (net.signum() < 0 || returned.signum() < 0
                || g.compareTo(net.add(fee)) != 0 || h.compareTo(g.add(returned)) != 0) {
            throw new IllegalStateException("Sổ quyết toán không cân bằng; không được thay đổi ví.");
        }
        return new EscrowSettlementMath(h, g, returned, fee, net, rate);
    }

    private static BigDecimal currency(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException(label + " không hợp lệ.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
