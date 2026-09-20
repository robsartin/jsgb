package com.robsartin.jsgb;

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
}
