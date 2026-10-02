import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BurstTest {

  //  private static final String BASE_URL = "http://localhost:8080";
    private static final String BASE_URL =
            System.getProperty("baseUrl", "http://localhost:8080");

    // Total requests in the burst
    private static final int REQUEST_COUNT = 20_000;

    // Maximum requests actively hitting the server at once.
    // This prevents the local machine from being overwhelmed.
    private static final int MAX_IN_FLIGHT = 500;

    private static final String SEAT_NUMBER = "A1";

    public static void main(String[] args) throws Exception {

        System.out.println();
        System.out.println("=============================================");
        System.out.println(" Seat Reservation 20,000 Request Burst Test");
        System.out.println(" Java 25 Virtual Threads");
        System.out.println(" Max In-Flight Requests: " + MAX_IN_FLIGHT);
        System.out.println("=============================================");
        System.out.println();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        // =====================================================
        // 1. HEALTH CHECK
        // =====================================================

        System.out.println("[1/5] Checking application health...");

        HttpRequest healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health/ready"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> healthResponse =
                client.send(
                        healthRequest,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (healthResponse.statusCode() != 200) {

            System.out.println("Health check failed.");
            System.out.println(healthResponse.body());
            return;
        }

        System.out.println("Application is healthy.");
        System.out.println(healthResponse.body());

        // =====================================================
        // 2. CREATE FRESH SHOW
        // =====================================================

        System.out.println();
        System.out.println("[2/5] Creating fresh show...");

        String showName =
                "burst-test-" + UUID.randomUUID();

        String showBody =
                """
                {
                  "name": "%s",
                  "seats": ["A1"],
                  "price_paise": 25000
                }
                """.formatted(showName);

        HttpRequest createShowRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/shows"))
                        .timeout(Duration.ofSeconds(30))
                        .header(
                                "Authorization",
                                "Bearer admin"
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        showBody
                                )
                        )
                        .build();

        HttpResponse<String> showResponse =
                client.send(
                        createShowRequest,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (showResponse.statusCode() != 201) {

            System.out.println("Create show failed.");
            System.out.println(
                    "HTTP Status: "
                            + showResponse.statusCode()
            );

            System.out.println(showResponse.body());

            return;
        }

        // CreateShowResponse returns "id", not "show_id".
        String showId =
                extractString(
                        showResponse.body(),
                        "id"
                );

        if (showId == null) {

            System.out.println(
                    "Could not find show id."
            );

            System.out.println(
                    showResponse.body()
            );

            return;
        }

        System.out.println(
                "Created show: " + showId
        );

        // =====================================================
        // 3. PREPARE CONCURRENCY
        // =====================================================

        System.out.println();
        System.out.println(
                "[3/5] Starting "
                        + REQUEST_COUNT
                        + " virtual-thread requests..."
        );

        System.out.println(
                "Maximum in-flight requests: "
                        + MAX_IN_FLIGHT
        );

        ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor();

        Semaphore semaphore =
                new Semaphore(MAX_IN_FLIGHT);

        List<Future<Integer>> futures =
                new ArrayList<>(REQUEST_COUNT);

        // Counters
        AtomicInteger successCount =
                new AtomicInteger();

        AtomicInteger conflictCount =
                new AtomicInteger();

        AtomicInteger otherStatusCount =
                new AtomicInteger();

        AtomicInteger exceptionCount =
                new AtomicInteger();

        // Track unexpected HTTP status codes
        Map<Integer, AtomicInteger> statusCounts =
                new ConcurrentHashMap<>();

        // Track actual Java exceptions
        Map<String, AtomicInteger> exceptionCounts =
                new ConcurrentHashMap<>();

        long startTime =
                System.currentTimeMillis();

        // =====================================================
        // 4. SUBMIT 20,000 REQUESTS
        // =====================================================

        for (int i = 1; i <= REQUEST_COUNT; i++) {

            final int requestNumber = i;

            Future<Integer> future =
                    executor.submit(() -> {

                        semaphore.acquire();

                        try {

                            String userId =
                                    "burst-user-"
                                            + requestNumber;

                            String idempotencyKey =
                                    "burst-"
                                            + requestNumber
                                            + "-"
                                            + UUID.randomUUID();

                            String requestBody =
                                    """
                                    {
                                      "seats": ["A1"],
                                      "idempotency_key": "%s"
                                    }
                                    """.formatted(
                                            idempotencyKey
                                    );

                            HttpRequest request =
                                    HttpRequest.newBuilder()
                                            .uri(
                                                    URI.create(
                                                            BASE_URL
                                                                    + "/shows/"
                                                                    + showId
                                                                    + "/reserve"
                                                    )
                                            )
                                            .timeout(
                                                    Duration.ofSeconds(
                                                            180
                                                    )
                                            )
                                            .header(
                                                    "Authorization",
                                                    "Bearer "
                                                            + userId
                                            )
                                            .header(
                                                    "Content-Type",
                                                    "application/json"
                                            )
                                            .header(
                                                    "X-Request-ID",
                                                    "burst-"
                                                            + requestNumber
                                            )
                                            .POST(
                                                    HttpRequest.BodyPublishers
                                                            .ofString(
                                                                    requestBody
                                                            )
                                            )
                                            .build();

                            try {

                                HttpResponse<String> response =
                                        client.send(
                                                request,
                                                HttpResponse.BodyHandlers
                                                        .ofString()
                                        );

                                int status =
                                        response.statusCode();

                                statusCounts
                                        .computeIfAbsent(
                                                status,
                                                key ->
                                                        new AtomicInteger()
                                        )
                                        .incrementAndGet();

                                if (status == 201) {

                                    successCount
                                            .incrementAndGet();

                                } else if (status == 409) {

                                    conflictCount
                                            .incrementAndGet();

                                } else {

                                    otherStatusCount
                                            .incrementAndGet();

                                    System.out.println(
                                            "Unexpected HTTP "
                                                    + status
                                                    + " for request "
                                                    + requestNumber
                                    );

                                    System.out.println(
                                            "Response: "
                                                    + response.body()
                                    );
                                }

                                return status;

                            } catch (Exception e) {

                                exceptionCount.incrementAndGet();

                                String exceptionName =
                                        e.getClass()
                                                .getName();

                                exceptionCounts
                                        .computeIfAbsent(
                                                exceptionName,
                                                key ->
                                                        new AtomicInteger()
                                        )
                                        .incrementAndGet();

                                return 0;
                            }

                        } finally {

                            semaphore.release();
                        }
                    });

            futures.add(future);

            if (i % 1000 == 0) {

                System.out.println(
                        "Submitted "
                                + i
                                + " / "
                                + REQUEST_COUNT
                );
            }
        }

        System.out.println();
        System.out.println(
                "All "
                        + REQUEST_COUNT
                        + " requests submitted."
        );

        System.out.println(
                "Waiting for responses..."
        );

        // =====================================================
        // WAIT FOR ALL REQUESTS
        // =====================================================

        for (Future<Integer> future : futures) {

            future.get();
        }

        long endTime =
                System.currentTimeMillis();

        executor.shutdown();

        double durationSeconds =
                (endTime - startTime) / 1000.0;

        // =====================================================
        // 5. FINAL SHOW STATE
        // =====================================================

        System.out.println();
        System.out.println(
                "[4/5] Checking final show state..."
        );

        HttpRequest showStateRequest =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        BASE_URL
                                                + "/shows/"
                                                + showId
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(30)
                        )
                        .GET()
                        .build();

        HttpResponse<String> showStateResponse =
                client.send(
                        showStateRequest,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (showStateResponse.statusCode() != 200) {

            System.out.println(
                    "Could not retrieve final show state."
            );

            System.out.println(
                    showStateResponse.body()
            );

            return;
        }

        String showState =
                showStateResponse.body();

        int totalSeats =
                extractInt(
                        showState,
                        "total_seats"
                );

        int availableSeats =
                extractInt(
                        showState,
                        "available_seats"
                );

        int heldSeats =
                extractInt(
                        showState,
                        "held_seats"
                );

        int confirmedSeats =
                extractInt(
                        showState,
                        "confirmed_seats"
                );

        // =====================================================
        // RESULTS
        // =====================================================

        System.out.println();
        System.out.println(
                "[5/5] Final results"
        );

        System.out.println();
        System.out.println(
                "============================================="
        );

        System.out.println(
                " BURST TEST RESULTS"
        );

        System.out.println(
                "============================================="
        );

        System.out.println();

        System.out.println(
                "Show ID:              "
                        + showId
        );

        System.out.println(
                "Total requests:       "
                        + REQUEST_COUNT
        );

        System.out.println(
                "Max in-flight:        "
                        + MAX_IN_FLIGHT
        );

        System.out.println(
                "Duration:             "
                        + durationSeconds
                        + " seconds"
        );

        System.out.println();

        System.out.println(
                "HTTP 201:             "
                        + successCount.get()
        );

        System.out.println(
                "HTTP 409:             "
                        + conflictCount.get()
        );

        System.out.println(
                "Other HTTP statuses:  "
                        + otherStatusCount.get()
        );

        System.out.println(
                "Client exceptions:    "
                        + exceptionCount.get()
        );

        // =====================================================
        // STATUS BREAKDOWN
        // =====================================================

        System.out.println();
        System.out.println(
                "HTTP status breakdown:"
        );

        statusCounts.entrySet()
                .stream()
                .sorted(
                        Map.Entry.comparingByKey()
                )
                .forEach(entry ->
                        System.out.println(
                                "  "
                                        + entry.getKey()
                                        + " -> "
                                        + entry.getValue()
                                        .get()
                        )
                );

        // =====================================================
        // EXCEPTION BREAKDOWN
        // =====================================================

        if (!exceptionCounts.isEmpty()) {

            System.out.println();
            System.out.println(
                    "Client exception breakdown:"
            );

            exceptionCounts.forEach(
                    (exceptionName, count) ->
                            System.out.println(
                                    "  "
                                            + exceptionName
                                            + " -> "
                                            + count.get()
                            )
            );
        }

        // =====================================================
        // DATABASE STATE
        // =====================================================

        System.out.println();
        System.out.println(
                "Final show state:"
        );

        System.out.println();

        System.out.println(
                "Total seats:          "
                        + totalSeats
        );

        System.out.println(
                "Available seats:      "
                        + availableSeats
        );

        System.out.println(
                "Held seats:           "
                        + heldSeats
        );

        System.out.println(
                "Confirmed seats:      "
                        + confirmedSeats
        );

        // =====================================================
        // VALIDATION
        // =====================================================

        boolean pass = true;

        if (successCount.get() != 1) {

            System.out.println();
            System.out.println(
                    "FAIL: Expected exactly 1 successful reservation."
            );

            pass = false;
        }

        if (conflictCount.get()
                != REQUEST_COUNT - 1) {

            System.out.println();
            System.out.println(
                    "FAIL: Expected "
                            + (REQUEST_COUNT - 1)
                            + " HTTP 409 conflicts."
            );

            pass = false;
        }

        if (otherStatusCount.get() != 0) {

            System.out.println();
            System.out.println(
                    "FAIL: Unexpected HTTP statuses occurred."
            );

            pass = false;
        }

        if (exceptionCount.get() != 0) {

            System.out.println();
            System.out.println(
                    "FAIL: Client-side HTTP exceptions occurred."
            );

            pass = false;
        }

        if (totalSeats != 1) {

            System.out.println();
            System.out.println(
                    "FAIL: Expected total seats = 1."
            );

            pass = false;
        }

        if (availableSeats != 0) {

            System.out.println();
            System.out.println(
                    "FAIL: Expected available seats = 0."
            );

            pass = false;
        }

        if (heldSeats != 0) {

            System.out.println();
            System.out.println(
                    "FAIL: Expected held seats = 0."
            );

            pass = false;
        }

        if (confirmedSeats != 1) {

            System.out.println();
            System.out.println(
                    "FAIL: Expected confirmed seats = 1."
            );

            pass = false;
        }

        int reconciled =
                availableSeats
                        + heldSeats
                        + confirmedSeats;

        if (reconciled != totalSeats) {

            System.out.println();
            System.out.println(
                    "FAIL: Seat reconciliation invariant failed."
            );

            pass = false;
        }

        // =====================================================
        // FINAL RESULT
        // =====================================================

        System.out.println();

        if (pass) {

            System.out.println(
                    "============================================="
            );

            System.out.println(
                    " PASS"
            );

            System.out.println(
                    "============================================="
            );

            System.out.println();

            System.out.println(
                    "Exactly one request won the hot seat."
            );

            System.out.println(
                    "All remaining requests received HTTP 409."
            );

            System.out.println(
                    "No client/network errors occurred."
            );

            System.out.println(
                    "Seat reconciliation is correct."
            );

        } else {

            System.out.println(
                    "============================================="
            );

            System.out.println(
                    " FAIL"
            );

            System.out.println(
                    "============================================="
            );
        }
    }

    // =========================================================
    // Extract string from JSON
    // =========================================================

    private static String extractString(
            String json,
            String field) {

        Pattern pattern =
                Pattern.compile(
                        "\""
                                + field
                                + "\"\\s*:\\s*\"([^\"]+)\""
                );

        Matcher matcher =
                pattern.matcher(json);

        if (matcher.find()) {

            return matcher.group(1);
        }

        return null;
    }

    // =========================================================
    // Extract integer from JSON
    // =========================================================

    private static int extractInt(
            String json,
            String field) {

        Pattern pattern =
                Pattern.compile(
                        "\""
                                + field
                                + "\"\\s*:\\s*(\\d+)"
                );

        Matcher matcher =
                pattern.matcher(json);

        if (matcher.find()) {

            return Integer.parseInt(
                    matcher.group(1)
            );
        }

        return -1;
    }
}