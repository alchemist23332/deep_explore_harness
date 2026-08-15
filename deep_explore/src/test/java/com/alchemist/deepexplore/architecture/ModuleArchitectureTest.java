package com.alchemist.deepexplore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.alchemist.deepexplore",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_framework_independent =
            noClasses().that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "dev.langchain4j..",
                            "org.springframework.jdbc..",
                            "reactor.."
                    );

    @ArchTest
    static final ArchRule conversation_does_not_depend_on_agent_or_harness =
            noClasses().that().resideInAPackage("..conversation..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..agent..",
                            "..harness.."
                    );

    @ArchTest
    static final ArchRule web_adapters_do_not_use_postgres_adapters =
            noClasses().that().resideInAnyPackage("..adapter.in.web..", "..api..")
                    .should().dependOnClassesThat().resideInAPackage(
                            "..adapter.out.postgres.."
                    );

    @ArchTest
    static final ArchRule agent_spi_does_not_depend_on_langchain4j =
            noClasses().that().resideInAnyPackage("..agent.spi..", "..agent.domain..")
                    .should().dependOnClassesThat().resideInAPackage(
                            "dev.langchain4j.."
                    );
}
