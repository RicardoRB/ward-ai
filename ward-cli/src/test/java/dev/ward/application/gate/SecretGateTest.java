package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.SecretsOptions;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.secrets.HistoryPatch;
import dev.ward.domain.secrets.HistoryPatch.AddedLine;
import dev.ward.domain.secrets.HistoryUnavailableException;
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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code secrets} gate over real files, the bundled rules and a fake Git history. */
final class SecretGateTest {

  /** A fake token built at run time, so that no source file holds one. */
  private static final String TOKEN = "ghp_" + "aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5";

  private static final String CONFIG = "config/app.yml";
  private static final String TOKEN_LINE = "a: 1\nb: 2\ntoken: " + TOKEN + "\n";

  @TempDir Path root;

  private final Map<String, String> cache = new HashMap<>();
  private final List<String> historyCalls = new ArrayList<>();
  private List<HistoryPatch> patches = List.of();
  private RuntimeException historyFailure;
  private String ignored = "";

  @BeforeEach
  void setUp() throws IOException {
    write("README.md", "nothing here\n");
  }

  @Test
  void reportsTheTokenWithRuleFileLineAndColumnAndNoSecret() throws IOException {
    write(CONFIG, TOKEN_LINE);

    final GateResult result = run(SecretsOptions.NONE, List.of());

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final Finding finding = onlyFinding(result);
    assertThat(finding.ruleId()).isEqualTo("ward:secrets/github-pat");
    assertThat(finding.file()).isEqualTo(CONFIG);
    assertThat(finding.line()).isEqualTo(3);
    assertThat(finding.column()).isEqualTo(8);
    assertThat(result.toString()).doesNotContain(TOKEN).doesNotContain(TOKEN.substring(0, 8));
    assertThat(finding.toString()).doesNotContain("token:");
  }

  @Test
  void aCleanProjectPasses() {
    assertThat(run(SecretsOptions.NONE, List.of()).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void hiddenFoldersAreScanned() throws IOException {
    write(".github/workflows/deploy.yml", "env: " + TOKEN + "\n");

    assertThat(onlyFinding(run(SecretsOptions.NONE, List.of())).file())
        .isEqualTo(".github/workflows/deploy.yml");
  }

  @Test
  void buildOutputAndIgnoredFilesAreSkipped() throws IOException {
    write("target/out.txt", TOKEN_LINE);
    write("node_modules/pkg/a.txt", TOKEN_LINE);
    write("secret.env", TOKEN_LINE);
    ignored = "secret.env\u0000";

    assertThat(run(SecretsOptions.NONE, List.of()).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aBinaryFileIsSkippedWithoutAnError() throws IOException {
    final byte[] bytes = (TOKEN_LINE + "\u0000").getBytes(StandardCharsets.ISO_8859_1);
    Files.write(root.resolve("blob.bin"), new byte[] {0, 1, 2});
    Files.write(root.resolve("blob.dat"), concat(new byte[] {0}, bytes));

    assertThat(run(SecretsOptions.NONE, List.of()).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aLatin1FileIsScannedInsteadOfRaisingAnError() throws IOException {
    Files.createDirectories(root.resolve("config"));
    Files.write(
        root.resolve(CONFIG),
        ("café: 1\ntoken: " + TOKEN + "\n").getBytes(StandardCharsets.ISO_8859_1));

    assertThat(onlyFinding(run(SecretsOptions.NONE, List.of())).line()).isEqualTo(2);
  }

  @Test
  void onlyTheEditedFilesAreScanned() throws IOException {
    write(CONFIG, TOKEN_LINE);
    write("src/other.yml", TOKEN_LINE);

    final GateResult result = run(SecretsOptions.NONE, List.of("src/other.yml", "deleted.yml"));

    assertThat(onlyFinding(result).file()).isEqualTo("src/other.yml");
  }

  @Test
  void aGitleaksCommentIsNotASuppression() throws IOException {
    write(CONFIG, "token: " + TOKEN + " # gitleaks:allow\n");

    assertThat(run(SecretsOptions.NONE, List.of()).status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void aTextFileOverTheLimitIsAnExecutionErrorNamingTheFile() throws IOException {
    writeLarge("big/data.txt");

    final GateResult result = run(SecretsOptions.NONE, List.of());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("big/data.txt");
  }

  @Test
  void anExcludedOversizedFileRaisesNoError() throws IOException {
    writeLarge("big/data.txt");

    final GateResult result =
        run(new SecretsOptions(false, Optional.empty(), List.of("big/**")), List.of());

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void checkLevelExcludePathsHideEveryRule() throws IOException {
    write("fixtures/a.yml", TOKEN_LINE);

    assertThat(
            run(new SecretsOptions(false, Optional.empty(), List.of("fixtures/**")), List.of())
                .status())
        .isEqualTo(GateStatus.PASSED);
  }

  @Test
  void ruleExcludePathsAndEntropyComeFromTheRuleConfiguration() throws IOException {
    write("docs/setup.md", TOKEN_LINE);
    write(CONFIG, TOKEN_LINE);
    write(
        "ward-rules.toml",
        "version = 1\n[rules.secrets.github-pat]\nexclude-paths = [\"docs/**\"]\n");

    final GateResult result = run(SecretsOptions.NONE, List.of());

    assertThat(onlyFinding(result).file()).isEqualTo(CONFIG);
  }

  @Test
  void aNegativeEntropyIsAConfigurationErrorNamingTheRuleAndTheOption() throws IOException {
    write(CONFIG, TOKEN_LINE);
    write("ward-rules.toml", "version = 1\n[rules.secrets.github-pat]\nentropy = -1\n");

    final GateResult result = run(SecretsOptions.NONE, List.of());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("github-pat").contains("entropy");
    assertThat(result.findings().items()).isEmpty();
  }

  @Test
  void aSecondRunReadsTheCacheAndAChangedFileRunsAgain() throws IOException {
    write(CONFIG, TOKEN_LINE);
    final GateResult first = run(SecretsOptions.NONE, List.of());
    final int stored = cache.size();

    final GateResult second = run(SecretsOptions.NONE, List.of());
    write(CONFIG, "clean\n");
    final GateResult third = run(SecretsOptions.NONE, List.of());

    assertThat(stored).isPositive();
    assertThat(second.findings().items()).isEqualTo(first.findings().items());
    assertThat(third.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void theHistoryIsNotReadUnlessAsked() throws IOException {
    write(CONFIG, "clean\n");

    run(SecretsOptions.NONE, List.of());

    assertThat(historyCalls).isEmpty();
  }

  @Test
  void aTokenAddedInACommitIsReportedWithTheCommitAndThePostImageLine() {
    patches =
        List.of(
            new HistoryPatch(
                "abcd1234",
                CONFIG,
                List.of(new AddedLine(40, "a: 1"), new AddedLine(41, "token: " + TOKEN))));

    final GateResult result =
        run(new SecretsOptions(true, Optional.of(LocalDate.of(2026, 1, 1)), List.of()), List.of());

    final Finding finding = onlyFinding(result);
    assertThat(finding.line()).isEqualTo(41);
    assertThat(finding.message()).endsWith("in commit abcd1234");
    assertThat(result.toString()).doesNotContain(TOKEN);
    assertThat(historyCalls).containsExactly("since=2026-01-01");
  }

  @Test
  void anUnreadableHistoryIsAnExecutionError() {
    historyFailure =
        new HistoryUnavailableException("the clone is shallow; fetch the full history");

    final GateResult result = run(new SecretsOptions(true, Optional.empty(), List.of()), List.of());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("shallow");
  }

  @Test
  void theHistoryIsLeftOutOfAnEditScan() throws IOException {
    write(CONFIG, "clean\n");

    run(new SecretsOptions(true, Optional.empty(), List.of()), List.of(CONFIG));

    assertThat(historyCalls).isEmpty();
  }

  private GateResult run(final SecretsOptions options, final List<String> focus) {
    final AnalysisPorts ports =
        new AnalysisPorts(
            new ProjectRuleSource(new ClasspathRuleSource()),
            new DeclarativeRuleMatcher(),
            new FileSystemSourceUnits(new FakeCommandExecutor(0, ignored, "")),
            List.of(),
            projectRoot -> AnalysisCacheFixtures.memory(cache),
            AnalysisCacheFixtures.codec(),
            "test",
            DependencyAdapters.none(),
            (unused, classpath) -> Map.of());
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
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            options);
    return gates
        .create(check, focus)
        .execute(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE));
  }

  /** The finding of the GitHub token rule; the generic key rule reports the same line too. */
  private static Finding onlyFinding(final GateResult result) {
    final List<Finding> found =
        result.findings().items().stream()
            .filter(finding -> finding.ruleId().equals("ward:secrets/github-pat"))
            .toList();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  private void writeLarge(final String path) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.write(file, "a".repeat(9 * 1024 * 1024).getBytes(StandardCharsets.ISO_8859_1));
  }

  private static byte[] concat(final byte[] first, final byte[] second) {
    final byte[] all = new byte[first.length + second.length];
    System.arraycopy(first, 0, all, 0, first.length);
    System.arraycopy(second, 0, all, first.length, second.length);
    return all;
  }
}
