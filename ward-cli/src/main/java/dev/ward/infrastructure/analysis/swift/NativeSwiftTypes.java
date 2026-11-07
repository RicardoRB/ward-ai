package dev.ward.infrastructure.analysis.swift;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.swift.SwiftTypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The native Swift type facts of a project. The project's Swift files are parsed on the first
 * question a rule asks, so a run without a type rule parses nothing. No process starts.
 */
public final class NativeSwiftTypes implements TypeFacts {

  private final Path projectRoot;
  private SwiftTypeFacts facts;

  /** Creates the facts of the project at {@code projectRoot}; nothing is read yet. */
  public NativeSwiftTypes(final Path projectRoot) {
    this.projectRoot = projectRoot;
  }

  @Override
  public synchronized UnitTypeFacts factsOf(
      final SourceUnit unit, final List<Span> spans, final Set<FactKind> kinds) {
    if (facts == null) {
      facts = build();
    }
    return facts.factsOf(unit, spans, kinds);
  }

  private SwiftTypeFacts build() {
    final Map<String, SyntaxNode> trees = new HashMap<>();
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    for (final SourceUnit unit :
        new FileSystemSourceUnits().read(projectRoot, Set.of(AnalysisLanguage.SWIFT))) {
      trees.put(unit.path(), frontend.parse(unit).tree());
    }
    return new SwiftTypeFacts(SwiftLayouts.read(projectRoot), trees);
  }
}
