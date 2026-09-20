plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless")
}

group = "com.robsartin.jsgb"
version = "0.1.0-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

sourceSets {
    // The oracle fixtures under demos/src/test/resources/oracle are the single canonical copy
    // (see demos/src/test/resources/oracle/MANIFEST.md); lib's tests read them from here instead
    // of keeping a second copy in lib/src/test/resources.
    test { resources { srcDir("../demos/src/test/resources") } }
}

tasks.test {
    useJUnitPlatform()
    // SGB is a single-threaded library with global state; never run tests in parallel.
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    jvmArgs("-ea")
    finalizedBy(tasks.jacocoTestReport)
}

spotless {
    java { googleJavaFormat() }
}

jacoco { toolVersion = "0.8.15" }

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit { counter = "LINE"; minimum = "0.80".toBigDecimal() }
            limit { counter = "BRANCH"; minimum = "0.65".toBigDecimal() }
        }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
