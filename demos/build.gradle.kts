plugins {
    java
    application
    jacoco
    id("com.diffplug.spotless")
}

group = "com.robsartin.jsgb"
version = "0.1.0-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

application {
    mainClass = "com.robsartin.jsgb.demo.Jsgb"
    applicationName = "jsgb"
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":lib"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
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
