package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.MisplacedScopeCheck;
import dev.ward.analysis.domain.rule.check.NonRuntimeAnnotationCheck;
import dev.ward.analysis.domain.rule.check.PackageInfoTypesCheck;
import dev.ward.analysis.domain.rule.check.PrivateLambdaFieldCheck;
import dev.ward.analysis.domain.rule.check.QualifierCountCheck;
import dev.ward.analysis.domain.rule.check.TypeParameterQualifierCheck;
import dev.ward.analysis.domain.rule.check.UnusedCollectionModifiedCheck;
import java.util.Map;

/** Builds the checks that read what one file declares: annotations, fields and type parameters. */
final class DeclarationContextChecks {

  private static final String MESSAGE = "message";

  private DeclarationContextChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "package-info-types",
            toml -> new PackageInfoTypesCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "private-lambda-field",
            toml -> new PrivateLambdaFieldCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unused-collection-modified",
            toml -> new UnusedCollectionModifiedCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "misplaced-scope", toml -> new MisplacedScopeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "qualifier-count", toml -> new QualifierCountCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "non-runtime-annotation-lookup",
            toml -> new NonRuntimeAnnotationCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "type-parameter-qualifier",
            toml -> new TypeParameterQualifierCheck(TomlValues.required(toml, MESSAGE))));
  }
}
