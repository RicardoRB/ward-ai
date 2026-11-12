package dev.ward.infrastructure.license.jvm;

import static dev.ward.infrastructure.license.LicenseReaderFixtures.dependency;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.project;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomLicenseInheritanceTest {

  @TempDir Path home;

  private PomLicenseMetadata reader() {
    return new PomLicenseMetadata(home.resolve("m2"), home.resolve("gradle"));
  }

  private LicenseLookup lookup(final String name, final String version) {
    return reader()
        .lookup(
            project(home.resolve("project"), Language.JAVA, BuildTool.MAVEN),
            "",
            dependency(Ecosystem.MAVEN, name, version));
  }

  private static String pom(final String body) {
    return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">" + body + "</project>";
  }

  private void maven(
      final String group, final String artifact, final String version, final String body)
      throws IOException {
    write(
        home.resolve("m2"),
        group.replace('.', '/')
            + "/"
            + artifact
            + "/"
            + version
            + "/"
            + artifact
            + "-"
            + version
            + ".pom",
        pom(body));
  }

  @Test
  void inheritsLicensesFromTheParentPom() throws IOException {
    maven(
        "com.example",
        "lib",
        "1.0",
        "<parent><groupId>com.example</groupId><artifactId>parent</artifactId><version>3</version></parent>");
    maven(
        "com.example",
        "parent",
        "3",
        "<licenses><license><name>The Apache Software License, Version 2.0</name></license></licenses>");

    assertThat(lookup("com.example:lib", "1.0").license().describe())
        .isEqualTo("The Apache Software License, Version 2.0");
  }

  @Test
  void aPomWithoutLicensesAndParentDeclaresNone() throws IOException {
    maven("com.example", "lib", "1.0", "<name>lib</name>");

    final LicenseLookup result = lookup("com.example:lib", "1.0");

    assertThat(result.present()).isFalse();
    assertThat(result.reason()).isEqualTo(LicenseLookup.Reason.NO_LICENSE_DECLARED);
  }

  @Test
  void aCyclicParentChainEndsWithoutALicense() throws IOException {
    maven(
        "g",
        "a",
        "1",
        "<parent><groupId>g</groupId><artifactId>b</artifactId><version>1</version></parent>");
    maven(
        "g",
        "b",
        "1",
        "<parent><groupId>g</groupId><artifactId>a</artifactId><version>1</version></parent>");

    final LicenseLookup result = lookup("g:a", "1");

    assertThat(result.present()).isFalse();
    assertThat(result.detail()).contains("cyclic");
  }

  @Test
  void aTooDeepParentChainEndsWithoutALicense() throws IOException {
    for (int i = 0; i <= PomLicenseMetadata.MAX_PARENTS + 1; i++) {
      maven(
          "g",
          "p" + i,
          "1",
          "<parent><groupId>g</groupId><artifactId>p"
              + (i + 1)
              + "</artifactId><version>1</version></parent>");
    }

    assertThat(lookup("g:p0", "1").detail()).contains("too deep");
  }

  @Test
  void aMissingParentPomIsNotInstalled() throws IOException {
    maven(
        "g",
        "a",
        "1",
        "<parent><groupId>g</groupId><artifactId>gone</artifactId><version>1</version></parent>");

    assertThat(lookup("g:a", "1").reason()).isEqualTo(LicenseLookup.Reason.NOT_INSTALLED);
  }
}
