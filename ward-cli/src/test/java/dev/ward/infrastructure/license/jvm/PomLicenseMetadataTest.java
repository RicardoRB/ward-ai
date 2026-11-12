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

class PomLicenseMetadataTest {

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
  void readsLicensesOfAPomInTheLocalRepository() throws IOException {
    maven(
        "com.example",
        "lib",
        "1.0",
        "<licenses><license><name>Apache License 2.0</name><url>https://x/y</url></license>"
            + "<license><name>MIT</name></license></licenses>");

    final LicenseLookup result = lookup("com.example:lib", "1.0");

    assertThat(result.present()).isTrue();
    assertThat(result.license().entries()).hasSize(2);
    assertThat(result.license().entries().getFirst().name()).isEqualTo("Apache License 2.0");
    assertThat(result.license().entries().getFirst().url()).isEqualTo("https://x/y");
    assertThat(result.detail()).isEqualTo("pom");
  }

  @Test
  void readsThePomFromTheGradleModuleCache() throws IOException {
    write(
        home.resolve("gradle"),
        "com.example/lib/2.0/abc123/lib-2.0.pom",
        pom("<licenses><license><name>MIT License</name></license></licenses>"));

    assertThat(lookup("com.example:lib", "2.0").license().describe()).isEqualTo("MIT License");
  }

  @Test
  void aPomThatIsNotLocalIsNotInstalledWithoutAnyNetworkAccess() {
    final LicenseLookup result = lookup("com.example:absent", "9");

    assertThat(result.present()).isFalse();
    assertThat(result.reason()).isEqualTo(LicenseLookup.Reason.NOT_INSTALLED);
  }

  @Test
  void aPackageBuiltInTheProjectReadsItsOwnPom() throws IOException {
    write(
        home.resolve("project"),
        "pom.xml",
        pom(
            "<groupId>g</groupId><artifactId>own</artifactId><version>1</version>"
                + "<licenses><license><name>MIT</name></license></licenses>"));

    assertThat(lookup("g:own", "1").license().describe()).isEqualTo("MIT");
    assertThat(lookup("g:own", "2").present()).isFalse();
  }

  @Test
  void aPomWithADoctypeIsRefused() throws IOException {
    write(
        home.resolve("m2"),
        "g/evil/1/evil-1.pom",
        "<!DOCTYPE project [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><project/>");

    assertThat(lookup("g:evil", "1").reason()).isEqualTo(LicenseLookup.Reason.NOT_INSTALLED);
  }
}
