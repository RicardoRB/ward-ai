package dev.ward.analysis.application.swift;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.swift.SwiftBlanketDisableCommandCheck;
import dev.ward.analysis.domain.swift.SwiftCaptureVariableCheck;
import dev.ward.analysis.domain.swift.SwiftDeploymentTargetCheck;
import dev.ward.analysis.domain.swift.SwiftDuplicateConditionsCheck;
import dev.ward.analysis.domain.swift.SwiftDuplicateEnumCasesCheck;
import dev.ward.analysis.domain.swift.SwiftDuplicateImportsCheck;
import dev.ward.analysis.domain.swift.SwiftDuplicatedDictionaryKeyCheck;
import dev.ward.analysis.domain.swift.SwiftForWhereCheck;
import dev.ward.analysis.domain.swift.SwiftGenericTypeNameCheck;
import dev.ward.analysis.domain.swift.SwiftNsobjectPreferIsEqualCheck;
import dev.ward.analysis.domain.swift.SwiftOrphanedDocCommentCheck;
import dev.ward.analysis.domain.swift.SwiftRedundantSendableCheck;
import dev.ward.analysis.domain.swift.SwiftRedundantVoidReturnCheck;
import dev.ward.analysis.domain.swift.SwiftStaticOverFinalClassCheck;
import dev.ward.analysis.domain.swift.SwiftTypesafeArrayInitCheck;
import dev.ward.analysis.domain.swift.SwiftUnneededOverrideCheck;
import dev.ward.analysis.domain.swift.SwiftUnneededSynthesizedInitializerCheck;
import dev.ward.analysis.domain.swift.SwiftUnusedSetterValueCheck;
import dev.ward.analysis.domain.swift.SwiftValidIbinspectableCheck;
import dev.ward.analysis.domain.swift.SwiftVoidFunctionInTernaryCheck;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The lint checks of declarations and statements that only Swift rules use. */
public final class SwiftLintCheckProvider implements CheckProvider {

  private static final String MESSAGE = "message";
  private static final String DEPLOYMENT_TARGET = "-deployment-target";

  @Override
  public Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> checks = new LinkedHashMap<>();
    lintChecks(checks);
    usageChecks(checks);
    return Map.copyOf(checks);
  }

  private static void lintChecks(final Map<String, CheckFactory> checks) {
    checks.put(
        "swift-blanket-disable-command",
        toml -> new SwiftBlanketDisableCommandCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-capture-variable",
        toml -> new SwiftCaptureVariableCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-deployment-target",
        toml ->
            new SwiftDeploymentTargetCheck(
                TomlValues.required(toml, MESSAGE), deploymentTargets(toml)));
    checks.put(
        "swift-duplicate-conditions",
        toml -> new SwiftDuplicateConditionsCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-duplicate-enum-cases",
        toml -> new SwiftDuplicateEnumCasesCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-duplicate-imports",
        toml -> new SwiftDuplicateImportsCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-duplicated-dictionary-key",
        toml -> new SwiftDuplicatedDictionaryKeyCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-for-where", toml -> new SwiftForWhereCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-generic-type-name",
        toml -> new SwiftGenericTypeNameCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-nsobject-prefer-isequal",
        toml -> new SwiftNsobjectPreferIsEqualCheck(TomlValues.required(toml, MESSAGE)));
  }

  private static void usageChecks(final Map<String, CheckFactory> checks) {
    checks.put(
        "swift-orphaned-doc-comment",
        toml -> new SwiftOrphanedDocCommentCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-redundant-sendable",
        toml -> new SwiftRedundantSendableCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-redundant-void-return",
        toml -> new SwiftRedundantVoidReturnCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-static-over-final-class",
        toml -> new SwiftStaticOverFinalClassCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-typesafe-array-init",
        toml -> new SwiftTypesafeArrayInitCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-unneeded-override",
        toml -> new SwiftUnneededOverrideCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-unneeded-synthesized-initializer",
        toml -> new SwiftUnneededSynthesizedInitializerCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-unused-setter-value",
        toml -> new SwiftUnusedSetterValueCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-valid-ibinspectable",
        toml -> new SwiftValidIbinspectableCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-void-function-in-ternary",
        toml -> new SwiftVoidFunctionInTernaryCheck(TomlValues.required(toml, MESSAGE)));
  }

  /** The configured deployment target of each platform, keyed by the platform's name. */
  private static Map<String, String> deploymentTargets(final TomlParseResult toml) {
    final Map<String, String> targets = new LinkedHashMap<>();
    for (final String platform : List.of("iOS", "macOS", "tvOS", "watchOS")) {
      final String value = toml.getString(platform.toLowerCase(Locale.ROOT) + DEPLOYMENT_TARGET);
      if (value != null) {
        targets.put(platform, value);
      }
    }
    return targets;
  }
}
