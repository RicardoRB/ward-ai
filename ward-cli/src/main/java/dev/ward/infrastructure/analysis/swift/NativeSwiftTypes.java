package dev.ward.infrastructure.analysis.swift;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.swift.SwiftTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Swift type facts of a project, answered in-process: the project's Swift files are parsed once
 * and the native resolver reads them. No process starts, so the native image needs no runtime.
 */
public final class NativeSwiftTypes implements TypeSidecar {

  private final Path projectRoot;

  /** Creates the facts of the project at {@code projectRoot}. */
  public NativeSwiftTypes(final Path projectRoot) {
    this.projectRoot = projectRoot;
  }

  @Override
  public Optional<TypeFacts> start() {
    final Map<String, SyntaxNode> trees = new HashMap<>();
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    for (final SourceUnit unit :
        new FileSystemSourceUnits().read(projectRoot, Set.of(AnalysisLanguage.SWIFT))) {
      trees.put(unit.path(), frontend.parse(unit).tree());
    }
    return Optional.of(new SwiftTypeFacts(SwiftLayouts.read(projectRoot), trees));
  }

  @Override
  public void close() {
    // Nothing runs in a process, so there is nothing to stop.
  }
}
