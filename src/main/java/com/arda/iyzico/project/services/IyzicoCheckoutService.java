package com.arda.iyzico.project.services;

import com.arda.iyzico.project.config.IyzicoProperties;
import com.arda.iyzico.project.dto.CheckoutInitResponse;
import com.arda.iyzico.project.dto.CheckoutRequest;
import com.arda.iyzico.project.models.Item;
import com.arda.iyzico.project.models.OrderItem;
import com.arda.iyzico.project.models.PaymentStatus;
import com.arda.iyzico.project.models.PurchaseOrder;
import com.arda.iyzico.project.repositories.PurchaseOrderRepository;
import com.iyzipay.Options;
import com.iyzipay.model.*;
import com.iyzipay.request.CreateCheckoutFormInitializeRequest;
import com.iyzipay.request.RetrieveCheckoutFormRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IyzicoCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(IyzicoCheckoutService.class);

    private final ItemService itemService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final IyzicoProperties properties;
    private final Options iyzipayOptions;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CheckoutInitResponse initialize(CheckoutRequest request, String buyerIp) {
        Basket basket = buildBasket(request.items());
        BigDecimal amount = basket.amount();

        String conversationId = UUID.randomUUID().toString();

        PurchaseOrder order = PurchaseOrder.builder()
                .buyerEmail(request.email())
                .amount(amount)
                .currency(basket.currency())
                .status(PaymentStatus.PENDING)
                .conversationId(conversationId)
                .buyerName(request.name())
                .buyerSurname(request.surname())
                .buyerPhone(request.phone())
                .buyerIdentityNo(request.identityNumber())
                .address(request.address())
                .city(request.city())
                .country(request.country())
                .zipCode(request.zipCode())
                .build();

        basket.lines().forEach(order::addItem);

        PurchaseOrder saved = purchaseOrderRepository.save(order);

        CheckoutFormInitialize formInitialize = callIyzico(request, basket, amount,
                conversationId, saved.getId(), buyerIp);

        if (!"success".equals(formInitialize.getStatus())) {
            String error = formInitialize.getErrorMessage() != null
                    ? formInitialize.getErrorMessage()
                    : "Iyzico rejected the checkout initialization";
            saved.markCancelled(error);
            log.warn("Checkout init failed for order {}: {}", saved.getId(), error);
            return new CheckoutInitResponse(saved.getId(), null, null, "failure", error);
        }

        saved.markAwaitingPayment(conversationId, formInitialize.getToken());

        log.info("Checkout initialized for order {} with token {}",
                saved.getId(), formInitialize.getToken());

        return new CheckoutInitResponse(
                saved.getId(),
                formInitialize.getCheckoutFormContent(),
                formInitialize.getToken(),
                "success",
                null);
    }

    private record Basket(List<OrderItem> lines, BigDecimal amount, String currency) {}

    /** Checks stock but never decrements it — stock comes off in OrderFulfilmentService. */
    private Basket buildBasket(List<CheckoutRequest.CheckoutLine> requestedLines) {
        Map<Long, Integer> quantityByItemId = new LinkedHashMap<>();
        for (CheckoutRequest.CheckoutLine line : requestedLines) {
            quantityByItemId.merge(line.itemId(), line.quantity(), Integer::sum);
        }

        List<OrderItem> lines = new ArrayList<>();
        BigDecimal amount = BigDecimal.ZERO;
        String currency = null;

        for (Map.Entry<Long, Integer> entry : quantityByItemId.entrySet()) {
            Item item = itemService.getItemForPurchase(entry.getKey());
            int quantity = entry.getValue();

            if (item.getStock() < quantity) {
                throw new IllegalStateException("Not enough stock for " + item.getName());
            }

            if (currency == null) {
                currency = item.getCurrency();
            } else if (!currency.equals(item.getCurrency())) {
                throw new IllegalStateException(
                        "Cart mixes currencies: " + currency + " and " + item.getCurrency());
            }

            OrderItem line = OrderItem.of(item, quantity);

            if (line.getLineTotal().signum() <= 0) {
                throw new IllegalStateException(item.getName() + " is not purchasable");
            }

            lines.add(line);
            amount = amount.add(line.getLineTotal());
        }

        return new Basket(lines, amount, currency);
    }

    private CheckoutFormInitialize callIyzico(CheckoutRequest request, Basket basket,
                                              BigDecimal amount, String conversationId,
                                              Long orderId, String buyerIp) {

        CreateCheckoutFormInitializeRequest iyzicoRequest =
                new CreateCheckoutFormInitializeRequest();

        iyzicoRequest.setLocale(Locale.TR.getValue());
        iyzicoRequest.setConversationId(conversationId);
        iyzicoRequest.setPrice(amount);
        iyzicoRequest.setPaidPrice(amount);
        iyzicoRequest.setCurrency(Currency.TRY.name());
        iyzicoRequest.setBasketId("ORDER-" + orderId);
        iyzicoRequest.setPaymentGroup(PaymentGroup.PRODUCT.name());
        iyzicoRequest.setCallbackUrl(properties.callbackUrl());

        Buyer buyer = new Buyer();
        buyer.setId("BUYER-" + request.email().hashCode());
        buyer.setName(request.name());
        buyer.setSurname(request.surname());
        buyer.setGsmNumber(request.phone());
        buyer.setEmail(request.email());
        buyer.setIdentityNumber(request.identityNumber());
        buyer.setRegistrationAddress(request.address());
        buyer.setCity(request.city());
        buyer.setCountry(request.country());
        buyer.setZipCode(request.zipCode());
        buyer.setIp(buyerIp != null && !buyerIp.isBlank() ? buyerIp : "127.0.0.1");
        iyzicoRequest.setBuyer(buyer);

        Address shippingAddress = new Address();
        shippingAddress.setContactName(request.name() + " " + request.surname());
        shippingAddress.setCity(request.city());
        shippingAddress.setCountry(request.country());
        shippingAddress.setAddress(request.address());
        shippingAddress.setZipCode(request.zipCode());
        iyzicoRequest.setShippingAddress(shippingAddress);
        iyzicoRequest.setBillingAddress(shippingAddress);

        // Basket-item prices must sum exactly to the paid price.
        List<BasketItem> basketItems = new ArrayList<>();
        for (OrderItem line : basket.lines()) {
            BasketItem basketItem = new BasketItem();
            basketItem.setId("ITEM-" + line.getItemId());
            basketItem.setName(line.getItemName() + " x" + line.getQuantity());
            basketItem.setCategory1("General");
            basketItem.setItemType(BasketItemType.PHYSICAL.name());
            basketItem.setPrice(line.getLineTotal());
            basketItems.add(basketItem);
        }
        iyzicoRequest.setBasketItems(basketItems);

        return CheckoutFormInitialize.create(iyzicoRequest, iyzipayOptions);
    }

    @Transactional
    public PurchaseOrder handleCallback(String token) {
        RetrieveCheckoutFormRequest request = new RetrieveCheckoutFormRequest();
        request.setLocale(Locale.TR.getValue());
        request.setToken(token);

        CheckoutForm result = CheckoutForm.retrieve(request, iyzipayOptions);

        PurchaseOrder order = purchaseOrderRepository.findByPaymentToken(token)
                .orElseThrow(() -> new IllegalStateException(
                        "No order found for payment token: " + token));

        if (order.isPaid()) {
            log.info("Order {} already marked paid — duplicate callback ignored", order.getId());
            return order;
        }

        boolean success = "success".equals(result.getStatus())
                && "SUCCESS".equals(result.getPaymentStatus());

        if (success) {
            order.markPaid(result.getPaymentId());
            log.info("Order {} paid, iyzico paymentId {}", order.getId(), result.getPaymentId());
            eventPublisher.publishEvent(
                    new PaymentSucceededEvent(order, List.copyOf(order.getItems())));
        } else {
            String error = result.getErrorMessage() != null
                    ? result.getErrorMessage()
                    : "Payment was not completed";
            order.markFailed(error);
            log.warn("Order {} payment failed: {}", order.getId(), error);
        }

        return order;
    }
}