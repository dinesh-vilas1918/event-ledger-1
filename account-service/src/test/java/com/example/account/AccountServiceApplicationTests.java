package com.example.account;

import com.example.account.model.Transaction;
import com.example.account.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    void testIdempotency_sameTransactionIdTwice_createsOnlyOneRow() throws Exception {
        String accountId = "ACC123";
        String transactionId = "TXN001";

        Map<String, Object> request = new HashMap<>();
        request.put("transactionId", transactionId);
        request.put("type", "CREDIT");
        request.put("amount", 100.0);
        request.put("currency", "USD");
        request.put("eventTimestamp", OffsetDateTime.now().toString());

        String requestBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(transactionId));

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.transactionId").value(transactionId));

        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
        assertEquals(1, transactions.size());
    }

    @Test
    void testBalanceComputation_sumOfCreditsMinusDebits() throws Exception {
        String accountId = "ACC456";

        Map<String, Object> credit1 = new HashMap<>();
        credit1.put("transactionId", "TXN101");
        credit1.put("type", "CREDIT");
        credit1.put("amount", 200.0);
        credit1.put("currency", "USD");
        credit1.put("eventTimestamp", OffsetDateTime.now().toString());

        Map<String, Object> debit1 = new HashMap<>();
        debit1.put("transactionId", "TXN102");
        debit1.put("type", "DEBIT");
        debit1.put("amount", 50.0);
        debit1.put("currency", "USD");
        debit1.put("eventTimestamp", OffsetDateTime.now().plusSeconds(1).toString());

        Map<String, Object> credit2 = new HashMap<>();
        credit2.put("transactionId", "TXN103");
        credit2.put("type", "CREDIT");
        credit2.put("amount", 150.0);
        credit2.put("currency", "USD");
        credit2.put("eventTimestamp", OffsetDateTime.now().plusSeconds(2).toString());

        Map<String, Object> debit2 = new HashMap<>();
        debit2.put("transactionId", "TXN104");
        debit2.put("type", "DEBIT");
        debit2.put("amount", 100.0);
        debit2.put("currency", "USD");
        debit2.put("eventTimestamp", OffsetDateTime.now().plusSeconds(3).toString());

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credit1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debit1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credit2)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debit2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/" + accountId + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balance").value(200.0));
    }

    @Test
    void testValidation_missingAndInvalidFields_return400() throws Exception {
        String accountId = "ACC789";

        Map<String, Object> missingFields = new HashMap<>();
        missingFields.put("transactionId", "");
        missingFields.put("type", "CREDIT");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingFields)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        Map<String, Object> invalidType = new HashMap<>();
        invalidType.put("transactionId", "TXN200");
        invalidType.put("type", "INVALID");
        invalidType.put("amount", 100.0);
        invalidType.put("currency", "USD");
        invalidType.put("eventTimestamp", OffsetDateTime.now().toString());

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidType)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        Map<String, Object> negativeAmount = new HashMap<>();
        negativeAmount.put("transactionId", "TXN201");
        negativeAmount.put("type", "CREDIT");
        negativeAmount.put("amount", -50.0);
        negativeAmount.put("currency", "USD");
        negativeAmount.put("eventTimestamp", OffsetDateTime.now().toString());

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeAmount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
