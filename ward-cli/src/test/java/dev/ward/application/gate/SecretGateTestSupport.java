package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.SecretsOptions;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.secrets.HistoryPatch;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import dev.ward.infrastructure.rule.ProjectRuleSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** The files, ports and fake Git history that the {@code secrets} gate tests share. */
class SecretGateTestSupport {

  protected static final String TOKEN = "ghp_" + "aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5";

  protected static final String CONFIG = "config/app.yml";
  protected static final String TOKEN_LINE = "a: 1\nb: 2\ntoken: " + TOKEN + "\n";

  protected static final int TOKEN_LINE_NUMBER = 3;
  protected static final int TOKEN_COLUMN = 8;
  protected static final int PREFIX_LENGTH = 8;
  protected static final int HISTORY_LINE = 41;
  protected static final int OVER_LIMIT = 9 * 1024 * 1024;

  @TempDir protected Path root;

  protected final Map<String, String> cache = new HashMap<>();
  protected final List<String> historyCalls = new ArrayList<>();
  protected List<HistoryPatch> patches = List.of();
  protected RuntimeException historyFailure;
  protected String ignored = "";

  @BeforeEach
  void setUp() throws IOException {
    write("README.md", "nothing here\n");
  }

  protected AnalysisPorts ports() {
    return new AnalysisPorts(
        new ProjectRuleSource(new ClasspathRuleSource()),
        new DeclarativeRuleMatcher(),
        new FileSystemSourceUnits(new FakeCommandExecutor(0, ignored, "")),
        List.of(),
        projectRoot -> AnalysisCacheFixtures.memory(cache),
        AnalysisCacheFixtures.codec(),
        "test",
        DependencyAdapters.none(),
        (unused, classpath) -> Map.of());
  }

  protected GateResult run(final SecretsOptions options, final List<String> focus) {
    final AnalysisPorts ports = ports();
    final SecretGates gates =
        new SecretGates(
            ports,
            (projectRoot, since, sink) -> {
              historyCalls.add("since=" + since.map(LocalDate::toString).orElse(""));
              if (historyFailure != null) {
                throw historyFailure;
              }
              sink.accept(patches);
            });
    final GateConfiguration check =
        new GateConfiguration(
            "secrets",
            "secrets",
            true,
            List.of(),
            Set.of("default"),
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            options,
            null,
            null,
            null,
            null,
            null,
            null);
    return gates
        .create(check, focus)
        .execute(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE));
  }

  /** The finding of the GitHub token rule; the generic key rule reports the same line too. */
  protected static Finding onlyFinding(final GateResult result) {
    final List<Finding> found =
        result.findings().items().stream()
            .filter(finding -> "ward:secrets/github-pat".equals(finding.ruleId()))
            .toList();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  protected void write(final String path, final String text) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  protected void writeLarge(final String path) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.write(file, "a".repeat(OVER_LIMIT).getBytes(StandardCharsets.ISO_8859_1));
  }

  protected static byte[] concat(final byte[] first, final byte[] second) {
    final byte[] all = new byte[first.length + second.length];
    System.arraycopy(first, 0, all, 0, first.length);
    System.arraycopy(second, 0, all, first.length, second.length);
    return all;
  }
}
