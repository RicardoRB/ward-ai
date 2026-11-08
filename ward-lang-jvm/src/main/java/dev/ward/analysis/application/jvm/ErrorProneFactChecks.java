package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.CallSuperCheck;
import dev.ward.analysis.domain.rule.check.CloseResourceCheck;
import dev.ward.analysis.domain.rule.check.CollectionTypeMismatchCheck;
import dev.ward.analysis.domain.rule.check.ConfusingArgumentToVarargsMethodCheck;
import dev.ward.analysis.domain.rule.check.InvalidLogMessageFormatCheck;
import dev.ward.analysis.domain.rule.check.NonSerializableClassCheck;
import dev.ward.analysis.domain.rule.check.ProperLoggerCheck;
import dev.ward.analysis.domain.rule.check.UseCorrectExceptionLoggingCheck;
import dev.ward.analysis.domain.rule.check.WrongTestAnnotationCheck;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** Builds the PMD error-prone checks that read the type facts or the imports of a file. */
final class ErrorProneFactChecks {

  private static final String MESSAGE = "message";

  private ErrorProneFactChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("call-super", ErrorProneFactChecks::callSuper),
        entry("proper-logger", ErrorProneFactChecks::properLogger),
        entry(
            "wrong-test-annotation",
            toml -> new WrongTestAnnotationCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "use-correct-exception-logging",
            toml -> new UseCorrectExceptionLoggingCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "confusing-argument-to-varargs-method",
            toml -> new ConfusingArgumentToVarargsMethodCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "invalid-log-message-format",
            toml -> new InvalidLogMessageFormatCheck(TomlValues.required(toml, MESSAGE))),
        entry("close-resource", toml -> new CloseResourceCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "non-serializable-class",
            toml -> new NonSerializableClassCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "collection-type-mismatch",
            toml -> new CollectionTypeMismatchCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static CallSuperCheck callSuper(final TomlParseResult toml) {
    return new CallSuperCheck(
        CallSuperCheck.Position.valueOf(
            TomlValues.required(toml, "position").toUpperCase(Locale.ROOT)),
        TomlValues.strings(toml, "methods"),
        TomlValues.strings(toml, "ancestors"),
        TomlValues.required(toml, MESSAGE));
  }

  private static ProperLoggerCheck properLogger(final TomlParseResult toml) {
    return new ProperLoggerCheck(
        toml.getString("logger-class", () -> "org.apache.commons.logging.Log"),
        toml.getString("static-logger-name", () -> "LOG"),
        toml.getString("logger-name", () -> "log"),
        TomlValues.required(toml, MESSAGE));
  }
}
