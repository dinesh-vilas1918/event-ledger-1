package com.example.gateway.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BalanceResponse {

    private String accountId;
    private Double balance;
    private String currency;
}
