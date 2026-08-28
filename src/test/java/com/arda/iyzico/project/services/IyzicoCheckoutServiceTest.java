package com.arda.iyzico.project.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.iyzipay.model.BasketItem;
import com.iyzipay.model.CheckoutForm;
import com.iyzipay.model.CheckoutFormInitialize;
import com.iyzipay.request.CreateCheckoutFormInitializeRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class IyzicoCheckoutServiceTest {

    private ItemService itemService;
    private PurchaseOrderRepository orderRepository;
    private ApplicationEventPublisher eventPublisher;
    private IyzicoCheckoutService service;

    private MockedStatic<CheckoutFormInitialize> formInitialize;
    private MockedStatic<CheckoutForm> checkoutForm;

    @BeforeEach
    void setUp() {
        itemService     = mock(ItemService.class);
        orderRepository = mock(PurchaseOrderRepository.class);
        eventPublisher  = mock(ApplicationEventPublisher.class);

        IyzicoProperties properties = new IyzicoProperties(
            "key", "secret",
            "https://sandbox-api.iyzipay.com",
            "http://localhost:8080/api/checkout/callback",
            "http://localhost:5173/order");

        service = new IyzicoCheckoutService(
            itemService, orderRepository, properties, new Options(), eventPublisher);

        when(orderRepository.save(any(PurchaseOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        formInitialize = Mockito.mockStatic(CheckoutFormInitialize.class);
        checkoutForm   = Mockito.mockStatic(CheckoutForm.class);
    }

    @AfterEach
    void tearDown() {
        formInitialize.close();
        checkoutForm.close();
    }

    @Test
    void rejectsACartThatMixesCurrencies() {
        stubItem(1L, "Laptop", "22000.00", "TRY", 5);
        stubItem(2L, "Mouse", "30.00", "USD", 5);

        assertThatThrownBy(() -> service.initialize(
                request(new CheckoutLine(1L, 1), new CheckoutLine(2L, 1)), "1.2.3.4"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("mixes currencies");

        formInitialize.verifyNoInteractions();
    }

    @Test
    void rejectsALineThatExceedsStockBeforeCallingIyzico() {
        stubItem(1L, "Laptop", "22000.00", "TRY", 1);

        assertThatThrownBy(() -> service.initialize(request(new CheckoutLine(1L, 2)), "1.2.3.4"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Not enough stock");

        formInitialize.verifyNoInteractions();
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownOrInactiveItem() {
        when(itemService.getItemForPurchase(99L))
            .thenThrow(new IllegalArgumentException("Item not found or inactive."));

        assertThatThrownBy(() -> service.initialize(request(new CheckoutLine(99L, 1)), "1.2.3.4"))
            .isInstanceOf(IllegalArgumentException.class);

        formInitialize.verifyNoInteractions();
    }

    @Test
    void mergesDuplicateLinesForTheSameItem() {
        Item laptop = stubItem(1L, "Laptop", "100.00", "TRY", 10);
        stubSuccessfulFormInitialize();

        service.initialize(
            request(new CheckoutLine(1L, 2), new CheckoutLine(1L, 3)), "1.2.3.4");

        CreateCheckoutFormInitializeRequest sent = captureIyzicoRequest();

        assertThat(sent.getBasketItems()).hasSize(1);
        assertThat(sent.getPrice()).isEqualByComparingTo("500.00");
        assertThat(laptop.getStock()).isEqualTo(10);
    }

    @Test
    void pricesEachLineAtItsLineTotalSoTheBasketSumsToThePaidPrice() {
        stubItem(1L, "Laptop", "22000.00", "TRY", 5);
        stubItem(2L, "Mouse", "250.50", "TRY", 5);
        stubSuccessfulFormInitialize();

        service.initialize(
            request(new CheckoutLine(1L, 2), new CheckoutLine(2L, 3)), "1.2.3.4");

        CreateCheckoutFormInitializeRequest sent = captureIyzicoRequest();

        BigDecimal basketSum = sent.getBasketItems().stream()
            .map(BasketItem::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(basketSum).isEqualByComparingTo(sent.getPaidPrice());
        assertThat(sent.getPrice()).isEqualByComparingTo("44751.50");
    }

    @Test
    void doesNotDecrementStockWhenTheFormIsOnlyInitialized() {
        Item laptop = stubItem(1L, "Laptop", "22000.00", "TRY", 5);
        stubSuccessfulFormInitialize();

        service.initialize(request(new CheckoutLine(1L, 3)), "1.2.3.4");

        // Stock comes off in OrderFulfilmentService, once the money is real.
        assertThat(laptop.getStock()).isEqualTo(5);
    }

    @Test
    void persistsTheOrderAsAwaitingPaymentWithTheReturnedToken() {
        stubItem(1L, "Laptop", "22000.00", "TRY", 5);
        stubSuccessfulFormInitialize();

        CheckoutInitResponse response =
            service.initialize(request(new CheckoutLine(1L, 1)), "1.2.3.4");

        PurchaseOrder saved = captureSavedOrder();

        assertThat(response.status()).isEqualTo("success");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        assertThat(saved.getPaymentToken()).isEqualTo("token-123");
        assertThat(saved.getConversationId()).isNotBlank();
        assertThat(saved.getItems()).hasSize(1);
    }

    @Test
    void fallsBackToLoopbackWhenTheBuyerIpIsUnknown() {
        stubItem(1L, "Laptop", "22000.00", "TRY", 5);
        stubSuccessfulFormInitialize();

        service.initialize(request(new CheckoutLine(1L, 1)), "   ");

        assertThat(captureIyzicoRequest().getBuyer().getIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void marksTheOrderCancelledWhenIyzicoRejectsTheInitialization() {
        Item laptop = stubItem(1L, "Laptop", "22000.00", "TRY", 5);

        CheckoutFormInitialize failure = mock(CheckoutFormInitialize.class);
        when(failure.getStatus()).thenReturn("failure");
        when(failure.getErrorMessage()).thenReturn("Invalid merchant credentials");
        formInitialize.when(() -> CheckoutFormInitialize.create(any(), any()))
            .thenReturn(failure);

        CheckoutInitResponse response =
            service.initialize(request(new CheckoutLine(1L, 1)), "1.2.3.4");

        assertThat(response.status()).isEqualTo("failure");
        assertThat(response.errorMessage()).isEqualTo("Invalid merchant credentials");
        assertThat(response.checkoutFormContent()).isNull();
        assertThat(captureSavedOrder().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(laptop.getStock()).isEqualTo(5);
        verify(eventPublisher, never()).publishEvent(any(PaymentSucceededEvent.class));
    }

    @Test
    void marksTheOrderPaidAndPublishesTheFulfilmentEvent() {
        PurchaseOrder order = awaitingOrder();
        stubRetrieve("success", "SUCCESS", "pay-77", null);

        service.handleCallback("token-123");

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo("pay-77");

        ArgumentCaptor<PaymentSucceededEvent> event =
            ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(eventPublisher).publishEvent(event.capture());

        assertThat(event.getValue().items()).hasSize(1);
    }

    @Test
    void marksTheOrderFailedWhenTheCardIsDeclined() {
        PurchaseOrder order = awaitingOrder();
        stubRetrieve("success", "FAILURE", null, "Not sufficient funds");

        service.handleCallback("token-123");

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getErrorMessage()).isEqualTo("Not sufficient funds");
        assertThat(order.getPaymentId()).isNull();

        verify(eventPublisher, never()).publishEvent(any(PaymentSucceededEvent.class));
    }

    @Test
    void marksTheOrderFailedWhenIyzicoItselfErrors() {
        PurchaseOrder order = awaitingOrder();
        stubRetrieve("failure", null, null, "Invalid token");

        service.handleCallback("token-123");

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getErrorMessage()).isEqualTo("Invalid token");
    }

    @Test
    void recordsAGenericMessageWhenIyzicoGivesNoReason() {
        PurchaseOrder order = awaitingOrder();
        stubRetrieve("success", "FAILURE", null, null);

        service.handleCallback("token-123");

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getErrorMessage()).isEqualTo("Payment was not completed");
    }

    @Test
    void ignoresADuplicateCallbackForAnAlreadyPaidOrder() {
        PurchaseOrder order = awaitingOrder();
        order.markPaid("pay-77");
        stubRetrieve("success", "SUCCESS", "pay-77", null);

        service.handleCallback("token-123");

        verify(eventPublisher, never()).publishEvent(any(PaymentSucceededEvent.class));
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void rejectsACallbackForATokenWeNeverIssued() {
        stubRetrieve("success", "SUCCESS", "pay-77", null);
        when(orderRepository.findByPaymentToken("forged")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleCallback("forged"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No order found");

        verify(eventPublisher, never()).publishEvent(any(PaymentSucceededEvent.class));
    }

    private Item stubItem(Long id, String name, String price, String currency, int stock) {
        Item item = Item.builder()
            .id(id)
            .name(name)
            .description(name)
            .price(new BigDecimal(price))
            .currency(currency)
            .stock(stock)
            .active(true)
            .build();

        when(itemService.getItemForPurchase(id)).thenReturn(item);
        return item;
    }

    private void stubSuccessfulFormInitialize() {
        CheckoutFormInitialize success = mock(CheckoutFormInitialize.class);
        when(success.getStatus()).thenReturn("success");
        when(success.getToken()).thenReturn("token-123");
        when(success.getCheckoutFormContent()).thenReturn("<script>/* iyzico */</script>");

        formInitialize.when(() -> CheckoutFormInitialize.create(any(), any()))
            .thenReturn(success);
    }

    private void stubRetrieve(String status, String paymentStatus,
                              String paymentId, String errorMessage) {
        CheckoutForm result = mock(CheckoutForm.class);
        when(result.getStatus()).thenReturn(status);
        when(result.getPaymentStatus()).thenReturn(paymentStatus);
        when(result.getPaymentId()).thenReturn(paymentId);
        when(result.getErrorMessage()).thenReturn(errorMessage);

        checkoutForm.when(() -> CheckoutForm.retrieve(any(), any())).thenReturn(result);
    }

    private PurchaseOrder awaitingOrder() {
        Item laptop = Item.builder()
            .id(1L).name("Laptop").description("Laptop")
            .price(new BigDecimal("22000.00")).currency("TRY").stock(5).active(true)
            .build();

        PurchaseOrder order = PurchaseOrder.builder()
            .id(42L)
            .buyerEmail("ada@example.com")
            .amount(new BigDecimal("22000.00"))
            .currency("TRY")
            .status(PaymentStatus.PENDING)
            .build();

        order.addItem(OrderItem.of(laptop, 1));
        order.markAwaitingPayment("conv-1", "token-123");

        when(orderRepository.findByPaymentToken("token-123")).thenReturn(Optional.of(order));
        return order;
    }

    private CreateCheckoutFormInitializeRequest captureIyzicoRequest() {
        ArgumentCaptor<CreateCheckoutFormInitializeRequest> captor =
            ArgumentCaptor.forClass(CreateCheckoutFormInitializeRequest.class);

        formInitialize.verify(() -> CheckoutFormInitialize.create(captor.capture(), any()));
        return captor.getValue();
    }

    private PurchaseOrder captureSavedOrder() {
        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    private static CheckoutRequest request(CheckoutLine... lines) {
        return new CheckoutRequest(
            List.of(lines),
            "Ada", "Yilmaz",
            "ada@example.com",
            "+905350000000",
            "10000000146",
            "Bagdat Caddesi 1", "Istanbul", "Turkey", "34000");
    }
}
