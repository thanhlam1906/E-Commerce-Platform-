package com.voltstack.ecommerce.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class IdentityServiceApplication {

    public static void main(String[] args) {
        // Postgres 16 rejects "Asia/Saigon" (Windows VN locale JVM default).
        // IntelliJ run configs don't read bootRun jvmArgs, so pin UTC here.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
