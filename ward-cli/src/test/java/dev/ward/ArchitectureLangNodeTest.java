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

class ArchitectureLangNodeTest {

  private static final String TEST_SCOPE = "test";

  @Test
  void langNodeDependsOnlyOnAnalysisModulesAndTestsOnTheFrontend()
      throws IOException, XmlPullParserException {
    final Path pom =
        Path.of(System.getProperty("ward.repository.root", ""), "ward-lang-node", "pom.xml");
    final Model model;
    try (BufferedReader reader = Files.newBufferedReader(pom)) {
      model = new MavenXpp3Reader().read(reader);
    }

    for (final Dependency dependency : model.getDependencies()) {
      final String artifact = dependency.getArtifactId();
      assertThat(artifact).isNotEqualTo("ward-cli");
      if (artifact.equals("ward-frontend-antlr")) {
        assertThat(dependency.getScope()).as(artifact).isEqualTo(TEST_SCOPE);
      }
    }
    assertThat(model.getDependencies().stream().map(Dependency::getArtifactId))
        .contains("ward-analysis-api", "ward-analysis-engine", "ward-frontend-antlr")
        .doesNotContainNull();
  }
}
