package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ComparisonContractCheck;
import dev.ward.analysis.domain.rule.check.ComputeIfAbsentReferenceCheck;
import dev.ward.analysis.domain.rule.check.JavaIncomparableCheck;
import dev.ward.analysis.domain.rule.check.JavaIsInstanceClassCheck;
import dev.ward.analysis.domain.rule.check.JavaIsInstanceIncompatibleCheck;
import dev.ward.analysis.domain.rule.check.JavaJdk8ApiCheck;
import dev.ward.analysis.domain.rule.check.JavaJodaToSelfCheck;
import dev.ward.analysis.domain.rule.check.JavaLenientFormatCheck;
import dev.ward.analysis.domain.rule.check.JavaLossyCompareCheck;
import dev.ward.analysis.domain.rule.check.JavaNonFinalConstantCheck;
import dev.ward.analysis.domain.rule.check.JavaProtoNullComparisonCheck;
import dev.ward.analysis.domain.rule.check.JavaStaticQualifiedCheck;
import dev.ward.analysis.domain.rule.check.JavaVarargsConditionalCheck;
import java.util.Map;

/** Builds the checks of Error Prone rules that read the call targets and types of expressions. */
final class ErrorProneTypedChecks {

  private static final String MESSAGE = "message";

  private ErrorProneTypedChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "comparison-contract",
            toml -> new ComparisonContractCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "compute-if-absent-reference",
            toml -> new ComputeIfAbsentReferenceCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "incomparable-element",
            toml -> new JavaIncomparableCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "is-instance-incompatible-type",
            toml -> new JavaIsInstanceIncompatibleCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "non-final-compile-time-constant",
            toml -> new JavaNonFinalConstantCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "static-qualified-using-expression",
            toml -> new JavaStaticQualifiedCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "proto-null-comparison",
            toml -> new JavaProtoNullComparisonCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "is-instance-of-class",
            toml -> new JavaIsInstanceClassCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "jdk8-api",
            toml ->
                new JavaJdk8ApiCheck(
                    TomlValues.integer(toml, "java-release"), TomlValues.required(toml, MESSAGE))),
        entry("joda-to-self", toml -> new JavaJodaToSelfCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "lenient-format",
            toml -> new JavaLenientFormatCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "lossy-primitive-compare",
            toml -> new JavaLossyCompareCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "varargs-conditional",
            toml -> new JavaVarargsConditionalCheck(TomlValues.required(toml, MESSAGE))));
  }
}
