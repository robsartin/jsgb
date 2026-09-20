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

  // Generators (raman, basic, rand, save) may depend on the kernel (graph, flip, io) but not on
  // sort, demo, or each other. Each rule below forbids its own package from depending on the
  // *other* generator packages plus sort and demo; it must not name its own package, since a
  // generator legitimately depends on its own nested/helper classes (e.g. Raman's Quaternion,
  // Save's Block and Where).
  @ArchTest
  static final ArchRule ramanDependsOnlyOnKernel =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".raman..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".basic..",
              BASE + ".rand..",
              BASE + ".save..",
              BASE + ".sort..",
              BASE + ".demo..");

  @ArchTest
  static final ArchRule basicDependsOnlyOnKernel =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".basic..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".raman..",
              BASE + ".rand..",
              BASE + ".save..",
              BASE + ".sort..",
              BASE + ".demo..");

  @ArchTest
  static final ArchRule randDependsOnlyOnKernel =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".rand..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".raman..",
              BASE + ".basic..",
              BASE + ".save..",
              BASE + ".sort..",
              BASE + ".demo..");

  @ArchTest
  static final ArchRule saveDependsOnlyOnKernel =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".save..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".raman..",
              BASE + ".basic..",
              BASE + ".rand..",
              BASE + ".sort..",
              BASE + ".demo..");
}
