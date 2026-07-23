package com.example.account.repository;

import com.example.account.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionId(String transactionId);

    List<Transaction> findByAccountIdOrderByEventTimestampAsc(String accountId);

    @Query("SELECT COALESCE(SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0.0) " +
           "FROM Transaction t WHERE t.accountId = :accountId")
    Double computeBalance(@Param("accountId") String accountId);

    boolean existsByAccountId(String accountId);
}
