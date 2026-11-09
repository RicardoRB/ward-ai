package dev.ward.analysis.application.swift;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ConstructorGroupCheck;
import dev.ward.analysis.domain.rule.check.ImportSortCheck;
import dev.ward.analysis.domain.rule.check.JavadocMethodCheck;
import dev.ward.analysis.domain.rule.check.UnusedImportCheck;
import dev.ward.analysis.domain.rule.check.VariableDistanceCheck;
import dev.ward.analysis.domain.swift.SwiftBlanketDisableCommandCheck;
import dev.ward.analysis.domain.swift.SwiftDeploymentTargetCheck;
import dev.ward.analysis.domain.swift.SwiftDuplicateConditionsCheck;
import dev.ward.analysis.domain.swift.SwiftDuplicateEnumCasesCheck;
import dev.ward.analysis.domain.swift.SwiftDuplicateImportsCheck;
import dev.ward.analysis.domain.swift.SwiftDuplicatedDictionaryKeyCheck;
import dev.ward.analysis.domain.swift.SwiftForWhereCheck;
import dev.ward.analysis.domain.swift.SwiftGenericTypeNameCheck;
import dev.ward.analysis.domain.swift.SwiftHiddenFieldCheck;
import dev.ward.analysis.domain.swift.SwiftNsobjectPreferIsEqualCheck;
import dev.ward.analysis.domain.swift.SwiftOrphanedDocCommentCheck;
import dev.ward.analysis.domain.swift.SwiftParameterAlignmentCheck;
import dev.ward.analysis.domain.swift.SwiftRedundantSendableCheck;
import dev.ward.analysis.domain.swift.SwiftRedundantVoidReturnCheck;
import dev.ward.analysis.domain.swift.SwiftStaticOverFinalClassCheck;
import dev.ward.analysis.domain.swift.SwiftUnneededSynthesizedInitializerCheck;
import dev.ward.analysis.domain.swift.SwiftUnneededOverrideCheck;
import dev.ward.analysis.domain.swift.SwiftValidIbinspectableCheck;
import dev.ward.analysis.domain.swift.SwiftVoidFunctionInTernaryCheck;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * The declarative checks that only Swift rules use: the style checks of declarations and imports,
 * and the lint checks of declarations and statements.
 */
public final class SwiftCheckProvider implements CheckProvider {

  private static final String MESSAGE = "message";

  @Override
  public Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        Map.entry(
            "swift-hidden-field",
            toml -> new SwiftHiddenFieldCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-javadoc-method",
            toml -> new JavadocMethodCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-constructor-group",
            toml -> new ConstructorGroupCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-import-sort", toml -> new ImportSortCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-unused-import",
            toml -> new UnusedImportCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-parameter-alignment",
            toml -> new SwiftParameterAlignmentCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-redundant-void-return",
            toml -> new SwiftRedundantVoidReturnCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-duplicate-enum-cases",
            toml -> new SwiftDuplicateEnumCasesCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-for-where", toml -> new SwiftForWhereCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-static-over-final-class",
            toml -> new SwiftStaticOverFinalClassCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-duplicated-dictionary-key",
            toml -> new SwiftDuplicatedDictionaryKeyCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-generic-type-name",
            toml -> new SwiftGenericTypeNameCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-nsobject-prefer-isequal",
            toml -> new SwiftNsobjectPreferIsEqualCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-redundant-sendable",
            toml -> new SwiftRedundantSendableCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-orphaned-doc-comment",
            toml -> new SwiftOrphanedDocCommentCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-unneeded-override",
            toml -> new SwiftUnneededOverrideCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-valid-ibinspectable",
            toml -> new SwiftValidIbinspectableCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-duplicate-conditions",
            toml -> new SwiftDuplicateConditionsCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-duplicate-imports",
            toml -> new SwiftDuplicateImportsCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-blanket-disable-command",
            toml -> new SwiftBlanketDisableCommandCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-deployment-target",
            toml ->
                new SwiftDeploymentTargetCheck(
                    TomlValues.required(toml, MESSAGE), deploymentTargets(toml))),
        Map.entry(
            "swift-void-function-in-ternary",
            toml -> new SwiftVoidFunctionInTernaryCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-unneeded-synthesized-initializer",
            toml -> new SwiftUnneededSynthesizedInitializerCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-variable-distance",
            toml ->
                new VariableDistanceCheck(
                    TomlValues.integer(toml, "max"), TomlValues.required(toml, MESSAGE))));
  }

  /** The configured deployment target of each platform, keyed by the platform's name. */
  private static Map<String, String> deploymentTargets(final TomlParseResult toml) {
    final Map<String, String> targets = new LinkedHashMap<>();
    for (final String platform : List.of("iOS", "macOS", "tvOS", "watchOS")) {
      final String value = toml.getString(platform.toLowerCase(Locale.ROOT) + "-deployment-target");
      if (value != null) {
        targets.put(platform, value);
      }
    }
    return targets;
  }
}
