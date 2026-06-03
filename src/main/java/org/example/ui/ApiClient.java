package org.example.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thin HTTP client used by JavaFX views to call the Spring Boot REST API.
 * <p>
 * Every request and response is logged to stdout (for live operator tailing)
 * and to {@code logs/ui-session.log} (relative to the working directory,
 * i.e. the repo root when {@code mvn javafx:run} runs).
 * <p>
 * Non-2xx responses throw {@link ApiException} with a clean, user-safe message.
 * Network-level failures throw {@link ApiException} with status 0.
 */
public class ApiClient {

    private static final String BASE_URL;
    static {
        String port = System.getenv("BACKEND_PORT");
        if (port == null || port.isBlank()) {
            port = "8080";
        }
        BASE_URL = "http://localhost:" + port + "/api";
    }

    private static final HttpClient HTTP  = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = buildMapper();

    /** Shared, fully-configured mapper for all UI views — supports LocalDateTime. */
    public static ObjectMapper mapper() { return JSON; }

    private static ObjectMapper buildMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Path to the rolling session log file. */
    private static final Path LOG_FILE;

    static {
        Path p = Paths.get("logs", "ui-session.log");
        try {
            Files.createDirectories(p.getParent());
        } catch (IOException ignored) { }
        LOG_FILE = p;
        // Write session start header so each run is easy to identify in the log.
        String header = "--- session started at " + LocalDateTime.now().format(DT_FMT) + " ---";
        fileLog(header);
        System.out.println(header);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static <T> T get(String path, Class<T> type) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .GET().build();
            HttpResponse<String> res = send(req, "GET", path);
            // Callers that pass String.class want the raw body to parse themselves.
            if (type == String.class) return type.cast(res.body());
            return JSON.readValue(res.body(), type);
        } catch (ApiException e) {
            throw e;
        } catch (ConnectException e) {
            handleNetworkError("GET", path, e);
            throw new ApiException(0, "Could not reach the server");
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            // Deserialization failure — log it but don't mask as network error
            handleNetworkError("GET", path, e);
            throw new ApiException(0, "Unexpected response format");
        } catch (Exception e) {
            handleNetworkError("GET", path, e);
            throw new ApiException(0, "Could not reach the server");
        }
    }

    public static String post(String path, Object body) {
        try {
            String json = JSON.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> res = send(req, "POST", path);
            return res.body();
        } catch (ApiException e) {
            throw e;
        } catch (ConnectException e) {
            handleNetworkError("POST", path, e);
            throw new ApiException(0, "Could not reach the server");
        } catch (Exception e) {
            handleNetworkError("POST", path, e);
            throw new ApiException(0, "Could not reach the server");
        }
    }

    public static String put(String path, Object body) {
        try {
            String json = JSON.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> res = send(req, "PUT", path);
            return res.body();
        } catch (ApiException e) {
            throw e;
        } catch (ConnectException e) {
            handleNetworkError("PUT", path, e);
            throw new ApiException(0, "Could not reach the server");
        } catch (Exception e) {
            handleNetworkError("PUT", path, e);
            throw new ApiException(0, "Could not reach the server");
        }
    }

    public static void delete(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .DELETE().build();
            send(req, "DELETE", path);
        } catch (ApiException e) {
            throw e;
        } catch (ConnectException e) {
            handleNetworkError("DELETE", path, e);
            throw new ApiException(0, "Could not reach the server");
        } catch (Exception e) {
            handleNetworkError("DELETE", path, e);
            throw new ApiException(0, "Could not reach the server");
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Send the request, log it, and check status; returns the response on 2xx. */
    private static HttpResponse<String> send(HttpRequest req, String method, String path)
            throws Exception {
        String ts = now();
        System.out.println("[" + ts + "] → " + method + " " + path);
        fileLog("[" + ts + "] → " + method + " " + path);

        long start = System.currentTimeMillis();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;

        int code = res.statusCode();
        String ts2 = now();
        if (code >= 400) {
            String body = res.body() != null ? res.body() : "";
            // Stdout: full detail for operators
            System.out.println("[" + ts2 + "] ← " + code + " " + method + " " + path
                    + " (" + elapsed + "ms)  body: " + body);
            // File: same
            fileLog("[" + ts2 + "] ← " + code + " " + method + " " + path
                    + " (" + elapsed + "ms)  body: " + body);
            throw new ApiException(code, cleanMessage(code));
        } else {
            System.out.println("[" + ts2 + "] ← " + code + " " + method + " " + path
                    + " (" + elapsed + "ms)");
            fileLog("[" + ts2 + "] ← " + code + " " + method + " " + path
                    + " (" + elapsed + "ms)");
            return res;
        }
    }

    /** Convert an HTTP status code to a clean, user-safe display message. */
    private static String cleanMessage(int code) {
        switch (code) {
            case 400: return "Invalid request";
            case 401: return "Invalid credentials";
            case 403: return "Access denied";
            case 404: return "Not found";
            case 409: return "Conflict — check your input";
            default:
                if (code >= 500) return "Server error (" + code + ")";
                return "Request failed (" + code + ")";
        }
    }

    /** Called on IOException/ConnectException — logs stack trace to file. */
    private static void handleNetworkError(String method, String path, Exception e) {
        String ts = now();
        String summary = "[" + ts + "] ERROR " + method + " " + path
                + " — " + e.getClass().getSimpleName() + ": " + e.getMessage();
        System.out.println(summary);

        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        fileLog(summary + "\n" + sw);
    }

    private static String now() {
        return LocalDateTime.now().format(TIME_FMT);
    }

    /** Append a line to the session log file; silently swallows IO errors. */
    private static void fileLog(String line) {
        try {
            Files.writeString(LOG_FILE, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }
}
