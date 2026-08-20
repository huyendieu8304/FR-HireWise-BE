package com.hirewise.be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Import(HireWiseBeApplicationTests.TestcontainersConfig.class)
class HireWiseBeApplicationTests {

    @Test
    void contextLoads() {
    }

    // Tu bat mot Postgres tam qua Testcontainers cho lan chay test nay.
    // @ServiceConnection khien Spring bo qua spring.datasource.url=${DB_URL} va tro
    // thang datasource/Flyway vao container - khong can DB_URL/DB_USERNAME/DB_PASSWORD
    // hay docker-compose Postgres chay san. Yeu cau Docker dang chay o noi thuc thi
    // (may local hoac CI runner).
    @TestConfiguration(proxyBeanMethods = false)
    static class TestcontainersConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>("postgres:16");
        }
    }
}
