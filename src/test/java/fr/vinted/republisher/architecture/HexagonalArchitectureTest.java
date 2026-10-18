package fr.vinted.republisher.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// Les packages métier sont encore vides : allowEmptyShould évite un faux échec
// tant qu'aucune classe ne s'y trouve, sans affaiblir la règle ensuite.
@AnalyzeClasses(packages = "fr.vinted.republisher", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String ADAPTER = "..adapter..";
    private static final String INBOUND_ADAPTER = "..adapter.in..";
    private static final String OUTBOUND_ADAPTER = "..adapter.out..";

    @ArchTest
    static final ArchRule domain_is_free_of_frameworks =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.transaction..",
                            "org.hibernate..",
                            "com.microsoft.playwright..")
                    .because("the domain is plain Java, technology is plugged in through adapters")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_does_not_depend_on_application_or_adapters =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, ADAPTER)
                    .because("dependencies point inwards: adapter -> application -> domain")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters =
            noClasses().that().resideInAPackage(APPLICATION)
                    .should().dependOnClassesThat().resideInAPackage(ADAPTER)
                    .because("use cases only know the domain and its ports")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_uses_spring_for_transactions_only =
            noClasses().that().resideInAPackage(APPLICATION)
                    .should().dependOnClassesThat(
                            resideInAPackage("org.springframework..")
                                    .and(not(resideInAPackage("org.springframework.transaction.."))))
                    .orShould().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.persistence..",
                            "org.hibernate..",
                            "com.microsoft.playwright..")
                    .because("bean wiring and infrastructure belong to adapters")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule inbound_adapters_do_not_depend_on_outbound_adapters =
            noClasses().that().resideInAPackage(INBOUND_ADAPTER)
                    .should().dependOnClassesThat().resideInAPackage(OUTBOUND_ADAPTER)
                    .because("inbound adapters go through use cases, never straight to infrastructure")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule outbound_adapters_do_not_depend_on_inbound_adapters =
            noClasses().that().resideInAPackage(OUTBOUND_ADAPTER)
                    .should().dependOnClassesThat().resideInAPackage(INBOUND_ADAPTER)
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule jpa_entities_live_in_outbound_adapters =
            classes().that().areAnnotatedWith("jakarta.persistence.Entity")
                    .should().resideInAPackage(OUTBOUND_ADAPTER)
                    .because("persistence model and domain model are kept separate")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_has_no_technical_names =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().haveSimpleNameEndingWith("Dto")
                    .orShould().haveSimpleNameEndingWith("Entity")
                    .orShould().haveSimpleNameEndingWith("Manager")
                    .orShould().haveSimpleNameEndingWith("Helper")
                    .orShould().haveSimpleNameEndingWith("Data")
                    .because("domain names come from the ubiquitous language (docs/glossary.md)")
                    .allowEmptyShould(true);
}
