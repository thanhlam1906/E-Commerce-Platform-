package com.voltstack.ecommerce.order.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;

@Component
public class OrderNumberGenerator {

    private final SecureRandom random = new SecureRandom();

    /**
     * OR-{yyyyMMdd}-{5 random digits}. Pre-checks uniqueness instead of relying on a
     * constraint-violation retry, which would poison the surrounding transaction.
     */
    public String generate(Predicate<String> exists) {
        for (int i = 0; i < 5; i++) {
            String number = String.format("OR-%s-%05d",
                    LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), random.nextInt(100000));
            if (!exists.test(number)) {
                return number;
            }
        }
        throw new IllegalStateException("Không thể sinh mã đơn hàng duy nhất");
    }
}
