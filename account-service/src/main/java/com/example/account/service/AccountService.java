package com.example.account.service;

import com.example.account.dto.AccountDetailsResponse;
import com.example.account.dto.BalanceResponse;
import com.example.account.dto.TransactionRequest;
import com.example.account.dto.TransactionResponse;
import com.example.account.model.Transaction;
import com.example.account.repository.TransactionRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final TransactionRepository transactionRepository;

    public TransactionResult applyTransaction(String accountId, TransactionRequest request) {
        var existing = transactionRepository.findByTransactionId(request.getTransactionId());
        if (existing.isPresent()) {
            return new TransactionResult(existing.get(), true);
        }

        Transaction transaction = new Transaction();
        transaction.setTransactionId(request.getTransactionId());
        transaction.setAccountId(accountId);
        transaction.setType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setEventTimestamp(request.getEventTimestamp());

        try {
            Transaction saved = transactionRepository.save(transaction);
            return new TransactionResult(saved, false);
        } catch (DataIntegrityViolationException e) {
            var duplicate = transactionRepository.findByTransactionId(request.getTransactionId())
                    .orElseThrow(() -> new RuntimeException("Transaction not found after constraint violation"));
            return new TransactionResult(duplicate, true);
        }
    }

    public BalanceResponse getBalance(String accountId) {
        Double balance = transactionRepository.computeBalance(accountId);
        String currency = transactionRepository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .findFirst()
                .map(Transaction::getCurrency)
                .orElse("USD");

        return BalanceResponse.builder()
                .accountId(accountId)
                .balance(balance)
                .currency(currency)
                .build();
    }

    public AccountDetailsResponse getAccountDetails(String accountId) {
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
        Double balance = transactionRepository.computeBalance(accountId);

        List<TransactionResponse> transactionResponses = transactions.stream()
                .map(TransactionResponse::from)
                .collect(Collectors.toList());

        return AccountDetailsResponse.builder()
                .accountId(accountId)
                .balance(balance)
                .recentTransactions(transactionResponses)
                .build();
    }

    @Getter
    @RequiredArgsConstructor
    public static class TransactionResult {
        private final Transaction transaction;
        private final boolean duplicate;
    }
}
