package com.example.account.controller;

import com.example.account.dto.AccountDetailsResponse;
import com.example.account.dto.BalanceResponse;
import com.example.account.dto.TransactionRequest;
import com.example.account.dto.TransactionResponse;
import com.example.account.repository.TransactionRepository;
import com.example.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;

    @PostMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest request) {

        AccountService.TransactionResult result = accountService.applyTransaction(accountId, request);
        TransactionResponse response = TransactionResponse.from(result.getTransaction());

        if (result.isDuplicate()) {
            return ResponseEntity.ok()
                    .header("X-Idempotent-Replay", "true")
                    .body(response);
        } else {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        BalanceResponse balance = accountService.getBalance(accountId);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AccountDetailsResponse> getAccountDetails(@PathVariable String accountId) {
        if (!transactionRepository.existsByAccountId(accountId)) {
            return ResponseEntity.notFound().build();
        }

        AccountDetailsResponse details = accountService.getAccountDetails(accountId);
        return ResponseEntity.ok(details);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
