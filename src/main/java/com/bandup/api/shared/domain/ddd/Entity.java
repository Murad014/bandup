package com.bandup.api.shared.domain.ddd;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * DDD building block (tactical design). An object inside an aggregate with its
 * own identity (equal by id, not by value). Changed only through its aggregate
 * root; has no repository of its own.
 *
 * <p>Plain Java, so the domain stays free of frameworks. ArchitectureTest uses
 * these markers to enforce the tactical rules.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Entity {
}
