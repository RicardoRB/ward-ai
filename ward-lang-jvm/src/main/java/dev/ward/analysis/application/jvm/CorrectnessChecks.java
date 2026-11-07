package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.BinaryOperandTypeCheck;
import dev.ward.analysis.domain.rule.check.IgnoredResultCheck;
import dev.ward.analysis.domain.rule.check.InvalidRegexLiteralCheck;
import dev.ward.analysis.domain.rule.check.SelfRecursiveMethodCheck;
import dev.ward.analysis.domain.rule.check.TypedCallCheck;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/** The checks of the SpotBugs correctness rules that read operand types, calls or literals. */
final class CorrectnessChecks {

  private static final String MESSAGE = "message";

  private CorrectnessChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "binary-operand-type",
            toml ->
                new BinaryOperandTypeCheck(
                    TomlValues.strings(toml, "operators"),
                    TomlValues.strings(toml, "types"),
                    toml.getBoolean("both", () -> false),
                    new BinaryOperandTypeCheck.Range(
                        bound(toml, "literal-min"), bound(toml, "literal-max")),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "typed-call",
            toml ->
                new TypedCallCheck(
                    Pattern.compile(TomlValues.required(toml, "method")),
                    new TypedCallCheck.Receiver(
                        pattern(toml, "receiver-text"), pattern(toml, "receiver-type")),
                    new TypedCallCheck.Argument(
                        TomlValues.optionalInteger(toml, "argument", 0),
                        TomlValues.optionalStrings(toml, "argument-types"),
                        pattern(toml, "argument-text")),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "ignored-result",
            toml ->
                new IgnoredResultCheck(
                    TomlValues.strings(toml, "types"), TomlValues.required(toml, MESSAGE))),
        entry(
            "invalid-regex-literal",
            toml -> new InvalidRegexLiteralCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "self-recursive-method",
            toml -> new SelfRecursiveMethodCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static Optional<Pattern> pattern(final TomlParseResult toml, final String key) {
    return Optional.ofNullable(toml.getString(key)).map(Pattern::compile);
  }

  private static Optional<BigInteger> bound(final TomlParseResult toml, final String key) {
    return Optional.ofNullable(toml.getString(key)).map(BigInteger::new);
  }
}
