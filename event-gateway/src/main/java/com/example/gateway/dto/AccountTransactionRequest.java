package com.example.gateway.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class AccountTransactionRequest {

    private String transactionId;
    private String type;
    private Double amount;
    private String currency;
    private OffsetDateTime eventTimestamp;
}
