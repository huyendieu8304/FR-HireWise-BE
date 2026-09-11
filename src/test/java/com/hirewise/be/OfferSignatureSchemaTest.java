package com.hirewise.be;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-39: guards the column type behind {@code offer_signatures.ip_address}.
 * <p>
 * Declared {@code INET} originally, it rejected every insert with "column is
 * of type inet but expression is of type character varying" - Hibernate binds
 * a {@link String} and PostgreSQL will not implicitly cast. Because
 * {@code OfferSigningServiceTest} mocks the repository, no unit test touches
 * a real column, so only a schema assertion can catch a regression here.
 * <p>
 * Reuses the Testcontainers setup of {@link HireWiseBeApplicationTests}, so
 * Spring serves it the already-cached context rather than a second database.
 */
@SpringBootTest
@Import(HireWiseBeApplicationTests.TestcontainersConfig.class)
class OfferSignatureSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ipAddressIsTextSoAStringBindSucceeds() {
        String dataType = columnType("offer_signatures", "ip_address");

        assertThat(dataType).isEqualTo("character varying");
    }

    @Test
    void ipAddressHoldsTheLongestPossibleLiteral() {
        Integer maxLength = jdbcTemplate.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_name = 'offer_signatures' AND column_name = 'ip_address'
                """, Integer.class);

        // 45 = IPv4-mapped IPv6, the longest textual form.
        assertThat(maxLength).isGreaterThanOrEqualTo(45);
    }

    @Test
    void ipAddressIsNullableSoAMissingIpCannotBlockASignature() {
        String nullable = jdbcTemplate.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'offer_signatures' AND column_name = 'ip_address'
                """, String.class);

        assertThat(nullable).isEqualTo("YES");
    }

    private String columnType(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                """, String.class, table, column);
    }
}
