package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Builds the summaries, modules and rule the module-scoped accessibility tests run on. */
final class SwiftAccessibilityFixtures {

  static final String SHAPE = "Shape";
  static final String DECLARING = "Core/Shape.swift";
  static final String CORE_USER = "Core/Draw.swift";
  static final String APP_USER = "App/Scene.swift";
  static final String LOOSE = "Loose.swift";
  static final RuleDefinition RULE =
      new RuleDefinition(
          SwiftUnusedDefinitions.REDUNDANT_ACCESSIBILITY_ID,
          Set.of(AnalysisLanguage.SWIFT),
          RuleCapability.SYMBOLS,
          AnalysisSeverity.WARNING,
          "check = \"swift-unused-definition\"");

  /** The modules of the files a test names; a file not named here is in no known module. */
  static final Map<String, String> CORE =
      Map.of(DECLARING, "Core", CORE_USER, "Core", APP_USER, "App");

  private static final Span SPAN = new Span(1, 1, 1, 1, 0, 1);

  private SwiftAccessibilityFixtures() {}

  static List<AnalysisFinding> findings(
      final Map<String, FileSummary> files, final Map<String, String> modules) {
    final Function<String, Optional<String>> moduleOf =
        path -> Optional.ofNullable(modules.get(path));
    return SwiftModuleAccessibility.findings(RULE, files, moduleOf);
  }

  static FileSummary exporting(final String path, final String name) {
    return new FileSummary(
        path,
        List.of(new Symbol(name, SPAN, SwiftUnusedDefinitions.PUBLIC_KIND_PREFIX + "type")),
        List.of(),
        List.of(),
        Set.of());
  }

  static FileSummary using(final String path, final String name) {
    return new FileSummary(path, List.of(), List.of(), List.of(), Set.of(name));
  }

  static FileSummary summary(final String path, final List<Symbol> exports) {
    return new FileSummary(path, exports, List.of(), List.of(), Set.of());
  }

  static Symbol publicSymbol(final String name) {
    return new Symbol(name, SPAN, SwiftUnusedDefinitions.PUBLIC_KIND_PREFIX + "type");
  }
}
