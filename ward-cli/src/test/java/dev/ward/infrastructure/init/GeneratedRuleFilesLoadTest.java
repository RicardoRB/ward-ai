package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectFixtures;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.w3c.dom.Element;

/**
 * Loads the rule files init writes with the real tools. The CLI fixtures use a stub {@code mvnw},
 * so without this a file the tools reject passes every other test.
 */
class GeneratedRuleFilesLoadTest {
  @TempDir Path root;

  @ParameterizedTest
  @EnumSource(QualityPreset.class)
  void enforcerRulesUseTheExternalRulesDocumentShape(final QualityPreset preset) throws Exception {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    final Element document =
        factory
            .newDocumentBuilder()
            .parse(generate(preset).resolve("enforcer-rules.xml").toFile())
            .getDocumentElement();

    assertThat(document.getTagName()).isEqualTo("enforcer");
    assertThat(document.getElementsByTagName("rules").getLength()).isOne();
    assertThat(document.getElementsByTagName("dependencyConvergence").getLength()).isOne();
  }

  private Path generate(final QualityPreset preset) {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    new PresetRuleFileWriter(RuleFileTargetsFixtures.defaults())
        .apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            preset,
            List.of(gate("enforcer")));
    return root.resolve("config");
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }
}
