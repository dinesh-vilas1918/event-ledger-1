package com.example.account.controller;

import com.example.account.dto.AccountDetailsResponse;
import com.example.account.dto.BalanceResponse;
import com.example.account.dto.TransactionRequest;
import com.example.account.dto.TransactionResponse;
import com.example.account.repository.TransactionRepository;
import com.example.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Account Service", description = "Internal account transaction and balance management API")
public class AccountController {

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;

    @Operation(summary = "Apply a transaction", description = "Applies a transaction to an account. Idempotent based on transactionId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transaction created successfully",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "200", description = "Duplicate transaction (idempotent replay)",
                    headers = @Header(name = "X-Idempotent-Replay", description = "Set to true for duplicate requests"),
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @Parameter(description = "Account ID", required = true) @PathVariable String accountId,
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

    @Operation(summary = "Get account balance", description = "Retrieves the computed balance for an account (sum of credits minus debits).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BalanceResponse.class)))
    })
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Account ID", required = true) @PathVariable String accountId) {
        BalanceResponse balance = accountService.getBalance(accountId);
        return ResponseEntity.ok(balance);
    }

    @Operation(summary = "Get account details", description = "Retrieves account details including balance and all transactions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AccountDetailsResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AccountDetailsResponse> getAccountDetails(
            @Parameter(description = "Account ID", required = true) @PathVariable String accountId) {
        if (!transactionRepository.existsByAccountId(accountId)) {
            return ResponseEntity.notFound().build();
        }

        AccountDetailsResponse details = accountService.getAccountDetails(accountId);
        return ResponseEntity.ok(details);
    }

    @Operation(summary = "Health check", description = "Simple health check endpoint.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
