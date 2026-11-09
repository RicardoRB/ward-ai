package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.KotlinBackingPropertyNamingCheck;
import dev.ward.analysis.domain.rule.check.KotlinConsecutiveCommentsCheck;
import dev.ward.analysis.domain.rule.check.KotlinEmptyFileCheck;
import dev.ward.analysis.domain.rule.check.KotlinFilenameCheck;
import dev.ward.analysis.domain.rule.check.KotlinFunctionExpressionBodyCheck;
import dev.ward.analysis.domain.rule.check.KotlinKdocPlacementCheck;
import dev.ward.analysis.domain.rule.check.KotlinSingleLineBlockCommentCheck;
import dev.ward.analysis.domain.rule.check.KotlinStringTemplateCheck;
import dev.ward.analysis.domain.rule.check.KotlinTrailingLambdaParenthesesCheck;
import dev.ward.analysis.domain.rule.check.KotlinUnitReturnCheck;
import java.util.Map;

/**
 * Builds the Kotlin checks of ktlint's standard set that read the tree, the file name or comments.
 */
final class KotlinLinterChecks {

  private static final String MESSAGE = "message";

  private KotlinLinterChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "kotlin-backing-property-naming",
            toml -> new KotlinBackingPropertyNamingCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-consecutive-comments",
            toml -> new KotlinConsecutiveCommentsCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-empty-file",
            toml -> new KotlinEmptyFileCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-filename", toml -> new KotlinFilenameCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-function-expression-body",
            toml -> new KotlinFunctionExpressionBodyCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-kdoc-placement",
            toml -> new KotlinKdocPlacementCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-single-line-block-comment",
            toml -> new KotlinSingleLineBlockCommentCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-string-template",
            toml -> new KotlinStringTemplateCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-trailing-lambda-parentheses",
            toml -> new KotlinTrailingLambdaParenthesesCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-unit-return",
            toml -> new KotlinUnitReturnCheck(TomlValues.required(toml, MESSAGE))));
  }
}
