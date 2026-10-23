package com.bandup.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * The architecture rules, checked on every build. Rules 1–6 are the layering
 * rules of .kiro/specs/backend/design.md §12; rules 7–13 are the tactical DDD
 * rules for the domain layer: one module per aggregate, split into
 * building-block packages, building blocks declared with the
 * {@code shared.domain.ddd} markers. Packages may still be
 * empty while features are being built, so rules allow "no classes yet".
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
            .because("the domain must not depend on any technology at run time (no Spring, JPA, Jackson; Lombok is compile-time only and leaves nothing in the bytecode)")
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

    // ------------------------------------------------------------------------
    // Tactical design: inside a context's domain, one module per aggregate.
    // ------------------------------------------------------------------------

    /** {@code com.bandup.api.<context>.domain.<module>} → "context.module"; null outside a domain module. */
    private static final Pattern DOMAIN_MODULE = Pattern.compile("com\\.bandup\\.api\\.(\\w+)\\.domain\\.(\\w+).*");

    /** 7. No class sits directly in a context's domain package: everything lives in a module. */
    @ArchTest
    static final ArchRule domainClassesLiveInAModule = noClasses()
            .should().resideInAPackage("com.bandup.api.*.domain")
            .because("each aggregate (or shared-kernel topic) is its own module (subpackage) of the domain")
            .allowEmptyShould(true);

    /**
     * 8. Aggregates reference each other only by id. Applies to the contexts'
     * aggregate modules; the shared kernel's modules are topics (language,
     * error), not aggregates, and may use each other.
     */
    @ArchTest
    static final ArchRule aggregatesReferenceEachOtherOnlyById = classes()
            .that().resideInAPackage("com.bandup.api..domain..")
            .should(onlyUseOtherModulesThroughTheirIds())
            .because("an aggregate must not hold or change another aggregate; it may only point at it by id")
            .allowEmptyShould(true);

    /** 9. One repository per aggregate, as a port (interface) in that aggregate's module. */
    @ArchTest
    static void eachAggregateModuleHasAtMostOneRepositoryPort(JavaClasses classes) {
        Map<String, List<String>> repositoriesByModule = classes.stream()
                .filter(c -> c.getSimpleName().endsWith("Repository") && module(c) != null)
                .collect(Collectors.groupingBy(ArchitectureTest::module,
                        Collectors.mapping(JavaClass::getName, Collectors.toList())));

        assertThat(repositoriesByModule).allSatisfy((module, repositories) ->
                assertThat(repositories).as("repositories in domain module " + module).hasSizeLessThanOrEqualTo(1));
        classes.stream().filter(c -> c.getSimpleName().endsWith("Repository") && module(c) != null)
                .forEach(c -> assertThat(c.isInterface()).as(c.getName() + " must be a port (interface)").isTrue());
    }

    private static ArchCondition<JavaClass> onlyUseOtherModulesThroughTheirIds() {
        return new ArchCondition<>("use other aggregate modules of the same context only through their *Id types") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String from = module(origin);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    String to = module(target);
                    boolean otherModuleOfSameContext = from != null && to != null && !from.equals(to)
                            && context(from).equals(context(to)) && !context(from).equals("shared");
                    boolean isAnId = target.getSimpleName().endsWith("Id") && target.isAnnotatedWith(VALUE_OBJECT);
                    if (otherModuleOfSameContext && !isAnId) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static final String AGGREGATE_ROOT = "com.bandup.api.shared.domain.ddd.AggregateRoot";
    private static final String ENTITY = "com.bandup.api.shared.domain.ddd.Entity";
    private static final String VALUE_OBJECT = "com.bandup.api.shared.domain.ddd.ValueObject";
    private static final String DOMAIN_SERVICE = "com.bandup.api.shared.domain.ddd.DomainService";

    /** 10. Every domain class says which building block it is. */
    @ArchTest
    static void everyDomainClassDeclaresItsBuildingBlock(JavaClasses classes) {
        List<String> undeclared = domainClasses(classes)
                .filter(c -> !isRepositoryPort(c) && !c.isAnnotation() && !c.isAssignableTo(RuntimeException.class))
                .filter(c -> !(c.isAnnotatedWith(AGGREGATE_ROOT) || c.isAnnotatedWith(ENTITY)
                        || c.isAnnotatedWith(VALUE_OBJECT) || c.isAnnotatedWith(DOMAIN_SERVICE)))
                .map(JavaClass::getName)
                .toList();
        assertThat(undeclared).as("domain classes without @AggregateRoot/@Entity/@ValueObject/@DomainService").isEmpty();
    }

    /** 11. Each aggregate module has exactly one aggregate root. */
    @ArchTest
    static void eachAggregateModuleHasExactlyOneRoot(JavaClasses classes) {
        Map<String, List<String>> rootsByModule = aggregateModuleClasses(classes)
                .collect(Collectors.groupingBy(ArchitectureTest::module, Collectors.filtering(
                        c -> c.isAnnotatedWith(AGGREGATE_ROOT), Collectors.mapping(JavaClass::getName, Collectors.toList()))));
        assertThat(rootsByModule).allSatisfy((module, roots) ->
                assertThat(roots).as("aggregate roots in module " + module).hasSize(1));
    }

    /** 12. A repository stores its module's aggregate root, never an entity inside an aggregate. */
    @ArchTest
    static void repositoriesWorkWithTheirAggregateRoot(JavaClasses classes) {
        aggregateModuleClasses(classes).filter(ArchitectureTest::isRepositoryPort).forEach(repository -> {
            List<JavaClass> used = repository.getDirectDependenciesFromSelf().stream()
                    .map(d -> d.getTargetClass().getBaseComponentType()).toList();
            assertThat(used).as(repository.getName() + " must work with its module's @AggregateRoot")
                    .anyMatch(t -> t.isAnnotatedWith(AGGREGATE_ROOT) && module(repository).equals(module(t)));
            assertThat(used).as(repository.getName() + " must not store an inner @Entity")
                    .noneMatch(t -> t.isAnnotatedWith(ENTITY));
        });
    }

    /** {@code com.bandup.api.<context>.domain.<module>.<block>} → block; null if directly in the module. */
    private static final Pattern BUILDING_BLOCK_PACKAGE = Pattern.compile("com\\.bandup\\.api\\.\\w+\\.domain\\.\\w+\\.(\\w+)");

    /**
     * 13. Inside an aggregate module, every class lives in the package of its
     * building block: aggregate/ (the root), entity/, valueobject/, repository/
     * (ports), service/ (domain services), exception/.
     */
    @ArchTest
    static void aggregateModulesAreSplitByBuildingBlock(JavaClasses classes) {
        List<String> misplaced = aggregateModuleClasses(classes)
                .filter(c -> !inItsBuildingBlockPackage(c))
                .map(JavaClass::getName)
                .toList();
        assertThat(misplaced).as("classes not in the package of their building block").isEmpty();
    }

    private static boolean inItsBuildingBlockPackage(JavaClass c) {
        Matcher m = BUILDING_BLOCK_PACKAGE.matcher(c.getPackageName());
        if (!m.matches()) {
            return false; // directly in the module folder
        }
        return switch (m.group(1)) {
            case "aggregate" -> c.isAnnotatedWith(AGGREGATE_ROOT);
            case "entity" -> c.isAnnotatedWith(ENTITY);
            case "valueobject" -> c.isAnnotatedWith(VALUE_OBJECT);
            case "service" -> c.isAnnotatedWith(DOMAIN_SERVICE);
            case "repository" -> isRepositoryPort(c);
            case "exception" -> c.isAssignableTo(RuntimeException.class);
            default -> false;
        };
    }

    /** Top-level classes in any domain module (nested types belong to their outer class). */
    private static Stream<JavaClass> domainClasses(JavaClasses classes) {
        return classes.stream().filter(c -> module(c) != null && c.getEnclosingClass().isEmpty());
    }

    /** Domain classes of the contexts' aggregate modules (the shared kernel has topics, not aggregates). */
    private static Stream<JavaClass> aggregateModuleClasses(JavaClasses classes) {
        return domainClasses(classes).filter(c -> !context(module(c)).equals("shared"));
    }

    private static boolean isRepositoryPort(JavaClass c) {
        return c.isInterface() && c.getSimpleName().endsWith("Repository");
    }

    private static String module(JavaClass javaClass) {
        Matcher m = DOMAIN_MODULE.matcher(javaClass.getPackageName());
        return m.matches() ? m.group(1) + "." + m.group(2) : null;
    }

    private static String context(String module) {
        return module.substring(0, module.indexOf('.'));
    }
}
