package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.KotlinClassLinesCheck;
import dev.ward.analysis.domain.rule.check.KotlinComplexConditionCheck;
import dev.ward.analysis.domain.rule.check.KotlinEmptyBlockCheck;
import dev.ward.analysis.domain.rule.check.KotlinEmptyConstructorCheck;
import dev.ward.analysis.domain.rule.check.KotlinEmptyWhenCheck;
import dev.ward.analysis.domain.rule.check.KotlinFunctionCountCheck;
import dev.ward.analysis.domain.rule.check.KotlinMatchingDeclarationNameCheck;
import dev.ward.analysis.domain.rule.check.KotlinMemberNameCheck;
import dev.ward.analysis.domain.rule.check.KotlinNameShadowingCheck;
import dev.ward.analysis.domain.rule.check.KotlinObjectPropertyCheck;
import dev.ward.analysis.domain.rule.check.KotlinPackageDirectoryCheck;
import java.util.Map;
import java.util.Optional;
import org.tomlj.TomlParseResult;

/** Builds the Kotlin checks of detekt's empty-blocks, complexity and naming sets. */
final class KotlinDeclarationChecks {

  private static final String MESSAGE = "message";
  private static final String KINDS = "kinds";
  private static final String AFTER = "after";
  private static final String THRESHOLD = "threshold";
  private static final String ROOT_PACKAGE = "root-package";
  private static final String REQUIRE_ROOT = "require-root-in-declaration";
  private static final String MUST_BE_FIRST = "must-be-first";
  private static final String IGNORE_OVERRIDDEN = "ignore-overridden";
  private static final String CONSTANT_PATTERN = "constant-pattern";
  private static final String PROPERTY_PATTERN = "property-pattern";
  private static final String PRIVATE_PATTERN = "private-property-pattern";
  private static final String MAX = "max";
  private static final String IGNORE_PRIVATE = "ignore-private";
  private static final String IGNORE_DEPRECATED = "ignore-deprecated";
  private static final String THRESHOLD_FILES = "threshold-in-files";
  private static final String THRESHOLD_CLASSES = "threshold-in-classes";
  private static final String THRESHOLD_INTERFACES = "threshold-in-interfaces";
  private static final String THRESHOLD_OBJECTS = "threshold-in-objects";
  private static final String THRESHOLD_ENUMS = "threshold-in-enums";

  private KotlinDeclarationChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("kotlin-empty-block", KotlinDeclarationChecks::emptyBlock),
        entry(
            "kotlin-complex-condition",
            toml ->
                new KotlinComplexConditionCheck(
                    TomlValues.strings(toml, KINDS),
                    TomlValues.integer(toml, THRESHOLD),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-package-directory",
            toml ->
                new KotlinPackageDirectoryCheck(
                    Optional.ofNullable(toml.getString(ROOT_PACKAGE)).orElse(""),
                    bool(toml, REQUIRE_ROOT, false))),
        entry(
            "kotlin-file-name",
            toml ->
                new KotlinMatchingDeclarationNameCheck(
                    bool(toml, MUST_BE_FIRST, true), TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-member-name",
            toml ->
                new KotlinMemberNameCheck(
                    bool(toml, IGNORE_OVERRIDDEN, true), TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-name-shadowing",
            toml -> new KotlinNameShadowingCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-class-lines",
            toml ->
                new KotlinClassLinesCheck(
                    TomlValues.strings(toml, KINDS),
                    TomlValues.integer(toml, MAX),
                    TomlValues.required(toml, MESSAGE))),
        entry("kotlin-function-count", KotlinDeclarationChecks::functionCount),
        entry(
            "kotlin-empty-when",
            toml -> new KotlinEmptyWhenCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-empty-constructor",
            toml -> new KotlinEmptyConstructorCheck(TomlValues.required(toml, MESSAGE))),
        entry("kotlin-object-property", KotlinDeclarationChecks::objectProperty));
  }

  private static KotlinEmptyBlockCheck emptyBlock(final TomlParseResult toml) {
    return new KotlinEmptyBlockCheck(
        TomlValues.strings(toml, KINDS),
        Optional.ofNullable(toml.getString(AFTER)).orElse(""),
        TomlValues.required(toml, MESSAGE));
  }

  private static KotlinFunctionCountCheck functionCount(final TomlParseResult toml) {
    return new KotlinFunctionCountCheck(
        new KotlinFunctionCountCheck.Policy(
            TomlValues.integer(toml, THRESHOLD_FILES),
            TomlValues.integer(toml, THRESHOLD_CLASSES),
            TomlValues.integer(toml, THRESHOLD_INTERFACES),
            TomlValues.integer(toml, THRESHOLD_OBJECTS),
            TomlValues.integer(toml, THRESHOLD_ENUMS),
            bool(toml, IGNORE_PRIVATE, false),
            bool(toml, IGNORE_OVERRIDDEN, false),
            bool(toml, IGNORE_DEPRECATED, false)),
        TomlValues.required(toml, MESSAGE));
  }

  private static KotlinObjectPropertyCheck objectProperty(final TomlParseResult toml) {
    return new KotlinObjectPropertyCheck(
        TomlValues.required(toml, CONSTANT_PATTERN),
        TomlValues.required(toml, PROPERTY_PATTERN),
        TomlValues.required(toml, PRIVATE_PATTERN),
        TomlValues.required(toml, MESSAGE));
  }

  private static boolean bool(
      final TomlParseResult toml, final String key, final boolean fallback) {
    return Optional.ofNullable(toml.getBoolean(key)).orElse(fallback);
  }
}
