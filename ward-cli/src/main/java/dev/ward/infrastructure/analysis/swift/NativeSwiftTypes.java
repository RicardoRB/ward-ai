package dev.ward.infrastructure.analysis.swift;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.swift.SwiftTypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The native Swift type facts of a project. The project's Swift files are parsed on the first
 * question a rule asks, so a run without a type rule parses nothing. No process starts.
 */
public final class NativeSwiftTypes implements TypeFacts {

  private final Path projectRoot;
  private final LanguageFrontend frontend;
  private final ReentrantLock lock = new ReentrantLock();
  private SwiftTypeFacts facts;

  /** Creates the facts of the project at {@code projectRoot}, with a parser of its own. */
  public NativeSwiftTypes(final Path projectRoot) {
    this(projectRoot, new AntlrLanguageFrontend());
  }

  /**
   * Creates the facts of the project at {@code projectRoot}, reading sources with {@code frontend},
   * which the other passes of the run share so that a unit is parsed once. Nothing is read yet.
   */
  public NativeSwiftTypes(final Path projectRoot, final LanguageFrontend frontend) {
    this.projectRoot = projectRoot;
    this.frontend = frontend;
  }

  @Override
  public UnitTypeFacts factsOf(
      final SourceUnit unit, final List<Span> spans, final Set<FactKind> kinds) {
    return delegate().factsOf(unit, spans, kinds);
  }

  private SwiftTypeFacts delegate() {
    lock.lock();
    try {
      if (facts == null) {
        facts = build();
      }
      return facts;
    } finally {
      lock.unlock();
    }
  }

  private SwiftTypeFacts build() {
    final Map<String, SyntaxNode> trees = new HashMap<>();
    for (final SourceUnit unit :
        new FileSystemSourceUnits(new ProcessCommandExecutor())
            .read(projectRoot, Set.of(AnalysisLanguage.SWIFT))) {
      trees.put(unit.path(), frontend.parse(unit).tree());
    }
    return new SwiftTypeFacts(SwiftLayouts.read(projectRoot), trees);
  }
}
