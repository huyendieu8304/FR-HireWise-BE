package com.hirewise.be.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu tham số controller cần được resolve thành {@link CurrentUser}
 * từ JWT của request hiện tại. Xem {@link CurrentUserResolver}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserPrincipal {
}
