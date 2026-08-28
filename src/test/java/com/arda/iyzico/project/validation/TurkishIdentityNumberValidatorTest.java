package com.arda.iyzico.project.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TurkishIdentityNumberValidatorTest {

    private final TurkishIdentityNumberValidator validator = new TurkishIdentityNumberValidator();

    @ParameterizedTest
    @ValueSource(strings = {
        "10000000146",
        "11111111110",
        "12345678950"
    })
    void acceptsNumbersWithAValidChecksum(String identityNumber) {
        assertThat(validator.isValid(identityNumber, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "11111111111",  // the value everyone reaches for — checksum is actually wrong
        "12345678951",  // last digit off by one
        "10000000145",  // 11th digit off by one
        "10000000156"   // 10th digit off by one
    })
    void rejectsNumbersWhoseChecksumDoesNotAddUp(String identityNumber) {
        assertThat(validator.isValid(identityNumber, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "01234567890",  // leading zero is never issued
        "1000000014",   // ten digits
        "100000001466", // twelve digits
        "1000000014a",  // a letter slipped in
        "10000000 46",  // a space slipped in
        "1000000014.",  // punctuation
        ""
    })
    void rejectsMalformedInput(String identityNumber) {
        assertThat(validator.isValid(identityNumber, null)).isFalse();
    }

    @Test
    void treatsNullAsValidSoNotBlankOwnsThatMessage() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
