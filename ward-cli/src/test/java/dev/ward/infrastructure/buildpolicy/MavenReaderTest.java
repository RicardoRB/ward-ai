package dev.ward.infrastructure.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.buildpolicy.BuildFileException;
import dev.ward.domain.buildpolicy.Declaration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenReaderTest {
  private static final String POM = "pom.xml";

  @TempDir Path root;

  private ReaderProject project() {
    return new ReaderProject(root);
  }

  @Test
  void aMalformedPomIsAnExecutionErrorNamingTheFile() throws IOException {
    project().write(POM, "<project>");
    assertThatThrownBy(project()::all)
        .isInstanceOf(BuildFileException.class)
        .hasMessageContaining(POM);
  }

  @Test
  void aDoctypeIsNotResolvedAndEndsInAnError() throws IOException {
    project()
        .write(
            POM,
            "<!DOCTYPE project [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + ReaderProject.HEAD
                + "</project>");
    assertThatThrownBy(project()::all)
        .isInstanceOf(BuildFileException.class)
        .hasMessageContaining(POM);
  }

  @Test
  void aWrapperUrlWithoutAVersionIsAnError() throws IOException {
    project()
        .write(
            ".mvn/wrapper/maven-wrapper.properties",
            "distributionUrl=https://example.com/maven.zip\n");
    assertThatThrownBy(project()::all)
        .isInstanceOf(BuildFileException.class)
        .hasMessageContaining("maven-wrapper.properties");
  }

  @Test
  void aManagedVersionInTheRootCoversTheModule() throws IOException {
    project()
        .write(
            POM,
            ReaderProject.HEAD
                + "<packaging>pom</packaging><modules><module>api</module></modules><build>"
                + "<pluginManagement><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>"
                + "<version>3.13.0</version></plugin></plugins></pluginManagement></build></project>");
    project()
        .write(
            "api/pom.xml",
            "<project><parent><groupId>g</groupId><artifactId>a</artifactId><version>1</version></parent>"
                + "<artifactId>api</artifactId><build><plugins><plugin>"
                + "<artifactId>maven-compiler-plugin</artifactId></plugin></plugins></build></project>");
    final List<Declaration> compiler = project().named("maven-compiler-plugin");
    assertThat(compiler).extracting(Declaration::file).contains("api/pom.xml");
    assertThat(compiler)
        .filteredOn(d -> "api/pom.xml".equals(d.file()))
        .allSatisfy(d -> assertThat(d.version()).isEqualTo("3.13.0"));
  }

  @Test
  void aVersionOnlyInAnExternalParentIsFlagged() throws IOException {
    project()
        .write(
            POM,
            "<project><parent><groupId>org.springframework.boot</groupId>"
                + "<artifactId>spring-boot-starter-parent</artifactId><version>3.3.0</version></parent>"
                + "<artifactId>a</artifactId></project>");
    assertThat(project().named("maven-jar-plugin"))
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.version()).isNull();
              assertThat(d.flag()).isEqualTo(Declaration.Flag.EXTERNAL_PARENT);
            });
  }

  @Test
  void aPomWarNeedsTheWarPluginAndNotTheJarPlugin() throws IOException {
    project().write(POM, ReaderProject.HEAD + "<packaging>war</packaging></project>");
    assertThat(project().named("maven-war-plugin")).hasSize(1);
    assertThat(project().named("maven-jar-plugin")).isEmpty();
  }

  @Test
  void modulesOutsideTheListAreNotRead() throws IOException {
    project().write(POM, ReaderProject.HEAD + "</project>");
    project().write("src/test/resources/fixture/pom.xml", "not xml at all");
    assertThat(project().all().declarations()).isNotEmpty();
  }
}
