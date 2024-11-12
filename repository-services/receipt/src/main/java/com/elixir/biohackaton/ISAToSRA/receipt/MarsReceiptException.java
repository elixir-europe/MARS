package com.elixir.biohackaton.ISAToSRA.receipt;

import lombok.Getter;

public class MarsReceiptException extends RuntimeException {
    @Getter
    private final String receiptErrorMessage;

    public MarsReceiptException(final String receiptErrorMessage) {
        this.receiptErrorMessage = receiptErrorMessage;
    }

    public MarsReceiptException(final String receiptErrorMessage, final Exception exception) {
        this.addSuppressed(exception);
        this.receiptErrorMessage = receiptErrorMessage;
    }
}
