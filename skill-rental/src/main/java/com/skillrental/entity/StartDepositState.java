package com.skillrental.entity;

/**
 * RESERVED: one-third milestone earmarked within the already locked full order total.
 * CLOSED: escrow resolved by final settlement or cancellation; no separate milestone payout.
 * null: awaiting both confirmations, or legacy record without a reconstructed milestone.
 */
public enum StartDepositState {
    RESERVED,
    CLOSED
}
