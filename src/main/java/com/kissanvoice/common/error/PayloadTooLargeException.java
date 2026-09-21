package com.kissanvoice.common.error;

/**
 * Business-level size guard, independent of the servlet multipart limit.
 * Keeps the 10 MB rule testable without standing up multipart infrastructure,
 * and gives a well-formed ProblemDetail rather than a container error page.
 */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
