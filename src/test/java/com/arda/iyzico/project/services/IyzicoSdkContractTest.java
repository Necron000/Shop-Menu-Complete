package com.arda.iyzico.project.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arda.iyzico.project.config.IyzicoProperties;
import com.arda.iyzico.project.dto.CheckoutInitResponse;
import com.arda.iyzico.project.dto.CheckoutRequest;
import com.arda.iyzico.project.dto.CheckoutRequest.CheckoutLine;
import com.arda.iyzico.project.models.Item;
import com.arda.iyzico.project.models.OrderItem;
import com.arda.iyzico.project.models.PaymentStatus;
import com.arda.iyzico.project.models.PurchaseOrder;
import com.arda.iyzico.project.repositories.PurchaseOrderRepository;
import com.iyzipay.Options;
import com.sun.net.httpserver.Headers;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Exercises the real iyzipay SDK against a local HTTPS server standing in for the sandbox. */
class IyzicoSdkContractTest {

    private static final String KEYSTORE = "/iyzico-stub-keystore.p12";
    private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();

    private HttpsServer server;
    private SSLSocketFactory previousSocketFactory;
    private HostnameVerifier previousHostnameVerifier;
    private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();
    private final AtomicReference<String> nextResponseBody = new AtomicReference<>("{}");
    private final AtomicReference<Integer> nextResponseStatus = new AtomicReference<>(200);

    private ItemService itemService;
    private PurchaseOrderRepository orderRepository;
    private ApplicationEventPublisher eventPublisher;
    private IyzicoCheckoutService service;

    private record RecordedRequest(String path, Headers headers, String body) {}

    @BeforeEach
    void startStubGateway() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = getClass().getResourceAsStream(KEYSTORE)) {
            keyStore.load(in, KEYSTORE_PASSWORD);
        }

        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, KEYSTORE_PASSWORD);

        // Trusts exactly this one self-signed cert; a trust-all factory would
        // also pass against a misconfigured TLS setup.
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);

        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));

        server.createContext("/", this::handle);
        server.start();

        // iyzipay casts to HttpsURLConnection and offers no factory hook, so the
        // JVM default is swapped for the duration of the test.
        previousSocketFactory    = HttpsURLConnection.getDefaultSSLSocketFactory();
        previousHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) ->
            "localhost".equals(hostname) || "127.0.0.1".equals(hostname));

        itemService     = mock(ItemService.class);
        orderRepository = mock(PurchaseOrderRepository.class);
        eventPublisher  = mock(ApplicationEventPublisher.class);

        when(orderRepository.save(any(PurchaseOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        String baseUrl = "https://localhost:" + server.getAddress().getPort();

        IyzicoProperties properties = new IyzicoProperties(
            "sandbox-test-api-key", "sandbox-test-secret-key", baseUrl,
            "http://localhost:8080/api/checkout/callback",
            "http://localhost:5173/order");

        Options options = new Options();
        options.setApiKey(properties.apiKey());
        options.setSecretKey(properties.secretKey());
        options.setBaseUrl(baseUrl);

        service = new IyzicoCheckoutService(
            itemService, orderRepository, properties, options, eventPublisher);
    }

    @AfterEach
    void stopStubGateway() {
        HttpsURLConnection.setDefaultSSLSocketFactory(previousSocketFactory);
        HttpsURLConnection.setDefaultHostnameVerifier(previousHostnameVerifier);
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Copy the headers — the exchange recycles its own once we respond.
        Headers headers = new Headers();
        headers.putAll(exchange.getRequestHeaders());

        lastRequest.set(new RecordedRequest(
            exchange.getRequestURI().getPath(), headers, body));

        byte[] response = nextResponseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(nextResponseStatus.get(), response.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    @Test
    void sendsARealSignedRequestAndParsesTheHostedForm() {
        stubItem(1L, "Laptop", "22000.00", 5);
        stubItem(2L, "Mouse", "250.50", 5);
        respondWith("""
            {
              "status": "success",
              "locale": "tr",
              "systemTime": 1724800000000,
              "conversationId": "conv-1",
              "token": "sandbox-token-abc",
              "checkoutFormContent": "<script type='text/javascript'>iyziInit()</script>",
              "tokenExpireTime": 1800,
              "paymentPageUrl": "https://sandbox-cpp.iyzipay.com/?token=sandbox-token-abc"
            }
            """);

        CheckoutInitResponse response = service.initialize(
            request(new CheckoutLine(1L, 2), new CheckoutLine(2L, 3)), "88.240.1.1");

        RecordedRequest sent = lastRequest.get();
        assertThat(sent).as("the SDK must actually have made an HTTP call").isNotNull();

        // iyzipay 2.0.61 signs with javax.xml.bind.DatatypeConverter, which only
        // resolves because the pom adds jaxb-api back.
        String authorization = sent.headers().getFirst("Authorization");
        assertThat(authorization).startsWith("IYZWS sandbox-test-api-key:");
        assertThat(sent.headers().getFirst("X-Iyzi-Rnd")).isNotBlank();

        assertThat(sent.path()).contains("checkoutform");
        assertThat(sent.body())
            .contains("\"paidPrice\"")
            .contains("\"basketItems\"")
            .contains("http://localhost:8080/api/checkout/callback")
            .contains("88.240.1.1")
            .contains("10000000146");

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.token()).isEqualTo("sandbox-token-abc");
        assertThat(response.checkoutFormContent()).contains("iyziInit()");
    }

    @Test
    void parksTheOrderAtAwaitingPaymentWhileTheBuyerIsStillOnTheForm() {
        Item laptop = stubItem(1L, "Laptop", "22000.00", 5);
        respondWith("""
            {"status":"success","token":"sandbox-token-abc",
             "checkoutFormContent":"<script></script>","conversationId":"conv-1"}
            """);

        service.initialize(request(new CheckoutLine(1L, 2)), "88.240.1.1");

        PurchaseOrder order = savedOrder();

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        assertThat(order.getPaymentId()).isNull();
        assertThat(laptop.getStock()).isEqualTo(5);
        verify(eventPublisher, never()).publishEvent(any(PaymentSucceededEvent.class));
    }

    @Test
    void cancelsTheOrderWhenTheGatewayRejectsTheCredentials() {
        stubItem(1L, "Laptop", "22000.00", 5);
        respondWith("""
            {"status":"failure","errorCode":"1000",
             "errorMessage":"api bilgileri bulunamadi","locale":"tr"}
            """);

        CheckoutInitResponse response =
            service.initialize(request(new CheckoutLine(1L, 1)), "88.240.1.1");

        assertThat(response.status()).isEqualTo("failure");
        assertThat(response.errorMessage()).isEqualTo("api bilgileri bulunamadi");
        assertThat(savedOrder().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void completesTheOrderWhenTheRetrievedPaymentIsSuccessful() {
        PurchaseOrder order = awaitingOrder();
        respondWith("""
            {
              "status": "success",
              "locale": "tr",
              "conversationId": "conv-1",
              "token": "sandbox-token-abc",
              "paymentStatus": "SUCCESS",
              "paymentId": "21830781",
              "price": 22000.00,
              "paidPrice": 22000.00,
              "currency": "TRY",
              "fraudStatus": 1,
              "mdStatus": 1
            }
            """);

        service.handleCallback("sandbox-token-abc");

        assertThat(lastRequest.get().body()).contains("sandbox-token-abc");
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo("21830781");
        verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));
    }

    @Test
    void failsTheOrderWhenThreeDSecureIsNotCompleted() {
        PurchaseOrder order = awaitingOrder();
        respondWith("""
            {
              "status": "success",
              "paymentStatus": "FAILURE",
              "token": "sandbox-token-abc",
              "errorMessage": "3D Secure dogrulamasi basarisiz",
              "mdStatus": 0
            }
            """);

        service.handleCallback("sandbox-token-abc");

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getErrorMessage()).isEqualTo("3D Secure dogrulamasi basarisiz");
        verify(eventPublisher, never()).publishEvent(any(PaymentSucceededEvent.class));
    }

    private void respondWith(String json) {
        nextResponseBody.set(json);
        nextResponseStatus.set(200);
    }

    private Item stubItem(Long id, String name, String price, int stock) {
        Item item = Item.builder()
            .id(id).name(name).description(name)
            .price(new BigDecimal(price)).currency("TRY").stock(stock).active(true)
            .build();

        when(itemService.getItemForPurchase(id)).thenReturn(item);
        return item;
    }

    private PurchaseOrder savedOrder() {
        org.mockito.ArgumentCaptor<PurchaseOrder> captor =
            org.mockito.ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    private PurchaseOrder awaitingOrder() {
        Item laptop = Item.builder()
            .id(1L).name("Laptop").description("Laptop")
            .price(new BigDecimal("22000.00")).currency("TRY").stock(5).active(true)
            .build();

        PurchaseOrder order = PurchaseOrder.builder()
            .id(42L).buyerEmail("ada@example.com")
            .amount(new BigDecimal("22000.00")).currency("TRY")
            .status(PaymentStatus.PENDING)
            .build();

        order.addItem(OrderItem.of(laptop, 1));
        order.markAwaitingPayment("conv-1", "sandbox-token-abc");

        when(orderRepository.findByPaymentToken("sandbox-token-abc"))
            .thenReturn(Optional.of(order));
        return order;
    }

    private static CheckoutRequest request(CheckoutLine... lines) {
        return new CheckoutRequest(
            List.of(lines),
            "Ada", "Yilmaz", "ada@example.com", "+905350000000", "10000000146",
            "Bagdat Caddesi 1", "Istanbul", "Turkey", "34000");
    }
}
