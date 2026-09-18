package com.alchemist.deepexplore.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage(
                            "..adapter.."
                    );

    @ArchTest
    static final ArchRule input_adapters_do_not_use_output_ports =
            noClasses().that().resideInAPackage("..adapter.in.web..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..port..",
                            "..adapter.out.."
                    );

    @ArchTest
    static final ArchRule agent_does_not_depend_on_conversation =
            noClasses().that().resideInAPackage("..agent..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..conversation..",
                            "..coding..",
                            "..workspace.."
                    );

    @ArchTest
    static final ArchRule feature_modules_are_free_of_cycles =
            slices().matching("com.alchemist.deepexplore.(*)..")
                    .should().beFreeOfCycles()
                    .ignoreDependency(
                            resideInAnyPackage("..config..", "..api.."),
                            alwaysTrue()
                    )
                    .ignoreDependency(
                            alwaysTrue(),
                            resideInAnyPackage("..config..", "..api..")
                    );

    @ArchTest
    static final ArchRule workspace_application_has_no_filesystem_details =
            noClasses().that().resideInAPackage("..workspace.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "java.nio..",
                            "java.util.zip..",
                            "org.apache.commons.compress.."
                    );

    @ArchTest
    static final ArchRule workspace_is_independent_of_other_features =
            noClasses().that().resideInAPackage("..workspace..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..agent..",
                            "..coding..",
                            "..conversation..",
                            "..harness..",
                            "..runtime.."
                    );

    @ArchTest
    static final ArchRule harness_does_not_depend_on_runtime_or_workspace =
            noClasses().that().resideInAPackage("..harness..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..runtime..",
                            "..workspace..",
                            "..coding.."
                    );

    @ArchTest
    static final ArchRule coding_application_does_not_depend_on_adapters =
            noClasses().that().resideInAPackage("..coding.application..")
                    .should().dependOnClassesThat().resideInAPackage(
                            "..adapter.."
                    );

    @ArchTest
    static final ArchRule ports_do_not_depend_on_application_or_adapters =
            noClasses().that().resideInAPackage("..port..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..",
                            "..adapter.."
                    );
}
