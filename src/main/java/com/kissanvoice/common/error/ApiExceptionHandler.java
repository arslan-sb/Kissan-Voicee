package com.kissanvoice.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.ConstraintViolationException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves this service as an RFC 9457 ProblemDetail.
 *
 * The prototype returned ad-hoc dicts - {'success': False, 'error': '...'} - with
 * HTTP 200 attached, so no client could tell success from failure without parsing
 * the body.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String BASE = "https://kissanvoice.dev/problems/";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail onNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), "not-found");
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail onConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), "conflict");
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail onForbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), "forbidden");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail onTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload too large",
                "The audio file exceeds the configured maximum size.", "upload-too-large");
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ProblemDetail onPayloadTooLarge(PayloadTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload too large", ex.getMessage(), "upload-too-large");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), "invalid-request");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid.", "validation-failed");
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        pd.setProperty("errors", errors);
        return pd;
    }

    /** Bean Validation on query/path params (@Validated on a controller). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail onConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more parameters are invalid.", "validation-failed");
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        pd.setProperty("errors", errors);
        return pd;
    }

    /** Spring 6.1+ raises this for method-level parameter validation failures. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail onHandlerValidation(HandlerMethodValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more parameters are invalid.", "validation-failed");
    }

    /** e.g. a path variable that is not a well-formed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "'" + ex.getName() + "' is not a valid " +
                        (ex.getRequiredType() == null ? "value" : ex.getRequiredType().getSimpleName()) + ".",
                "invalid-parameter");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail onMissingPart(MissingServletRequestPartException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Missing part",
                "Required multipart part '" + ex.getRequestPartName() + "' is missing.", "missing-part");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail onMissingParam(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Missing parameter",
                "Required parameter '" + ex.getParameterName() + "' is missing.", "missing-parameter");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request body",
                "The request body could not be parsed as JSON.", "malformed-body");
    }

    /**
     * Must be declared explicitly: without it the catch-all below turns an
     * authorisation failure into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail onAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have access to this resource.", "forbidden");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail onUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "An unexpected error occurred.", "internal-error");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String slug) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(BASE + slug));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
