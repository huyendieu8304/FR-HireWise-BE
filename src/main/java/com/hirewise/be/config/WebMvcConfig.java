package com.hirewise.be.config;

import com.hirewise.be.security.CurrentUserResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Wires custom Spring MVC extensions into the application context.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserResolver currentUserResolver;

    public WebMvcConfig(CurrentUserResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * Registers {@link CurrentUserResolver} so controller methods can
     * declare a {@code @CurrentUserPrincipal CurrentUser} parameter and
     * have it resolved automatically from the authenticated JWT, instead
     * of every controller manually casting {@code Authentication} to
     * {@code Jwt} and reading claims by hand.
     *
     * @param resolvers the resolver list Spring MVC uses to bind
     *                   controller method arguments
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
