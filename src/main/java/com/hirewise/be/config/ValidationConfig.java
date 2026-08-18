package com.hirewise.be.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Cho phep Bean Validation tren DTO request (@NotBlank, @Size, @Email,
 * @NotNull...) dung CHUNG 1 nguon message voi ErrorCode
 * (xem exception/ErrorCode.java, resources/messages.properties).
 *
 * Mac dinh (khong co config nay), khi annotation khai bao
 * message = "{some.key}", Hibernate Validator se tim key do trong bundle
 * rieng ValidationMessages.properties (khong lien quan messages.properties
 * cua Spring) - nghia la message loi ErrorCode va message loi validation
 * song o 2 he thong tach biet, khong the sua chung 1 cho, khong i18n dong
 * bo voi nhau.
 *
 * Khai bao LocalValidatorFactoryBean rieng va goi
 * setValidationMessageSource(messageSource) de tro thang ve MessageSource
 * cua app (bean nay Spring Boot da tu tao san tu spring.messages.basename,
 * xem application.properties) - nho vay:
 *   1. Message validation doc tu CHUNG 1 file messages.properties voi
 *      ErrorCode -> sua o 1 noi, khong build lai code.
 *   2. Tu dong theo Accept-Language / LocaleContextHolder giong het co che
 *      dang dung cho ErrorCode (xem GlobalExceptionHandler), neu sau nay bo
 *      sung messages_vi.properties, messages_en.properties...
 *   3. Constraint nao KHONG khai bao message = "{...}" (dung message mac
 *      dinh cua Hibernate Validator) van hoat dong binh thuong - Hibernate
 *      Validator tu fallback ve bundle mac dinh (ValidationMessages) khi
 *      khong tim thay key trong messageSource duoc inject.
 *
 * Khai bao bean nay se khien Spring Boot tu lui (backs off) khong tu tao
 * Validator mac dinh nua (ValidationAutoConfiguration co
 * @ConditionalOnMissingBean(Validator.class)), MethodValidationPostProcessor
 * (xu ly @Validated tren service/controller) van tu dong dung lai bean nay.
 */
@Configuration
public class ValidationConfig {

    @Bean
    public LocalValidatorFactoryBean getValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.setValidationMessageSource(messageSource);
        return validatorFactoryBean;
    }
}
