package dev.ward.infrastructure.analysis.rust;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.rust.RustDependencyResolver;
import dev.ward.analysis.domain.rust.RustFileSummarizer;
import dev.ward.application.gate.DependencyAdapters;
import java.nio.file.Path;
import java.util.List;

/** Builds the Rust dependency adapter of a run from the crates of the project's Cargo manifests. */
public final class RustDependencyAdapters implements DependencyAdapters {
  @Override
  public List<DependencySupport> forProject(final Path projectRoot, final List<SourceUnit> units) {
    if (units.stream().noneMatch(unit -> unit.language() == AnalysisLanguage.RUST)
        || !RustLayouts.isRustProject(projectRoot)) {
      return List.of();
    }
    return List.of(
        new DependencySupport(
            AnalysisLanguage.RUST,
            new RustFileSummarizer(),
            new RustDependencyResolver(RustLayouts.read(projectRoot))
        )
    );
  }
}
