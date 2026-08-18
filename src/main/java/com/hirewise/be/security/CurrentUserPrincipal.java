package com.hirewise.be.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller parameter that should be resolved into a
 * {@link CurrentUser} from the current request's JWT. See
 * {@link CurrentUserResolver}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserPrincipal {
}
