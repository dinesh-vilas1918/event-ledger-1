package com.example.gateway.service;

import com.example.gateway.dto.AccountTransactionRequest;
import com.example.gateway.dto.BalanceResponse;
import com.example.gateway.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class AccountServiceClient {

    private final RestClient restClient;

    @CircuitBreaker(name = "accountService", fallbackMethod = "applyTransactionFallback")
    public void applyTransaction(String accountId, AccountTransactionRequest request) {
        String traceId = MDC.get("traceId");

        restClient.post()
                .uri("/accounts/{accountId}/transactions", accountId)
                .header("X-Trace-Id", traceId)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private void applyTransactionFallback(String accountId, AccountTransactionRequest request, Throwable throwable) {
        throw new AccountServiceUnavailableException("Account Service is unavailable", throwable);
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "getBalanceFallback")
    public BalanceResponse getBalance(String accountId) {
        String traceId = MDC.get("traceId");

        return restClient.get()
                .uri("/accounts/{accountId}/balance", accountId)
                .header("X-Trace-Id", traceId)
                .retrieve()
                .body(BalanceResponse.class);
    }

    private BalanceResponse getBalanceFallback(String accountId, Throwable throwable) {
        throw new AccountServiceUnavailableException("Account Service is unavailable", throwable);
    }
}
