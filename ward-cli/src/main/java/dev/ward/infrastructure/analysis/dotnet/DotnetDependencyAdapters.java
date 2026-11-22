package dev.ward.infrastructure.analysis.dotnet;

import dev.ward.analysis.domain.csharp.CSharpFileSummarizer;
import dev.ward.analysis.domain.csharp.CSharpNamespaceResolver;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.application.gate.DependencyAdapters;
import java.nio.file.Path;
import java.util.List;

/** Builds the C# dependency adapter: namespaces declared and used, resolved across files. */
public final class DotnetDependencyAdapters implements DependencyAdapters {
  @Override
  public List<DependencySupport> forProject(final Path projectRoot, final List<SourceUnit> units) {
    if (units.stream().noneMatch(unit -> unit.language() == AnalysisLanguage.CSHARP)) {
      return List.of();
    }
    return List.of(
        new DependencySupport(
            AnalysisLanguage.CSHARP,
            new CSharpFileSummarizer(),
            new CSharpNamespaceResolver()
        )
    );
  }
}
