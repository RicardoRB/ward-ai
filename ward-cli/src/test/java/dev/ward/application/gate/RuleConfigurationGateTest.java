package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Rule selection through {@code ward-rules.toml}. */
class RuleConfigurationGateTest {

  private static final String METHOD_LENGTH = "ward:java/method-length";
  private static final String TYPE_NAME = "ward:java/type-name";
  private static final String RULES_FILE = "ward-rules.toml";
  private static final String TABLE = "[rules.java.method-length]\n";
  private static final int SEVENTY = 70;

  @TempDir Path dir;

  private FixtureProject fixture() {
    return new FixtureProject(dir);
  }

  @Test
  void withoutTheFileTheListedRuleRunsWithItsBuiltInLimit() throws IOException {
    final FixtureProject f = fixture();
    f.source(FixtureProject.SRC_A, "class A", SEVENTY);

    final GateResult result = f.run(List.of(METHOD_LENGTH), null, new HashMap<>());

    assertThat(FixtureProject.ids(result)).containsExactly(METHOD_LENGTH);
  }

  @Test
  void aRulesListInBothFilesIsAConfigurationError() throws IOException {
    final FixtureProject f = fixture();
    f.source(FixtureProject.SRC_A, "class A", SEVENTY);
    f.config(FixtureProject.VERSION);

    final GateResult result = f.run(List.of(METHOD_LENGTH), null, new HashMap<>());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("ward.toml").contains(RULES_FILE);
  }

  @Test
  void excludeRemovesAPresetRuleAndKeepsTheOthers() throws IOException {
    final FixtureProject f = fixture();
    f.source(FixtureProject.SRC_A, "class lower", SEVENTY);
    f.config("version = 1\n[select]\nexclude = [\"" + METHOD_LENGTH + "\"]\n");

    final GateResult result = f.run(List.of(), null, new HashMap<>());

    assertThat(FixtureProject.ids(result)).contains(TYPE_NAME).doesNotContain(METHOD_LENGTH);
  }

  @Test
  void aTunedRuleThatIsNotSelectedIsRejected() throws IOException {
    final FixtureProject f = fixture();
    f.config("version = 1\n[select]\npreset = \"none\"\n" + TABLE + "max = 80\n");

    final GateResult result = f.run(List.of(), null, new HashMap<>());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains(RULES_FILE).contains(TABLE.strip());
  }

  @Test
  void aChangedOptionRerunsAndTheWarmResultEqualsTheColdOne() throws IOException {
    final FixtureProject f = fixture();
    f.source(FixtureProject.SRC_A, "class A", SEVENTY);
    final Map<String, String> cache = new HashMap<>();
    f.config(FixtureProject.VERSION + TABLE + "max = 60\n");
    assertThat(FixtureProject.ids(f.run(List.of(), null, cache))).contains(METHOD_LENGTH);

    f.config(FixtureProject.VERSION + TABLE + "max = 80\n");
    final GateResult warm = f.run(List.of(), null, cache);
    final GateResult cold = f.run(List.of(), null, new HashMap<>());

    assertThat(FixtureProject.ids(warm)).doesNotContain(METHOD_LENGTH);
    assertThat(warm.findings().items()).isEqualTo(cold.findings().items());
  }

  @Test
  void rulesFilePointsToAnotherFile() throws IOException {
    final FixtureProject f = fixture();
    f.source(FixtureProject.SRC_A, "class A", SEVENTY);
    Files.createDirectories(f.root().resolve("config"));
    Files.writeString(
        f.root().resolve("config/rules.toml"), FixtureProject.VERSION + TABLE + "max = 80\n");

    assertThat(FixtureProject.ids(f.run(List.of(), "config/rules.toml", new HashMap<>())))
        .doesNotContain(METHOD_LENGTH);
  }

  @Test
  void aMissingConfiguredFileIsAnError() {
    final FixtureProject f = fixture();
    final GateResult result = f.run(List.of(), "config/missing.toml", new HashMap<>());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("config/missing.toml").contains("does not exist");
  }
}
