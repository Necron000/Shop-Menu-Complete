package com.arda.iyzico.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.arda.iyzico.project.config.RabbitMqConfig;
import com.arda.iyzico.project.dto.AuthResponse;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.arda.iyzico.project.dto.CheckoutInitResponse;
import com.arda.iyzico.project.dto.CheckoutRequest;
import com.arda.iyzico.project.dto.CheckoutRequest.CheckoutLine;
import com.arda.iyzico.project.dto.RegisterRequest;
import com.arda.iyzico.project.models.Item;
import com.arda.iyzico.project.models.PaymentStatus;
import com.arda.iyzico.project.models.PurchaseOrder;
import com.arda.iyzico.project.repositories.ItemRepository;
import com.arda.iyzico.project.repositories.PurchaseOrderRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The whole purchase flow against real Postgres and RabbitMQ, with Iyzico stubbed. Needs Docker. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckoutFlowIntegrationTest {

    private static final HttpsServer STUB;
    private static final AtomicReference<String> INITIALIZE_RESPONSE = new AtomicReference<>();
    private static final AtomicReference<String> RETRIEVE_RESPONSE = new AtomicReference<>();

    static {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            char[] password = "changeit".toCharArray();
            try (InputStream in = CheckoutFlowIntegrationTest.class
                    .getResourceAsStream("/iyzico-stub-keystore.p12")) {
                keyStore.load(in, password);
            }

            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, password);

            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);

            STUB = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            STUB.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            STUB.createContext("/", CheckoutFlowIntegrationTest::handle);
            STUB.start();

            // iyzipay casts to HttpsURLConnection and offers no factory hook.
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) ->
                "localhost".equals(hostname) || "127.0.0.1".equals(hostname));
        }
        catch (Exception e) {
            throw new IllegalStateException("could not start the stub gateway", e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            in.readAllBytes();
        }

        String body = exchange.getRequestURI().getPath().contains("initialize")
            ? INITIALIZE_RESPONSE.get()
            : RETRIEVE_RESPONSE.get();

        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    @DynamicPropertySource
    static void pointIyzicoAtTheStub(DynamicPropertyRegistry registry) {
        registry.add("iyzico.base-url", () -> "https://localhost:" + STUB.getAddress().getPort());
        // Must not start with "your-", or IyzicoConfig refuses to build the bean.
        registry.add("iyzico.api-key", () -> "sandbox-test-api-key");
        registry.add("iyzico.secret-key", () -> "sandbox-test-secret-key");
        registry.add("iyzico.frontend-result-url", () -> "http://localhost:5173/order");
    }

    @Autowired private ItemRepository itemRepository;
    @Autowired private PurchaseOrderRepository orderRepository;
    @Autowired private RabbitTemplate rabbitTemplate;

    @Value("${local.server.port}")
    private int port;

    private RestClient rest;
    private String jwt;

    @BeforeEach
    void registerABuyer() {
        // Redirects must not be followed: the callback's 302 is the assertion.
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
            .build();

        rest = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(httpClient))
            .baseUrl("http://localhost:" + port)
            .build();

        String email = "buyer-" + UUID.randomUUID() + "@example.com";

        AuthResponse auth = rest.post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterRequest(email, "a-good-password"))
            .retrieve()
            .body(AuthResponse.class);

        assertThat(auth).isNotNull();
        jwt = auth.token();
    }

    @Test
    void aPaidCheckoutSettlesTheOrderAndTakesTheStockThroughRabbit() {
        Item laptop = givenAnItemInStock("Laptop", "22000.00", 10);
        String token = "stub-token-" + UUID.randomUUID();

        stubInitializeReturning(token);
        CheckoutInitResponse init = whenTheBuyerStartsCheckout(laptop, 3);

        assertThat(init.status()).isEqualTo("success");
        PurchaseOrder parked = orderRepository.findById(init.orderId()).orElseThrow();
        assertThat(parked.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        assertThat(itemRepository.findById(laptop.getId()).orElseThrow().getStock())
            .as("stock must not move until the payment is real")
            .isEqualTo(10);

        stubRetrieveSucceedingWith("21830781");
        ResponseEntity<Void> callback = whenIyzicoCallsBack(token);

        assertThat(callback.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(callback.getHeaders().getLocation())
            .hasToString("http://localhost:5173/order/" + init.orderId());

        PurchaseOrder settled = orderRepository.findById(init.orderId()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(settled.getPaymentId()).isEqualTo("21830781");

        assertThat(awaitStock(laptop.getId(), 7))
            .as("stock should drop from 10 to 7 once the fulfilment message is consumed")
            .isEqualTo(7);
    }

    @Test
    void aDeclinedCardLeavesTheStockAloneAndRecordsWhy() {
        Item laptop = givenAnItemInStock("Keyboard", "1299.90", 5);
        String token = "stub-token-" + UUID.randomUUID();

        stubInitializeReturning(token);
        CheckoutInitResponse init = whenTheBuyerStartsCheckout(laptop, 2);

        RETRIEVE_RESPONSE.set("""
            {"status":"success","paymentStatus":"FAILURE",
             "errorMessage":"Not sufficient funds"}
            """);

        ResponseEntity<Void> callback = whenIyzicoCallsBack(token);
        assertThat(callback.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        PurchaseOrder failed = orderRepository.findById(init.orderId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("Not sufficient funds");

        assertThat(stockStaysAt(laptop.getId(), 5)).isTrue();
    }

    @Test
    void aReplayedCallbackDoesNotSellTheStockTwice() {
        Item laptop = givenAnItemInStock("Mouse", "250.00", 8);
        String token = "stub-token-" + UUID.randomUUID();

        stubInitializeReturning(token);
        CheckoutInitResponse init = whenTheBuyerStartsCheckout(laptop, 2);

        stubRetrieveSucceedingWith("21830999");
        whenIyzicoCallsBack(token);
        assertThat(awaitStock(laptop.getId(), 6)).isEqualTo(6);

        ResponseEntity<Void> replay = whenIyzicoCallsBack(token);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        assertThat(stockStaysAt(laptop.getId(), 6))
            .as("the isPaid() guard must stop a second fulfilment message")
            .isTrue();
    }

    /** A CORS rule covering this route 403s the callback after the card is charged. */
    @Test
    void theCallbackAcceptsIyzicosCrossOriginPost() {
        Item laptop = givenAnItemInStock("Monitor", "4500.00", 4);
        String token = "stub-token-" + UUID.randomUUID();

        stubInitializeReturning(token);
        CheckoutInitResponse init = whenTheBuyerStartsCheckout(laptop, 1);
        stubRetrieveSucceedingWith("21831234");

        // "null" is what a browser sends after a cross-origin redirect.
        ResponseEntity<Void> callback = rest.post()
            .uri("/api/checkout/callback?token={token}", token)
            .header("Origin", "null")
            .retrieve()
            .toBodilessEntity();

        assertThat(callback.getStatusCode())
            .as("a 403 here means CORS is eating real payment callbacks")
            .isEqualTo(HttpStatus.FOUND);

        assertThat(orderRepository.findById(init.orderId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.PAID);
        assertThat(awaitStock(laptop.getId(), 3)).isEqualTo(3);
    }

    @Test
    void theCallbackAcceptsAPostFromIyzicosOwnDomain() {
        Item laptop = givenAnItemInStock("Webcam", "900.00", 3);
        String token = "stub-token-" + UUID.randomUUID();

        stubInitializeReturning(token);
        CheckoutInitResponse init = whenTheBuyerStartsCheckout(laptop, 1);
        stubRetrieveSucceedingWith("21831235");

        ResponseEntity<Void> callback = rest.post()
            .uri("/api/checkout/callback?token={token}", token)
            .header("Origin", "https://sandbox-api.iyzipay.com")
            .retrieve()
            .toBodilessEntity();

        assertThat(callback.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(orderRepository.findById(init.orderId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.PAID);
    }

    /** Without the DLX, Spring AMQP requeues and the broker redelivers in a hot loop. */
    @Test
    void anUnfulfillableMessageLandsOnTheDeadLetterQueue() {
        Item scarce = givenAnItemInStock("Tablet", "8000.00", 3);
        String token = "stub-token-" + UUID.randomUUID();

        stubInitializeReturning(token);
        CheckoutInitResponse init = whenTheBuyerStartsCheckout(scarce, 3);

        // Sell the stock out from under the order while the buyer is paying.
        // This is the real oversell race: two checkouts, one last unit.
        Item toDrain = itemRepository.findById(scarce.getId()).orElseThrow();
        toDrain.adjustStock(-3);
        itemRepository.save(toDrain);

        stubRetrieveSucceedingWith("21831299");
        whenIyzicoCallsBack(token);

        assertThat(orderRepository.findById(init.orderId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.PAID);

        Message dead = rabbitTemplate.receive(RabbitMqConfig.FULFILMENT_DLQ, 15000);

        assertThat(dead)
            .as("the unfulfillable message should be on " + RabbitMqConfig.FULFILMENT_DLQ)
            .isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8))
            .contains("\"orderId\":" + init.orderId());

        assertThat(itemRepository.findById(scarce.getId()).orElseThrow().getStock()).isZero();
    }

    private Item givenAnItemInStock(String name, String price, int stock) {
        return itemRepository.save(Item.builder()
            .name(name).description(name)
            .price(new BigDecimal(price)).currency("TRY")
            .stock(stock).active(true)
            .build());
    }

    private CheckoutInitResponse whenTheBuyerStartsCheckout(Item item, int quantity) {
        CheckoutRequest request = new CheckoutRequest(
            List.of(new CheckoutLine(item.getId(), quantity)),
            "Ada", "Yilmaz", "ada@example.com", "+905350000000", "10000000146",
            "Bagdat Caddesi 1", "Istanbul", "Turkey", "34000");

        ResponseEntity<CheckoutInitResponse> response = rest.post()
            .uri("/api/checkout/initialize")
            .headers(headers -> headers.setBearerAuth(jwt))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toEntity(CheckoutInitResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Void> whenIyzicoCallsBack(String token) {
        return rest.post()
            .uri("/api/checkout/callback?token={token}", token)
            .retrieve()
            .toBodilessEntity();
    }

    private void stubInitializeReturning(String token) {
        INITIALIZE_RESPONSE.set("""
            {"status":"success","token":"%s",
             "checkoutFormContent":"<script>iyziInit()</script>"}
            """.formatted(token));
    }

    private void stubRetrieveSucceedingWith(String paymentId) {
        RETRIEVE_RESPONSE.set("""
            {"status":"success","paymentStatus":"SUCCESS","paymentId":"%s",
             "price":100.0,"paidPrice":100.0,"currency":"TRY"}
            """.formatted(paymentId));
    }

    /** Polls until the stock reaches {@code expected}, or gives up after 20s. */
    private int awaitStock(Long itemId, int expected) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        int stock = -1;

        while (Instant.now().isBefore(deadline)) {
            stock = itemRepository.findById(itemId).orElseThrow().getStock();
            if (stock == expected) {
                return stock;
            }
            sleep(200);
        }

        return stock;
    }

    private boolean stockStaysAt(Long itemId, int expected) {
        for (int i = 0; i < 10; i++) {
            if (itemRepository.findById(itemId).orElseThrow().getStock() != expected) {
                return false;
            }
            sleep(200);
        }
        return true;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
