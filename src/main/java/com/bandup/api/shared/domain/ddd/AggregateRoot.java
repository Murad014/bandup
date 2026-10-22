package com.bandup.api.shared.domain.ddd;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * DDD building block (tactical design). The root of an aggregate: the only
 * object outside code may hold or load. It guards the aggregate's rules;
 * everything inside is reached through it. Has a repository and an identity;
 * other aggregates refer to it only by that identity.
 *
 * <p>Plain Java, so the domain stays free of frameworks. ArchitectureTest uses
 * these markers to enforce the tactical rules.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AggregateRoot {
}
