package com.example.account.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class AccountDetailsResponse {

    private String accountId;
    private Double balance;
    private List<TransactionResponse> recentTransactions;
}
