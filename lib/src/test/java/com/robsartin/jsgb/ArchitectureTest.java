package com.robsartin.jsgb;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

@AnalyzeClasses(
    packages = "com.robsartin.jsgb",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String BASE = "com.robsartin.jsgb";

  @ArchTest
  static final ArchRule noCycles =
      SlicesRuleDefinition.slices().matching(BASE + ".(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule graphDependsOnNothing =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".graph..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".io..", BASE + ".flip..", BASE + ".sort..");

  @ArchTest
  static final ArchRule ioDependsOnNothing =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".io..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".graph..", BASE + ".flip..", BASE + ".sort..");

  @ArchTest
  static final ArchRule flipDependsOnNothing =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".flip..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".graph..", BASE + ".io..", BASE + ".sort..");

  @ArchTest
  static final ArchRule sortDependsOnlyOnFlip =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".sort..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".graph..", BASE + ".io..");

  // Generators (raman, basic, rand, save) may depend on the kernel but not on sort, demo, or each
  // other. Each rule is a whitelist of everything the package is allowed to depend on (its own
  // package, its kernel packages, and the JDK); onlyDependOnClassesThat, unlike a blacklist of the
  // forbidden packages, also catches an undeclared kernel dependency (e.g. raman depending on io).
  @ArchTest
  static final ArchRule ramanDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".raman..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(BASE + ".raman..", BASE + ".graph..", "java..");

  @ArchTest
  static final ArchRule basicDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".basic..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(BASE + ".basic..", BASE + ".graph..", "java..");

  @ArchTest
  static final ArchRule randDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".rand..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(BASE + ".rand..", BASE + ".graph..", BASE + ".flip..", "java..");

  @ArchTest
  static final ArchRule saveDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".save..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(BASE + ".save..", BASE + ".graph..", BASE + ".io..", "java..");
}
