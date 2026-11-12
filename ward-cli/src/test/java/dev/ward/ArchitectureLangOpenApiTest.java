package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;

class ArchitectureLangOpenApiTest {

  private static final String FRONTEND = "ward-frontend-antlr";

  @Test
  void langOpenApiDependsOnlyOnAnalysisModules() throws IOException, XmlPullParserException {
    final Path pom =
        Path.of(System.getProperty("ward.repository.root", ""), "ward-lang-openapi", "pom.xml");
    final Model model;
    try (BufferedReader reader = Files.newBufferedReader(pom)) {
      model = new MavenXpp3Reader().read(reader);
    }

    for (final Dependency dependency : model.getDependencies()) {
      final String artifact = dependency.getArtifactId();
      assertThat(artifact).isNotEqualTo("ward-cli");
      assertThat(artifact).isNotEqualTo(FRONTEND);
    }
    assertThat(model.getDependencies().stream().map(Dependency::getArtifactId))
        .contains("ward-analysis-api", "ward-analysis-engine")
        .doesNotContainNull();
  }
}
