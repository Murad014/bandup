package com.bandup.api.shared.domain.ddd;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * DDD building block (tactical design). An immutable value, equal by its
 * content (e.g. an email address, a language code). Validates itself when
 * created.
 *
 * <p>Plain Java, so the domain stays free of frameworks. ArchitectureTest uses
 * these markers to enforce the tactical rules.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ValueObject {
}
