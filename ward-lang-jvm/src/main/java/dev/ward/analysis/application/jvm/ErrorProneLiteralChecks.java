package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.InfiniteRecursionCheck;
import dev.ward.analysis.domain.rule.check.LiteralArgumentCheck;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** Builds the checks of Error Prone rules that read a literal argument or a method's own call. */
final class ErrorProneLiteralChecks {

  private static final String MESSAGE = "message";

  private ErrorProneLiteralChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "infinite-recursion",
            toml -> new InfiniteRecursionCheck(TomlValues.required(toml, MESSAGE))),
        entry("invalid-literal-argument", ErrorProneLiteralChecks::literal));
  }

  private static LiteralArgumentCheck literal(final TomlParseResult toml) {
    return new LiteralArgumentCheck(
        LiteralArgumentCheck.Mode.valueOf(
            TomlValues.required(toml, "mode").toUpperCase(Locale.ROOT)),
        TomlValues.required(toml, "call"),
        TomlValues.required(toml, MESSAGE));
  }
}
