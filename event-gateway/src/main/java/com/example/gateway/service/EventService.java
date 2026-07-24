package com.example.gateway.service;

import com.example.gateway.dto.AccountTransactionRequest;
import com.example.gateway.dto.EventRequest;
import com.example.gateway.exception.AccountServiceUnavailableException;
import com.example.gateway.model.Event;
import com.example.gateway.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;

    public EventResult submitEvent(EventRequest request) {
        Optional<Event> existing = eventRepository.findByEventId(request.getEventId());

        if (existing.isPresent()) {
            Event event = existing.get();
            if ("APPLIED".equals(event.getStatus())) {
                return new EventResult(event, ResultType.DUPLICATE);
            }
            
            if ("PENDING".equals(event.getStatus()) || "ACCOUNT_SERVICE_UNAVAILABLE".equals(event.getStatus())) {
                return attemptAccountServiceCall(event);
            }
        }

        Event event = new Event();
        event.setEventId(request.getEventId());
        event.setAccountId(request.getAccountId());
        event.setType(request.getType());
        event.setAmount(request.getAmount());
        event.setCurrency(request.getCurrency());
        event.setEventTimestamp(request.getEventTimestamp());
        event.setMetadataJson(convertMetadataToJson(request.getMetadata()));
        event.setStatus("PENDING");

        try {
            event = eventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            Event duplicate = eventRepository.findByEventId(request.getEventId())
                    .orElseThrow(() -> new RuntimeException("Event not found after constraint violation"));
            if ("APPLIED".equals(duplicate.getStatus())) {
                return new EventResult(duplicate, ResultType.DUPLICATE);
            }
            return attemptAccountServiceCall(duplicate);
        }

        return attemptAccountServiceCall(event);
    }

    private EventResult attemptAccountServiceCall(Event event) {
        AccountTransactionRequest accountRequest = AccountTransactionRequest.builder()
                .transactionId(event.getEventId())
                .type(event.getType())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .eventTimestamp(event.getEventTimestamp())
                .build();

        try {
            accountServiceClient.applyTransaction(event.getAccountId(), accountRequest);
            event.setStatus("APPLIED");
            eventRepository.save(event);
            return new EventResult(event, ResultType.SUCCESS);
        } catch (AccountServiceUnavailableException e) {
            event.setStatus("ACCOUNT_SERVICE_UNAVAILABLE");
            eventRepository.save(event);
            return new EventResult(event, ResultType.ACCOUNT_SERVICE_UNAVAILABLE);
        }
    }

    private String convertMetadataToJson(Object metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize metadata", e);
        }
    }

    public Optional<Event> getEvent(String eventId) {
        return eventRepository.findByEventId(eventId);
    }

    public List<Event> getEventsForAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
    }

    @Getter
    @RequiredArgsConstructor
    public static class EventResult {
        private final Event event;
        private final ResultType resultType;
    }

    public enum ResultType {
        SUCCESS, DUPLICATE, ACCOUNT_SERVICE_UNAVAILABLE
    }
}
