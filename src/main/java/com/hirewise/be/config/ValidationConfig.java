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
 * <p>
 * By default (without this config), when a constraint declares
 * {@code message = "{some.key}"}, Hibernate Validator looks that key up in
 * its own {@code ValidationMessages.properties} bundle - unrelated to
 * Spring's {@code messages.properties}. That would leave {@code ErrorCode}
 * error messages and validation error messages living in two separate
 * systems, editable in two different places and not sharing the same i18n
 * setup.
 * <p>
 * Declaring a {@link LocalValidatorFactoryBean} here and pointing
 * {@code setValidationMessageSource(messageSource)} at the app's own
 * {@link MessageSource} (already auto-created by Spring Boot from
 * {@code spring.messages.basename}, see {@code application.properties})
 * gives us:
 * <ol>
 *   <li>Validation messages read from the SAME {@code messages.properties}
 *       file as {@code ErrorCode} - one place to edit, no rebuild needed.</li>
 *   <li>Automatic {@code Accept-Language} / {@code LocaleContextHolder}
 *       resolution, matching the mechanism already used for
 *       {@code ErrorCode} (see {@code GlobalExceptionHandler}), ready for
 *       when {@code messages_vi.properties}/{@code messages_en.properties}
 *       etc. are added later.</li>
 *   <li>Constraints that do NOT declare {@code message = "{...}"} (i.e. use
 *       Hibernate Validator's own default message) still work normally -
 *       Hibernate Validator falls back to its default
 *       {@code ValidationMessages} bundle whenever a key isn't found in the
 *       injected {@code messageSource}.</li>
 * </ol>
 * Declaring this bean makes Spring Boot back off from creating its own
 * default {@code Validator}
 * ({@code ValidationAutoConfiguration} is annotated
 * {@code @ConditionalOnMissingBean(Validator.class)}); the
 * {@code MethodValidationPostProcessor} that handles {@code @Validated} on
 * service/controller classes still automatically picks up this bean.
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
