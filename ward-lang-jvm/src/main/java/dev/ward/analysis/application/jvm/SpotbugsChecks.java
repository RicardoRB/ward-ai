package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.LockScopeCallCheck;
import dev.ward.analysis.domain.rule.check.SynchronizedOnUpdatedFieldCheck;
import dev.ward.analysis.domain.rule.check.UnusedFieldCheck;
import dev.ward.analysis.domain.rule.check.VolatileIncrementCheck;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/** The checks of the Java rules ported from SpotBugs that need more than a node pattern. */
final class SpotbugsChecks {

  private static final String MESSAGE = "message";
  private static final String ANY = "any";

  private SpotbugsChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> all = new HashMap<>(SpotbugsClassChecks.checks());
    all.putAll(
        Map.ofEntries(
            entry(
                "lock-scope-call",
                toml ->
                    new LockScopeCallCheck(
                        Pattern.compile(TomlValues.required(toml, "call")),
                        presence(toml, "lock"),
                        presence(toml, "loop"),
                        TomlValues.required(toml, MESSAGE))),
            entry(
                "unused-private-field",
                toml ->
                    new UnusedFieldCheck(
                        UnusedFieldCheck.Mode.valueOf(
                            TomlValues.required(toml, "mode").toUpperCase(Locale.ROOT)),
                        TomlValues.required(toml, MESSAGE))),
            entry(
                "volatile-increment",
                toml -> new VolatileIncrementCheck(TomlValues.required(toml, MESSAGE))),
            entry(
                "synchronized-on-updated-field",
                toml ->
                    new SynchronizedOnUpdatedFieldCheck(
                        toml.getBoolean("guarding-only", () -> false),
                        TomlValues.required(toml, MESSAGE)))));
    return Map.copyOf(all);
  }

  private static LockScopeCallCheck.Presence presence(
      final TomlParseResult toml, final String key) {
    return LockScopeCallCheck.Presence.valueOf(
        toml.getString(key, () -> ANY).toUpperCase(Locale.ROOT));
  }
}
