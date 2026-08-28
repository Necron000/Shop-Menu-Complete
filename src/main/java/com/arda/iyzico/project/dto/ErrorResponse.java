package com.arda.iyzico.project.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {
    private final String message;
    private final int status;
    private final Instant timestamp;
}
