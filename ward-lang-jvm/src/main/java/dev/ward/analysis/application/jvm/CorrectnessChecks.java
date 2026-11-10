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
import java.util.HashMap;
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
                    typeRanges(toml),
                    toml.getBoolean("both", () -> false),
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

  /**
   * The left operand types with their allowed right literals: {@code types} share the {@code
   * literal-min} and {@code literal-max}, and each {@code type-ranges} entry {@code type:min:max}
   * has its own.
   */
  private static Map<String, BinaryOperandTypeCheck.Range> typeRanges(final TomlParseResult toml) {
    final BinaryOperandTypeCheck.Range shared =
        new BinaryOperandTypeCheck.Range(bound(toml, "literal-min"), bound(toml, "literal-max"));
    final Map<String, BinaryOperandTypeCheck.Range> ranges = new HashMap<>();
    TomlValues.optionalStrings(toml, "types").forEach(type -> ranges.put(type, shared));
    TomlValues.optionalStrings(toml, "type-ranges")
        .forEach(entry -> ranges.put(entry.split(":")[0], rangeOf(entry)));
    return ranges;
  }

  private static BinaryOperandTypeCheck.Range rangeOf(final String entry) {
    final String[] parts = entry.split(":");
    return new BinaryOperandTypeCheck.Range(
        Optional.of(new BigInteger(parts[1])), Optional.of(new BigInteger(parts[2])));
  }

  private static Optional<Pattern> pattern(final TomlParseResult toml, final String key) {
    return Optional.ofNullable(toml.getString(key)).map(Pattern::compile);
  }

  private static Optional<BigInteger> bound(final TomlParseResult toml, final String key) {
    return Optional.ofNullable(toml.getString(key)).map(BigInteger::new);
  }
}
