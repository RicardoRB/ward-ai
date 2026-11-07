package dev.ward.analysis.application.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.CheckFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Builds every check the JVM module registers from the bodies of its own bundled rules. */
final class JvmCheckProviderTest {

  private static final String CHECK = "check";

  @Test
  void everyBundledRuleBodyWhoseCheckTheModuleRegistersBuildsACheck() throws IOException {
    final Map<String, CheckFactory> checks = new JvmCheckProvider().checks();
    final Set<String> built = new HashSet<>();
    for (final String file : new JvmRuleFiles().ruleFiles()) {
      final TomlArray rules = Objects.requireNonNull(read(file).getArray("rules"), file);
      for (int index = 0; index < rules.size(); index++) {
        final TomlParseResult body = Toml.parse(rules.getTable(index).getString("body"));
        final CheckFactory factory = checks.get(body.getString(CHECK));
        if (factory != null) {
          assertThat(factory.create(body)).as(file + " " + body.getString(CHECK)).isNotNull();
          built.add(body.getString(CHECK));
        }
      }
    }

    assertThat(built).isNotEmpty();
  }

  @Test
  void registersEveryCheckUnderAName() {
    final Map<String, CheckFactory> checks = new JvmCheckProvider().checks();

    assertThat(checks).isNotEmpty();
    assertThat(checks.keySet()).doesNotContainNull();
  }

  private static TomlTable read(final String resource) throws IOException {
    try (InputStream in = JvmCheckProviderTest.class.getResourceAsStream("/" + resource)) {
      return Toml.parse(
          new String(Objects.requireNonNull(in, resource).readAllBytes(), StandardCharsets.UTF_8));
    }
  }
}
