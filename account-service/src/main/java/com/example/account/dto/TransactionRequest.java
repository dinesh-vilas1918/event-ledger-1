package com.example.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class TransactionRequest {

    @NotBlank
    private String transactionId;

    @NotBlank
    @Pattern(regexp = "CREDIT|DEBIT")
    private String type;

    @NotNull
    @Positive
    private Double amount;

    @NotBlank
    private String currency;

    @NotNull
    private OffsetDateTime eventTimestamp;
}
