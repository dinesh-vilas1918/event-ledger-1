package com.example.account.dto;

import com.example.account.model.Transaction;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class TransactionResponse {

    private String transactionId;
    private String accountId;
    private String type;
    private Double amount;
    private String currency;
    private OffsetDateTime eventTimestamp;

    public static TransactionResponse from(Transaction transaction) {
        return TransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .accountId(transaction.getAccountId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .eventTimestamp(transaction.getEventTimestamp())
                .build();
    }
}
