package com.example.gateway.controller;

import com.example.gateway.dto.BalanceResponse;
import com.example.gateway.dto.EventRequest;
import com.example.gateway.dto.EventResponse;
import com.example.gateway.exception.AccountServiceUnavailableException;
import com.example.gateway.model.Event;
import com.example.gateway.service.AccountServiceClient;
import com.example.gateway.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final AccountServiceClient accountServiceClient;

    @PostMapping("/events")
    public ResponseEntity<EventResponse> submitEvent(@Valid @RequestBody EventRequest request) {
        EventService.EventResult result = eventService.submitEvent(request);
        EventResponse response = EventResponse.from(result.getEvent());

        switch (result.getResultType()) {
            case SUCCESS:
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            case DUPLICATE:
                return ResponseEntity.ok()
                        .header("X-Idempotent-Replay", "true")
                        .body(response);
            case ACCOUNT_SERVICE_UNAVAILABLE:
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            default:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable String id) {
        return eventService.getEvent(id)
                .map(event -> ResponseEntity.ok(EventResponse.from(event)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> getEventsForAccount(@RequestParam String account) {
        List<Event> events = eventService.getEventsForAccount(account);
        List<EventResponse> responses = events.stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        try {
            BalanceResponse balance = accountServiceClient.getBalance(accountId);
            return ResponseEntity.ok(balance);
        } catch (AccountServiceUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
