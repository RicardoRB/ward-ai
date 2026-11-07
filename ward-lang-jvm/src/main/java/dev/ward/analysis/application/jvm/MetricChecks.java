package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AnonymousLinesCheck;
import dev.ward.analysis.domain.rule.check.BooleanOperatorCheck;
import dev.ward.analysis.domain.rule.check.ClassCouplingCheck;
import dev.ward.analysis.domain.rule.check.LineLengthCheck;
import dev.ward.analysis.domain.rule.check.MethodCountCheck;
import dev.ward.analysis.domain.rule.check.NPathCheck;
import dev.ward.analysis.domain.rule.check.NcssCheck;
import java.util.List;
import java.util.Map;

/** Builds the Java size and metric checks that a rule body names, with their parameters. */
final class MetricChecks {

  private static final String MAX = "max";
  private static final String MESSAGE = "message";
  private static final String TAB_WIDTH = "tab-width";
  private static final String IGNORE_PATTERN = "ignore-pattern";
  private static final String METHOD_MAXIMUM = "method-maximum";
  private static final String CLASS_MAXIMUM = "class-maximum";
  private static final String FILE_MAXIMUM = "file-maximum";
  private static final String MAX_TOTAL = "max-total";
  private static final String MAX_PRIVATE = "max-private";
  private static final String MAX_PACKAGE = "max-package";
  private static final String MAX_PROTECTED = "max-protected";
  private static final String MAX_PUBLIC = "max-public";
  private static final String TYPES = "types";
  private static final String EXCLUDED_CLASSES = "excluded-classes";

  private MetricChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(build()));
  }

  private static Map<String, CheckFactory> build() {
    return Map.ofEntries(
        entry(
            "line-length",
            toml ->
                new LineLengthCheck(
                    TomlValues.integer(toml, MAX),
                    TomlValues.integer(toml, TAB_WIDTH),
                    TomlValues.required(toml, IGNORE_PATTERN),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "boolean-operators",
            toml ->
                new BooleanOperatorCheck(
                    TomlValues.integer(toml, MAX), TomlValues.required(toml, MESSAGE))),
        entry(
            "npath",
            toml ->
                new NPathCheck(TomlValues.integer(toml, MAX), TomlValues.required(toml, MESSAGE))),
        entry(
            "ncss",
            toml ->
                new NcssCheck(
                    TomlValues.integer(toml, METHOD_MAXIMUM),
                    TomlValues.integer(toml, CLASS_MAXIMUM),
                    TomlValues.integer(toml, FILE_MAXIMUM),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "method-count",
            toml ->
                new MethodCountCheck(
                    new MethodCountCheck.Limits(
                        TomlValues.integer(toml, MAX_TOTAL),
                        TomlValues.integer(toml, MAX_PRIVATE),
                        TomlValues.integer(toml, MAX_PACKAGE),
                        TomlValues.integer(toml, MAX_PROTECTED),
                        TomlValues.integer(toml, MAX_PUBLIC)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "class-coupling",
            toml ->
                new ClassCouplingCheck(
                    TomlValues.strings(toml, TYPES),
                    TomlValues.optionalStrings(toml, EXCLUDED_CLASSES),
                    TomlValues.integer(toml, MAX),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "anonymous-lines",
            toml ->
                new AnonymousLinesCheck(
                    TomlValues.integer(toml, MAX), TomlValues.required(toml, MESSAGE))));
  }
}
