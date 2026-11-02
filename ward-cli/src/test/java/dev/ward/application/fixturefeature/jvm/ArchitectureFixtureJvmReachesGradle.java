package dev.ward.application.fixturefeature.jvm;

import dev.ward.application.fixturefeature.gradle.ArchitectureFixtureGradleType;

/** A violating fixture: the JVM slice names the Gradle slice, which may depend on the JVM only. */
public final class ArchitectureFixtureJvmReachesGradle {
  public ArchitectureFixtureGradleType type;
}
