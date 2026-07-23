package com.example.account.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BalanceResponse {

    private String accountId;
    private Double balance;
    private String currency;
}
