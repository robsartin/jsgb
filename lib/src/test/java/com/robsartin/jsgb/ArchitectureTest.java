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

  // Generators added in increment 3a: words and miles also use sort (for gb_linksort); roget stays
  // on the plain kernel; plane additionally depends on miles (plane_miles reduces to it); dijk is
  // an algorithm module over graph alone.
  @ArchTest
  static final ArchRule wordsDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".words..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".words..",
              BASE + ".graph..",
              BASE + ".io..",
              BASE + ".flip..",
              BASE + ".sort..",
              "java..");

  @ArchTest
  static final ArchRule rogetDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".roget..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".roget..", BASE + ".graph..", BASE + ".io..", BASE + ".flip..", "java..");

  @ArchTest
  static final ArchRule milesDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".miles..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".miles..",
              BASE + ".graph..",
              BASE + ".io..",
              BASE + ".flip..",
              BASE + ".sort..",
              "java..");

  @ArchTest
  static final ArchRule planeDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".plane..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".plane..", BASE + ".graph..", BASE + ".flip..", BASE + ".miles..", "java..");

  @ArchTest
  static final ArchRule dijkDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".dijk..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(BASE + ".dijk..", BASE + ".graph..", "java..");

  // Generators added in increment 3b: books and games also use sort (for gb_linksort); econ stays
  // on the plain kernel plus flip; lisa is a plain-kernel generator; gates additionally depends on
  // flip (for the risc simulator's memory area).
  @ArchTest
  static final ArchRule booksDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".books..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".books..",
              BASE + ".graph..",
              BASE + ".io..",
              BASE + ".flip..",
              BASE + ".sort..",
              "java..");

  @ArchTest
  static final ArchRule econDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".econ..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".econ..", BASE + ".graph..", BASE + ".io..", BASE + ".flip..", "java..");

  @ArchTest
  static final ArchRule gamesDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".games..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".games..",
              BASE + ".graph..",
              BASE + ".io..",
              BASE + ".flip..",
              BASE + ".sort..",
              "java..");

  @ArchTest
  static final ArchRule lisaDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".lisa..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(BASE + ".lisa..", BASE + ".graph..", BASE + ".io..", "java..");

  @ArchTest
  static final ArchRule gatesDependsOnlyOnKernel =
      classes()
          .that()
          .resideInAPackage(BASE + ".gates..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(BASE + ".gates..", BASE + ".graph..", BASE + ".flip..", "java..");
}
