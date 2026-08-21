package com.hirewise.be.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Wires Bean Validation (on request DTOs: {@code @NotBlank}, {@code @Size},
 * {@code @Email}, {@code @NotNull}, ...) to read its messages from the SAME
 * source as {@code ErrorCode} (see {@code exception/ErrorCode.java},
 * {@code resources/messages.properties}), instead of Hibernate Validator's
 * separate default bundle.
 */
@Configuration
public class ValidationConfig {

    /**
     * Builds the app-wide {@link jakarta.validation.Validator} bean, backed
     * by the shared {@link MessageSource} so validation error messages stay
     * in sync with {@code ErrorCode} messages (see class Javadoc).
     *
     * @param messageSource the app's shared i18n message source, auto-wired
     *                       by Spring Boot
     * @return a {@link LocalValidatorFactoryBean} configured to resolve
     *         constraint messages through {@code messageSource}
     */
    @Bean
    public LocalValidatorFactoryBean getValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.setValidationMessageSource(messageSource);
        return validatorFactoryBean;
    }
}
