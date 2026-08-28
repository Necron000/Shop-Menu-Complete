package com.arda.iyzico.project.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TurkishIdentityNumberValidator
        implements ConstraintValidator<TurkishIdentityNumber, String> {

    private static final int LENGTH = 11;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.length() != LENGTH) {
            return false;
        }

        int[] digits = new int[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            digits[i] = c - '0';
        }

        if (digits[0] == 0) {
            return false;
        }

        int oddSum  = digits[0] + digits[2] + digits[4] + digits[6] + digits[8];
        int evenSum = digits[1] + digits[3] + digits[5] + digits[7];

        // floorMod, not %: (oddSum * 7 - evenSum) is negative for some inputs
        // and Java's % would then yield a negative remainder.
        if (Math.floorMod(oddSum * 7 - evenSum, 10) != digits[9]) {
            return false;
        }

        int firstTenSum = 0;
        for (int i = 0; i < LENGTH - 1; i++) {
            firstTenSum += digits[i];
        }

        return firstTenSum % 10 == digits[10];
    }
}
