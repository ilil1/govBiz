package ai.govbiz.core.controller;

import java.net.URI;
import java.util.List;

import ai.govbiz.core.service.AiServiceHealthException;
import ai.govbiz.core.service.SupportProgramSearchException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(SupportProgramSearchException.class)
    public ResponseEntity<ProblemDetail> handleSupportProgramSearchException(
            SupportProgramSearchException exception,
            HttpServletRequest request
    ) {
        ProblemDefinition definition = supportProgramDefinitionFor(exception.failure());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                definition.status(),
                definition.detail());
        problem.setType(definition.type());
        problem.setTitle(definition.title());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", definition.code());

        return ResponseEntity.status(definition.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AiServiceHealthException.class)
    public ResponseEntity<ProblemDetail> handleAiServiceHealthException(
            AiServiceHealthException exception,
            HttpServletRequest request
    ) {
        ProblemDefinition definition = definitionFor(exception.failure());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                definition.status(),
                definition.detail());
        problem.setType(definition.type());
        problem.setTitle(definition.title());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", definition.code());

        return ResponseEntity.status(definition.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ValidationError> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toValidationError)
                .toList();

        return validationProblem(
                HttpStatus.BAD_REQUEST,
                URI.create("urn:govbiz:problem:request-validation-failed"),
                "Request Validation Failed",
                "One or more request fields are invalid.",
                "REQUEST_VALIDATION_FAILED",
                errors,
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return validationProblem(
                HttpStatus.BAD_REQUEST,
                URI.create("urn:govbiz:problem:request-validation-failed"),
                "Request Validation Failed",
                "The request body is invalid.",
                "REQUEST_VALIDATION_FAILED",
                List.of(),
                request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return validationProblem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                URI.create("urn:govbiz:problem:unsupported-media-type"),
                "Unsupported Media Type",
                "This endpoint accepts application/json requests.",
                "UNSUPPORTED_MEDIA_TYPE",
                List.of(),
                request);
    }

    private ResponseEntity<ProblemDetail> validationProblem(
            HttpStatusCode status,
            URI type,
            String title,
            String detail,
            String code,
            List<ValidationError> errors,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("errors", errors);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ValidationError toValidationError(FieldError fieldError) {
        return new ValidationError(fieldError.getField(), "INVALID_VALUE");
    }

    private static ProblemDefinition definitionFor(AiServiceHealthException.Failure failure) {
        return switch (failure) {
            case UPSTREAM_ERROR -> new ProblemDefinition(
                    HttpStatus.BAD_GATEWAY,
                    URI.create("urn:govbiz:problem:ai-service-upstream-error"),
                    "AI Service Upstream Error",
                    "AI Service returned an unexpected HTTP status.",
                    "AI_SERVICE_UPSTREAM_ERROR");
            case INVALID_RESPONSE -> new ProblemDefinition(
                    HttpStatus.BAD_GATEWAY,
                    URI.create("urn:govbiz:problem:ai-service-invalid-response"),
                    "AI Service Invalid Response",
                    "AI Service returned an invalid response.",
                    "AI_SERVICE_INVALID_RESPONSE");
            case UNAVAILABLE -> new ProblemDefinition(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    URI.create("urn:govbiz:problem:ai-service-unavailable"),
                    "AI Service Unavailable",
                    "AI Service is currently unavailable.",
                    "AI_SERVICE_UNAVAILABLE");
            case TIMEOUT -> new ProblemDefinition(
                    HttpStatus.GATEWAY_TIMEOUT,
                    URI.create("urn:govbiz:problem:ai-service-timeout"),
                    "AI Service Gateway Timeout",
                    "AI Service did not respond within the configured timeout.",
                    "AI_SERVICE_TIMEOUT");
        };
    }

    private static ProblemDefinition supportProgramDefinitionFor(
            SupportProgramSearchException.Failure failure
    ) {
        return switch (failure) {
            case NOT_CONFIGURED -> new ProblemDefinition(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    URI.create("urn:govbiz:problem:support-program-source-not-configured"),
                    "Support Program Search Unavailable",
                    "The support program data source is not configured.",
                    "SUPPORT_PROGRAM_SOURCE_NOT_CONFIGURED");
            case UPSTREAM_ERROR -> new ProblemDefinition(
                    HttpStatus.BAD_GATEWAY,
                    URI.create("urn:govbiz:problem:support-program-source-error"),
                    "Support Program Source Error",
                    "The support program data source returned an unexpected response.",
                    "SUPPORT_PROGRAM_SOURCE_ERROR");
            case INVALID_RESPONSE -> new ProblemDefinition(
                    HttpStatus.BAD_GATEWAY,
                    URI.create("urn:govbiz:problem:support-program-invalid-response"),
                    "Support Program Invalid Response",
                    "The support program data source returned an invalid response.",
                    "SUPPORT_PROGRAM_INVALID_RESPONSE");
            case UNAVAILABLE -> new ProblemDefinition(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    URI.create("urn:govbiz:problem:support-program-source-unavailable"),
                    "Support Program Source Unavailable",
                    "The support program data source is currently unavailable.",
                    "SUPPORT_PROGRAM_SOURCE_UNAVAILABLE");
            case TIMEOUT -> new ProblemDefinition(
                    HttpStatus.GATEWAY_TIMEOUT,
                    URI.create("urn:govbiz:problem:support-program-source-timeout"),
                    "Support Program Source Timeout",
                    "The support program data source did not respond in time.",
                    "SUPPORT_PROGRAM_SOURCE_TIMEOUT");
        };
    }

    private record ProblemDefinition(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            String code
    ) {
    }

    private record ValidationError(String field, String code) {
    }
}
