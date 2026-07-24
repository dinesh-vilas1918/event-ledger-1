package com.example.gateway.dto;

import com.example.gateway.model.Event;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class EventResponse {

    private String eventId;
    private String accountId;
    private String type;
    private Double amount;
    private String currency;
    private OffsetDateTime eventTimestamp;
    private String status;

    public static EventResponse from(Event event) {
        return EventResponse.builder()
                .eventId(event.getEventId())
                .accountId(event.getAccountId())
                .type(event.getType())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .eventTimestamp(event.getEventTimestamp())
                .status(event.getStatus())
                .build();
    }
}
