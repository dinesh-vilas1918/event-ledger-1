package com.example.gateway.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class ErrorResponse {

    private String error;
    private String message;
    private Integer status;
    private Instant timestamp;
}
