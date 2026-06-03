package org.example.ui;

/**
 * Thrown by ApiClient on non-2xx responses. Carries only the HTTP status code
 * and a clean display message — never URLs, paths, or server body content.
 * Full request/response details are written to the session log instead.
 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String displayMessage) {
        super(displayMessage);
        this.status = status;
    }

    public int getStatus() { return status; }
}
