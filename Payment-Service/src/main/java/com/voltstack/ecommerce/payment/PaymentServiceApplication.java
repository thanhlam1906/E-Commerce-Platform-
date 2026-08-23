package com.voltstack.ecommerce.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        // Postgres 16 rejects "Asia/Saigon" (Windows VN locale JVM default).
        // IntelliJ run configs don't read bootRun jvmArgs, so pin UTC here.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
