package com.example.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
public class EventRequest {

    @NotBlank
    private String eventId;

    @NotBlank
    private String accountId;

    @NotBlank
    @Pattern(regexp = "CREDIT|DEBIT")
    private String type;

    @NotNull
    @Positive
    private Double amount;

    @NotBlank
    private String currency;

    @NotNull
    private OffsetDateTime eventTimestamp;

    private Map<String, Object> metadata;
}
