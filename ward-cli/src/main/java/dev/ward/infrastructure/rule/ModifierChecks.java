package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.AnnotatedDeclarationVisibilityCheck;
import dev.ward.analysis.domain.rule.check.AnnotationLocationCheck;
import dev.ward.analysis.domain.rule.check.AnnotationOnSameLineCheck;
import dev.ward.analysis.domain.rule.check.AnnotationUseStyleCheck;
import dev.ward.analysis.domain.rule.check.ClassMemberImpliedModifierCheck;
import dev.ward.analysis.domain.rule.check.InterfaceMemberImpliedModifierCheck;
import dev.ward.analysis.domain.rule.check.JavadocAnnotationCheck;
import dev.ward.analysis.domain.rule.check.ModifierOrderCheck;
import dev.ward.analysis.domain.rule.check.OpenjdkAnnotationLocationCheck;
import dev.ward.analysis.domain.rule.check.PackageAnnotationCheck;
import dev.ward.analysis.domain.rule.check.RecordAccessorOverrideCheck;
import dev.ward.analysis.domain.rule.check.RedundantModifierCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.SuppressWarningsCheck;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java modifier and annotation checks that a rule body names. Each parameter is a rule
 * option, named after the Checkstyle property in kebab-case.
 */
final class ModifierChecks {

  private static final String MESSAGE = "message";
  private static final String ALLOW_SINGLE_PARAMETERLESS =
      "allow-sameline-single-parameterless-annotation";
  private static final String ALLOW_PARAMETERIZED = "allow-sameline-parameterized-annotation";

  private static final String ANNOTATIONS = "annotations";
  private static final String VISIBILITY = "visibility";
  private static final String FORMAT = "format";
  private static final String TAG = "tag";
  private static final String ANNOTATION = "annotation";
  private static final String METHODS_ONLY = "methods-only";

  private static final Set<String> CHECKS =
      Set.of(
          "modifier-order",
          "annotation-location",
          "missing-override-on-record-accessor",
          "annotated-declaration-visibility",
          "redundant-modifier",
          "suppress-warnings",
          "package-annotation",
          "missing-override",
          "missing-deprecated",
          "annotation-on-same-line",
          "openjdk-annotation-location",
          "class-member-implied-modifier",
          "interface-member-implied-modifier",
          "annotation-use-style");

  private ModifierChecks() {}

  /** Returns whether {@code name} is one of the modifier and annotation checks. */
  static boolean isModifierCheck(final String name) {
    return CHECKS.contains(name);
  }

  /**
   * Builds the check named {@code name}.
   *
   * @throws dev.ward.analysis.domain.rule.RuleConfigurationException if a parameter is invalid
   */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "modifier-order" -> new ModifierOrderCheck(message(toml));
      case "annotation-location" ->
          new AnnotationLocationCheck(
              flag(toml, ALLOW_SINGLE_PARAMETERLESS, true),
              flag(toml, ALLOW_PARAMETERIZED, false),
              message(toml));
      case "annotated-declaration-visibility" ->
          new AnnotatedDeclarationVisibilityCheck(
              TomlValues.strings(toml, ANNOTATIONS),
              TomlValues.strings(toml, VISIBILITY),
              message(toml));
      case "redundant-modifier" -> new RedundantModifierCheck(message(toml));
      case "annotation-on-same-line" -> new AnnotationOnSameLineCheck(message(toml));
      case "openjdk-annotation-location" -> new OpenjdkAnnotationLocationCheck(message(toml));
      default -> annotationCheck(name, toml);
    };
  }

  /** The checks over suppressed warnings, package annotations and Javadoc tags. */
  private static RuleCheck annotationCheck(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "suppress-warnings" ->
          new SuppressWarningsCheck(
              Pattern.compile(TomlValues.required(toml, FORMAT)), message(toml));
      case "package-annotation" -> new PackageAnnotationCheck(message(toml));
      case "missing-override" ->
          new JavadocAnnotationCheck(
              TomlValues.required(toml, TAG), TomlValues.required(toml, ANNOTATION),
              flag(toml, METHODS_ONLY, true), message(toml));
      case "missing-deprecated" ->
          new JavadocAnnotationCheck(
              TomlValues.required(toml, TAG), TomlValues.required(toml, ANNOTATION),
              flag(toml, METHODS_ONLY, false), message(toml));
      default -> memberCheck(name, toml);
    };
  }

  /** The checks over implied modifiers of nested types and interface members. */
  private static RuleCheck memberCheck(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "class-member-implied-modifier" ->
          new ClassMemberImpliedModifierCheck(
              flag(toml, "violate-implied-static-on-nested-enum", true),
              flag(toml, "violate-implied-static-on-nested-interface", true),
              flag(toml, "violate-implied-static-on-nested-record", true),
              message(toml));
      case "interface-member-implied-modifier" ->
          new InterfaceMemberImpliedModifierCheck(
              new InterfaceMemberImpliedModifierCheck.Flags(
                  flag(toml, "violate-implied-abstract-method", true),
                  flag(toml, "violate-implied-final-field", true),
                  flag(toml, "violate-implied-public-field", true),
                  flag(toml, "violate-implied-public-method", true),
                  flag(toml, "violate-implied-public-nested", true),
                  flag(toml, "violate-implied-static-field", true),
                  flag(toml, "violate-implied-static-nested", true)),
              message(toml));
      case "annotation-use-style" ->
          new AnnotationUseStyleCheck(
              TomlValues.required(toml, "closing-parens"),
              TomlValues.required(toml, "element-style"),
              TomlValues.required(toml, "trailing-array-comma"),
              message(toml));
      default -> new RecordAccessorOverrideCheck(message(toml));
    };
  }

  private static boolean flag(
      final TomlParseResult toml, final String key, final boolean fallback) {
    return Optional.ofNullable(toml.getBoolean(key)).orElse(fallback);
  }

  private static String message(final TomlParseResult toml) {
    return TomlValues.required(toml, MESSAGE);
  }
}
