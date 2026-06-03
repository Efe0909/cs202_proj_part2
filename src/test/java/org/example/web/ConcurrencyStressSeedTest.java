package org.example.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = org.example.BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.config=classpath:logback-it.xml")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Concurrency stress: 20x placeOrder, 20x coupon resolve, 10x lifecycle")
class ConcurrencyStressSeedTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private DataSource dataSource;

    private String url(String path) { return "http://localhost:" + port + path; }

    private ResponseEntity<String> post(String path, Object body) {
        return rest.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body), String.class);
    }

    private ResponseEntity<String> put(String path, Object body) {
        return rest.exchange(url(path), HttpMethod.PUT,
                new HttpEntity<>(body), String.class);
    }

    @Test
    @DisplayName("20 parallel placeOrder calls all succeed with distinct ids + correct totals")
    void stress_placeOrder20x() throws Exception {
        int N = 20;
        Callable<ResponseEntity<String>> task = () -> post("/api/orders", Map.of(
                "customerId", 4, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));

        List<ResponseEntity<String>> results = parallel(N, task);

        Set<Integer> orderIds = new HashSet<>();
        for (int i = 0; i < N; i++) {
            ResponseEntity<String> r = results.get(i);
            assertTrue(r.getStatusCode().is2xxSuccessful(),
                    "order " + i + " must succeed: " + r.getStatusCode()
                            + " " + r.getBody());
            assertTrue(r.getBody() != null && r.getBody().contains("\"totalPrice\":45.0"),
                    "order " + i + " total must be 45.0, body=" + r.getBody());
            int id = extractOrderId(r.getBody());
            assertTrue(orderIds.add(id),
                    "order id " + id + " produced more than once (race)");
        }
        assertEquals(N, orderIds.size(), "every concurrent call must yield a distinct order id");
    }

    @Test
    @DisplayName("20 parallel coupon-applied placeOrder: exactly 1 wins the single-use coupon, rest get 400")
    void stress_couponResolve20x() throws Exception {
        int N = 20;
        Callable<ResponseEntity<String>> task = () -> post("/api/orders", Map.of(
                "customerId", 4, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1)),
                "couponCode", "KEBAB10"));

        List<ResponseEntity<String>> results = parallel(N, task);
        int successes = 0;
        int failures = 0;
        for (int i = 0; i < N; i++) {
            ResponseEntity<String> r = results.get(i);
            if (r.getStatusCode().is2xxSuccessful()) {
                assertTrue(r.getBody() != null && r.getBody().contains("\"totalPrice\":40.5"),
                        "winning coupon order must have total 40.5, body=" + r.getBody());
                successes++;
            } else {
                assertEquals(400, r.getStatusCode().value(),
                        "non-winning order must be 400, got " + r.getStatusCode());
                failures++;
            }
        }
        assertEquals(1, successes, "exactly 1 order must win the single-use coupon");
        assertEquals(N - 1, failures, "the remaining " + (N - 1) + " orders must be rejected");
    }

    @Test
    @DisplayName("10 parallel accept transitions (SENT->PREPARING) on distinct orders")
    void stress_lifecycle10x() throws Exception {
        int N = 10;

        List<Integer> orderIds = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            ResponseEntity<String> r = post("/api/orders", Map.of(
                    "customerId", 4, "restaurantId", 1,
                    "items", List.of(Map.of("itemId", 1, "quantity", 1))));
            assertTrue(r.getStatusCode().is2xxSuccessful(),
                    "setup order " + i + " must succeed: " + r.getBody());
            orderIds.add(extractOrderId(r.getBody()));
        }

        List<Callable<ResponseEntity<String>>> acceptTasks = new ArrayList<>();
        for (int oid : orderIds) {
            acceptTasks.add(() -> put("/api/orders/" + oid + "/accept",
                    Map.of("managerId", 1)));
        }
        List<ResponseEntity<String>> acceptResults = parallel(acceptTasks);
        for (int i = 0; i < N; i++) {
            assertTrue(acceptResults.get(i).getStatusCode().is2xxSuccessful(),
                    "accept " + orderIds.get(i) + " failed: "
                            + acceptResults.get(i).getStatusCode()
                            + " " + acceptResults.get(i).getBody());
        }

        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            for (int oid : orderIds) {
                try (var rs = st.executeQuery(
                        "SELECT status FROM `Order` WHERE order_id = " + oid)) {
                    assertTrue(rs.next(), "order " + oid + " must exist");
                    assertEquals("PREPARING", rs.getString("status"),
                            "order " + oid + " must be PREPARING after parallel accept");
                }
            }
        }
    }

    private <T> List<T> parallel(int n, Callable<T> task) throws Exception {
        List<Callable<T>> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) tasks.add(task);
        return parallel(tasks);
    }

    private <T> List<T> parallel(List<Callable<T>> tasks) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(10);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) futures.add(ex.submit(task));
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) results.add(f.get());
            return results;
        } finally {
            ex.shutdownNow();
        }
    }

    private static int extractOrderId(String json) {
        int i = json.indexOf("\"orderId\"");
        if (i < 0) throw new IllegalStateException("orderId not in " + json);
        int colon = json.indexOf(":", i);
        int end = colon + 1;
        while (end < json.length() && !Character.isDigit(json.charAt(end))) end++;
        int start = end;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Integer.parseInt(json.substring(start, end));
    }

    @AfterAll
    void restoreSeedWatermarks() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM Rating    WHERE rating_id > 10");
            st.executeUpdate("DELETE FROM OrderItem WHERE order_id  > 12");
            st.executeUpdate("DELETE FROM `Order`   WHERE order_id  > 12");
        }
    }
}
