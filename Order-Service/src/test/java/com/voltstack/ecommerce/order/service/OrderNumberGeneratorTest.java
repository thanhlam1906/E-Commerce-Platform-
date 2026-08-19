package com.voltstack.ecommerce.order.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderNumberGeneratorTest {

    private static final Pattern FORMAT = Pattern.compile("OR-\\d{8}-\\d{5}");

    private final OrderNumberGenerator generator = new OrderNumberGenerator();

    @Test
    void generate_returnsExpectedFormat() {
        String number = generator.generate(exists -> false);
        assertTrue(FORMAT.matcher(number).matches(), "Unexpected order number: " + number);
    }

    @Test
    void generate_retriesUntilUniqueNumberFound() {
        AtomicInteger calls = new AtomicInteger();
        String number = generator.generate(s -> calls.incrementAndGet() == 1); // first candidate taken
        assertTrue(FORMAT.matcher(number).matches());
        assertTrue(calls.get() >= 2, "expected at least one uniqueness retry");
    }

    @Test
    void generate_throwsAfterFiveAttempts() {
        assertThrows(IllegalStateException.class, () -> generator.generate(exists -> true));
    }
}
