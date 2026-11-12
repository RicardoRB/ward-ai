package dev.ward.application.gate.openapi;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.application.gate.AnalysisPorts;
import dev.ward.application.gate.DependencyAdapters;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.JsonAnalysisResultCodec;
import dev.ward.infrastructure.gate.FileSystemProjectFiles;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import dev.ward.infrastructure.rule.ProjectRuleSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class NativeLintFixture {

  static final String SPEC = "api/openapi.yaml";
  static final String CLEAN =
      "openapi: 3.0.3\npaths:\n  /pets:\n    get:\n      operationId: listPets\n";
  static final String VIOLATING =
      "openapi: 3.0.3\npaths:\n  /pets:\n    get:\n      summary: list\n";
  private static final String GATE = "openapi-lint";
  private static final AnalysisCache NO_CACHE =
      new AnalysisCache() {
        @Override
        public Optional<String> read(final String key) {
          return Optional.empty();
        }

        @Override
        public void write(final String key, final String value) {}
      };

  private final Path root;

  /** Selects only {@code operation-operation-id}, so each test is about one rule. */
  static final String ONLY_OPERATION_ID =
      "version = 1\n[select]\npreset = \"none\"\ninclude = [\"openapi.operation-operation-id\"]\n";

  NativeLintFixture(final Path root) {
    this.root = root;
    try {
      write("ward-rules.toml", ONLY_OPERATION_ID);
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  void write(final String path, final String text) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  GateResult lint() {
    final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());
    final AnalysisPorts ports =
        new AnalysisPorts(
            new ProjectRuleSource(new ClasspathRuleSource()),
            new DeclarativeRuleMatcher(),
            (unused, languages) -> List.of(),
            frontends,
            unused -> NO_CACHE,
            new JsonAnalysisResultCodec(new ForyJsonCodec()),
            "test",
            DependencyAdapters.none(),
            (unused, classpath) -> Map.of());
    return new OpenApiNativeLintGate(GATE, SPEC, null, ports, new FileSystemProjectFiles())
        .execute(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT));
  }
}
