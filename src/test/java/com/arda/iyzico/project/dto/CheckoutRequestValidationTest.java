package com.arda.iyzico.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.arda.iyzico.project.dto.CheckoutRequest.CheckoutLine;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CheckoutRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void acceptsAFullyValidRequest() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "11111111111",   // wrong checksum, though it is 11 digits
        "1234567890",    // too short
        "123456789012",  // too long
        "abcdefghijk",   // not digits at all
        "1234567890a",   // one letter at the end
        "01234567890"    // leading zero
    })
    void rejectsAMistypedIdentityNumber(String identityNumber) {
        assertThat(violationPaths(r -> withIdentityNumber(r, identityNumber)))
            .contains("identityNumber");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingIdentityNumber(String identityNumber) {
        assertThat(violationPaths(r -> withIdentityNumber(r, identityNumber)))
            .contains("identityNumber");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "abc",
        "05x50000000",
        "+90",              // far too short
        "12345",            // too short
        "(0535) 000-0000",  // Iyzico wants it starting with a digit or +
        "+9053500000001234567890"  // implausibly long
    })
    void rejectsAMistypedPhoneNumber(String phone) {
        assertThat(violationPaths(r -> withPhone(r, phone))).contains("phone");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "+905350000000",
        "05350000000",
        "0535 000 00 00",
        "0535-000-0000"
    })
    void acceptsThePhoneFormatsBuyersActuallyType(String phone) {
        assertThat(violationPaths(r -> withPhone(r, phone))).doesNotContain("phone");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-an-email",
        "@no-local-part.com",
        "spaces in@example.com"
    })
    void rejectsAMistypedEmail(String email) {
        assertThat(violationPaths(r -> withEmail(r, email))).contains("email");
    }

    @Test
    void rejectsAnEmptyCart() {
        assertThat(violationPaths(r -> withItems(r, List.of()))).contains("items");
    }

    @Test
    void rejectsANullCart() {
        assertThat(violationPaths(r -> withItems(r, null))).contains("items");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void rejectsANonPositiveQuantity(int quantity) {
        Set<String> paths = violationPaths(
            r -> withItems(r, List.of(new CheckoutLine(1L, quantity))));

        assertThat(paths).contains("items[0].quantity");
    }

    @Test
    void rejectsALineWithNoItemId() {
        assertThat(violationPaths(r -> withItems(r, List.of(new CheckoutLine(null, 1)))))
            .contains("items[0].itemId");
    }

    @Test
    void rejectsALineWithNoQuantity() {
        assertThat(violationPaths(r -> withItems(r, List.of(new CheckoutLine(1L, null)))))
            .contains("items[0].quantity");
    }

    @Test
    void reportsEveryBadLineNotJustTheFirst() {
        Set<String> paths = violationPaths(r -> withItems(r, List.of(
            new CheckoutLine(1L, 0),
            new CheckoutLine(null, 2))));

        assertThat(paths).contains("items[0].quantity", "items[1].itemId");
    }

    @Test
    void rejectsBlankDeliveryDetails() {
        CheckoutRequest request = new CheckoutRequest(
            List.of(new CheckoutLine(1L, 1)),
            "  ", "  ", "buyer@example.com", "+905350000000", "10000000146",
            "  ", "  ", "  ", "  ");

        assertThat(pathsOf(validator.validate(request)))
            .contains("name", "surname", "address", "city", "country", "zipCode");
    }

    @Test
    void rejectsOverlongDeliveryDetails() {
        String tooLong = "x".repeat(101);

        CheckoutRequest request = new CheckoutRequest(
            List.of(new CheckoutLine(1L, 1)),
            tooLong, tooLong, "buyer@example.com", "+905350000000", "10000000146",
            "Bagdat Caddesi 1", tooLong, tooLong, "x".repeat(21));

        assertThat(pathsOf(validator.validate(request)))
            .contains("name", "surname", "city", "country", "zipCode");
    }

    @Test
    void reportsAllProblemsAtOnceSoTheFormCanHighlightEveryField() {
        CheckoutRequest request = new CheckoutRequest(
            List.of(),
            "", "", "nope", "abc", "11111111111",
            "", "", "", "");

        assertThat(pathsOf(validator.validate(request)))
            .contains("items", "name", "surname", "email", "phone",
                      "identityNumber", "address", "city", "country", "zipCode");
    }

    private Set<String> violationPaths(UnaryOperator<CheckoutRequest> mutation) {
        return pathsOf(validator.validate(mutation.apply(valid())));
    }

    private static Set<String> pathsOf(Set<ConstraintViolation<CheckoutRequest>> violations) {
        return violations.stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }

    private static CheckoutRequest valid() {
        return new CheckoutRequest(
            List.of(new CheckoutLine(1L, 2)),
            "Ada", "Yilmaz",
            "ada@example.com",
            "+905350000000",
            "10000000146",
            "Bagdat Caddesi 1", "Istanbul", "Turkey", "34000");
    }

    private static CheckoutRequest withIdentityNumber(CheckoutRequest r, String identityNumber) {
        return new CheckoutRequest(r.items(), r.name(), r.surname(), r.email(), r.phone(),
            identityNumber, r.address(), r.city(), r.country(), r.zipCode());
    }

    private static CheckoutRequest withPhone(CheckoutRequest r, String phone) {
        return new CheckoutRequest(r.items(), r.name(), r.surname(), r.email(), phone,
            r.identityNumber(), r.address(), r.city(), r.country(), r.zipCode());
    }

    private static CheckoutRequest withEmail(CheckoutRequest r, String email) {
        return new CheckoutRequest(r.items(), r.name(), r.surname(), email, r.phone(),
            r.identityNumber(), r.address(), r.city(), r.country(), r.zipCode());
    }

    private static CheckoutRequest withItems(CheckoutRequest r, List<CheckoutLine> items) {
        return new CheckoutRequest(items, r.name(), r.surname(), r.email(), r.phone(),
            r.identityNumber(), r.address(), r.city(), r.country(), r.zipCode());
    }
}
