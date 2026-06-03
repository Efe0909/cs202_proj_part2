package org.example.ui;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

import java.util.Optional;

/**
 * Small UI helpers shared across views: cleanly extracting the human-readable
 * backend error message out of the {@code RuntimeException} thrown by
 * {@link ApiClient}, and standard confirmation dialogs.
 *
 * <p>{@code ApiClient} throws messages shaped like:
 * {@code "POST /orders failed [400]: <backend body>"}. The backend body is
 * usually a short human message (or a JSON object with a "message"/"error"
 * field). These helpers strip the {@code "METHOD /path failed [code]:"} prefix
 * and unwrap a JSON message so the user only sees the meaningful part.
 */
final class UiUtil {

    private UiUtil() {}

    /** Friendly, one-line message extracted from any exception thrown by ApiClient. */
    static String friendlyMessage(Throwable t) {
        if (t == null) {
            return "Unexpected error.";
        }

        // Fast path: ApiException already carries a clean display message.
        if (t instanceof ApiException api) {
            return api.getMessage();
        }
        // Unwrap a wrapped ApiException (e.g. ExecutionException).
        Throwable cause = t.getCause();
        if (cause instanceof ApiException api) {
            return api.getMessage();
        }

        String raw = t.getMessage();
        if (raw == null || raw.isBlank()) {
            // Fall back to a cause message if present, else the class name.
            if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
                raw = cause.getMessage();
            } else {
                return "Unexpected error. Please try again.";
            }
        }

        // Connection-level failures (no backend reachable) read poorly raw.
        if (raw.contains("Connection refused") || raw.contains("ConnectException")
                || raw.contains("failed to respond") || raw.contains("UnknownHost")) {
            return "Could not reach the server. Please check your connection and try again.";
        }

        // Fallback: strip the legacy "METHOD /path failed [code]: body" wrapper
        // in case a non-ApiClient exception still uses that format.
        int marker = raw.indexOf("failed [");
        if (marker >= 0) {
            int colon = raw.indexOf(':', marker);
            if (colon >= 0 && colon + 1 < raw.length()) {
                raw = raw.substring(colon + 1).trim();
            } else {
                int open = raw.indexOf('[', marker);
                int close = raw.indexOf(']', marker);
                if (open >= 0 && close > open) {
                    String code = raw.substring(open + 1, close).trim();
                    return statusFallback(code);
                }
            }
        }

        // If the body is JSON like {"message":"..."} or {"error":"..."}, unwrap it.
        String unwrapped = unwrapJsonMessage(raw);
        if (unwrapped != null && !unwrapped.isBlank()) {
            raw = unwrapped;
        }

        raw = raw.trim();
        if (raw.isEmpty()) {
            return "Request failed. Please try again.";
        }
        return raw;
    }

    private static String statusFallback(String code) {
        switch (code) {
            case "400": return "The request was invalid. Please check your input.";
            case "401": return "Invalid credentials.";
            case "403": return "You are not allowed to perform this action.";
            case "404": return "The requested item was not found.";
            case "409": return "This action conflicts with the current state.";
            case "500": return "Server error. Please try again later.";
            default:    return "Request failed (" + code + ").";
        }
    }

    /** Very small/forgiving JSON message unwrapper (no Jackson dependency here). */
    private static String unwrapJsonMessage(String body) {
        String b = body.trim();
        if (!b.startsWith("{")) {
            return null;
        }
        for (String key : new String[] {"message", "error", "detail"}) {
            String token = "\"" + key + "\"";
            int k = b.indexOf(token);
            if (k < 0) {
                continue;
            }
            int colon = b.indexOf(':', k + token.length());
            if (colon < 0) {
                continue;
            }
            int q1 = b.indexOf('"', colon + 1);
            if (q1 < 0) {
                continue;
            }
            int q2 = b.indexOf('"', q1 + 1);
            if (q2 < 0) {
                continue;
            }
            String val = b.substring(q1 + 1, q2).trim();
            if (!val.isEmpty()) {
                return val;
            }
        }
        return null;
    }

    /** Shows an error alert with a cleaned-up message. */
    static void errorAlert(String header, Throwable t) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText(header);
        a.setContentText(friendlyMessage(t));
        a.showAndWait();
    }

    /** Confirmation dialog; returns true only if the user explicitly confirms. */
    static boolean confirm(String title, String header, String content) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(header);
        a.setContentText(content);
        styleDialog(a);
        Optional<ButtonType> res = a.showAndWait();
        return res.isPresent() && res.get() == ButtonType.OK;
    }

    // ── Shared styling helpers ──────────────────────────────────────────────

    /** Classpath location of the single shared stylesheet. */
    static final String STYLESHEET = "/styles/app.css";

    /** Applies the shared stylesheet to a Scene (idempotent). */
    static Scene styled(Scene scene) {
        try {
            String css = UiUtil.class.getResource(STYLESHEET).toExternalForm();
            if (!scene.getStylesheets().contains(css)) {
                scene.getStylesheets().add(css);
            }
        } catch (Exception ignored) {
            // Missing stylesheet must never break the app.
        }
        return scene;
    }

    /** Adds the stylesheet to any dialog that has a DialogPane. */
    private static void styleDialog(Alert a) {
        try {
            a.getDialogPane().getStylesheets().add(
                    UiUtil.class.getResource(STYLESHEET).toExternalForm());
        } catch (Exception ignored) { }
    }

    /** Error alert (styled) with a cleaned-up message. */
    static void error(String header, Throwable t) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText(header);
        a.setContentText(friendlyMessage(t));
        styleDialog(a);
        a.showAndWait();
    }

    /** Convenience: a Label carrying one or more style classes. */
    static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }
}
