package com.example.gateway.controller;

import com.example.gateway.dto.BalanceResponse;
import com.example.gateway.dto.EventRequest;
import com.example.gateway.dto.EventResponse;
import com.example.gateway.exception.AccountServiceUnavailableException;
import com.example.gateway.model.Event;
import com.example.gateway.service.AccountServiceClient;
import com.example.gateway.service.EventService;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Tag(name = "Event Gateway", description = "Public-facing event submission and account query API")
public class EventController {

    private final EventService eventService;
    private final AccountServiceClient accountServiceClient;

    @Operation(summary = "Submit an event", description = "Submits a transaction event. Idempotent based on eventId. Event persists even if account service is unavailable.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Event created and forwarded successfully",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "200", description = "Duplicate event (idempotent replay)",
                    headers = @Header(name = "X-Idempotent-Replay", description = "Set to true for duplicate requests"),
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "503", description = "Event persisted but account service unavailable",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
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

    @Operation(summary = "Get event by ID", description = "Retrieves a single event by its eventId from local storage.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Event found",
                    content = @Content(schema = @Schema(implementation = EventResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @GetMapping("/events/{id}")
    public ResponseEntity<EventResponse> getEvent(
            @Parameter(description = "Event ID", required = true) @PathVariable String id) {
        return eventService.getEvent(id)
                .map(event -> ResponseEntity.ok(EventResponse.from(event)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get events for account", description = "Retrieves all events for a specific account from local storage, ordered by event timestamp.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully",
                    content = @Content(schema = @Schema(implementation = EventResponse.class)))
    })
    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> getEventsForAccount(
            @Parameter(description = "Account ID", required = true) @RequestParam String account) {
        List<Event> events = eventService.getEventsForAccount(account);
        List<EventResponse> responses = events.stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get account balance", description = "Retrieves account balance from account service (passthrough).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "503", description = "Account service unavailable")
    })
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Account ID", required = true) @PathVariable String accountId) {
        try {
            BalanceResponse balance = accountServiceClient.getBalance(accountId);
            return ResponseEntity.ok(balance);
        } catch (AccountServiceUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
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
