package kz.kaspi.fileupload.api.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgument(
            IllegalArgumentException ex,
            ServerWebExchange exchange
    ) {
        log.error("Validation error: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse(ex.getMessage(), exchange.getRequest().getPath().value()))
        );
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationErrors(
            WebExchangeBindException ex,
            ServerWebExchange exchange
    ) {
        Map<String, Object> errors = new HashMap<>();
        ex.getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> response = createErrorResponse("Validation failed", exchange.getRequest().getPath().value());
        response.put("validationErrors", errors);

        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response)
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRuntimeException(
            RuntimeException ex,
            ServerWebExchange exchange
    ) {
        log.error("Internal server error: {}", ex.getMessage(), ex);
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Internal server error", exchange.getRequest().getPath().value()))
        );
    }

    private Map<String, Object> createErrorResponse(String message, String path) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("message", message);
        response.put("path", path);
        return response;
    }
}