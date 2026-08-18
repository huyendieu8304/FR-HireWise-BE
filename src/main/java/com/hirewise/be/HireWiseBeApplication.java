package com.hirewise.be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point that bootstraps the HireWise backend Spring Boot
 * application.
 */
@SpringBootApplication
public class HireWiseBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HireWiseBeApplication.class, args);
    }

}
