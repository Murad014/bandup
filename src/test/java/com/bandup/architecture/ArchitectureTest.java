package com.bandup.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The architecture rules from .kiro/specs/backend/design.md §12, checked on
 * every build. Packages may still be empty while features are being built, so
 * each rule allows "no classes yet" instead of failing on it.
 */
@AnalyzeClasses(packages = "com.bandup", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** 1. The frontend reaches the backend only over HTTP. */
    @ArchTest
    static final ArchRule uiNeverUsesTheBackend = noClasses()
            .that().resideInAPackage("com.bandup.ui..")
            .should().dependOnClassesThat().resideInAPackage("com.bandup.api..")
            .because("the frontend talks to the backend only over HTTP")
            .allowEmptyShould(true);

    /** 2. The domain is pure Java. */
    @ArchTest
    static final ArchRule domainIsPureJava = classes()
            .that().resideInAPackage("com.bandup.api..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.bandup.api..domain..")
            .because("the domain must not depend on any technology (no Spring, JPA, Jackson, Lombok)")
            .allowEmptyShould(true);

    /** 3. Use cases depend only on the domain, plus Spring's @Service and @Transactional. */
    @ArchTest
    static final ArchRule applicationUsesOnlyTheDomainAndSpringTransactions = classes()
            .that().resideInAPackage("com.bandup.api..application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..",
                    "com.bandup.api..domain..",
                    "com.bandup.api..application..",
                    "org.springframework.stereotype..",
                    "org.springframework.transaction..")
            .because("the application layer may only use Spring's @Service and @Transactional")
            .allowEmptyShould(true);

    /** 4. Controllers go through use cases, never straight to adapters. */
    @ArchTest
    static final ArchRule webDoesNotUseInfrastructure = noClasses()
            .that().resideInAPackage("com.bandup.api..web..")
            .should().dependOnClassesThat().resideInAPackage("com.bandup.api..infrastructure..")
            .because("the web layer talks only to the application layer")
            .allowEmptyShould(true);

    /** 5. Bounded contexts are independent; only the shared kernel may be used by all. */
    @ArchTest
    static final ArchRule contextsDoNotDependOnEachOther = slices()
            .matching("com.bandup.api.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(alwaysTrue(), resideInAPackage("com.bandup.api.shared.."))
            .because("contexts use each other only by id and through their own ports")
            .allowEmptyShould(true);

    /** 6. JPA entities are an infrastructure detail. */
    @ArchTest
    static final ArchRule entitiesLiveOnlyInInfrastructure = noClasses()
            .that().resideOutsideOfPackage("com.bandup.api..infrastructure..")
            .should().beAnnotatedWith("jakarta.persistence.Entity")
            .because("the domain has its own model; JPA entities are adapters")
            .allowEmptyShould(true);
}
