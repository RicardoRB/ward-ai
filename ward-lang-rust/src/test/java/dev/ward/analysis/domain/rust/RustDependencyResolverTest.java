package dev.ward.analysis.domain.rust;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rust's {@code use} and {@code mod} paths become edges between the files of a Cargo layout. */
class RustDependencyResolverTest {
  private static final RustProjectLayout SINGLE =
      new RustProjectLayout(List.of(new RustCrate("app", "src")));
  private static final RustProjectLayout WORKSPACE =
      new RustProjectLayout(
          List.of(
              new RustCrate("core_lib", "crates/core/src"),
              new RustCrate("cli", "crates/cli/src")
          )
      );

  @Test
  @DisplayName("a crate path reaches the file of the module")
  void aCratePathReachesTheFileOfTheModule() {
    final Set<String> edges =
        edges(
            SINGLE,
            Map.of(
                "src/lib.rs",
                "mod domain;\nmod infra;\n",
                "src/domain/mod.rs",
                "use crate::infra::db::connect;\n",
                "src/infra/mod.rs",
                "pub mod db;\n",
                "src/infra/db.rs",
                "pub fn connect() {}\n"
            )
        );

    assertThat(edges)
        .containsExactlyInAnyOrder(
            "src/domain/mod.rs->src/infra/db.rs",
            "src/lib.rs->src/domain/mod.rs",
            "src/lib.rs->src/infra/mod.rs",
            "src/infra/mod.rs->src/infra/db.rs"
        );
  }

  @Test
  @DisplayName("self and super paths resolve from the module of the file")
  void selfAndSuperPathsResolveFromTheModuleOfTheFile() {
    final Set<String> edges =
        edges(
            SINGLE,
            Map.of(
                "src/lib.rs",
                "",
                "src/a.rs",
                "use super::b::Thing;\nuse self::c::Other;\n",
                "src/b.rs",
                "pub struct Thing;\n",
                "src/a/c.rs",
                "pub struct Other;\n"
            )
        );

    assertThat(edges).containsExactlyInAnyOrder("src/a.rs->src/b.rs", "src/a.rs->src/a/c.rs");
  }

  @Test
  @DisplayName("a use inside an inline test module is not a dependency on the parent")
  void aUseInsideAnInlineTestModuleIsNotADependencyOnTheParent() {
    final Set<String> edges =
        edges(
            SINGLE,
            Map.of(
                "src/lib.rs",
                "mod domain;\n",
                "src/domain.rs",
                "#[cfg(test)]\nmod tests {\n  use super::*;\n}\n"
            )
        );

    assertThat(edges).containsExactly("src/lib.rs->src/domain.rs");
  }

  @Test
  @DisplayName("a workspace crate is reached by its name")
  void aWorkspaceCrateIsReachedByItsName() {
    final Set<String> edges =
        edges(
            WORKSPACE,
            Map.of(
                "crates/core/src/lib.rs",
                "pub mod money;\n",
                "crates/core/src/money.rs",
                "pub struct Money;\n",
                "crates/cli/src/main.rs",
                "use core_lib::money::Money;\nuse serde::Serialize;\n"
            )
        );

    assertThat(edges)
        .contains("crates/cli/src/main.rs->crates/core/src/money.rs")
        .noneMatch(edge -> edge.contains("serde"));
  }

  @Test
  @DisplayName("a module name in scope resolves like a self path")
  void aModuleNameInScopeResolvesLikeASelfPath() {
    final Set<String> edges =
        edges(
            SINGLE,
            Map.of(
                "src/lib.rs",
                "mod db;\nuse db::connect;\n",
                "src/db.rs",
                "pub fn connect() {}\n"
            )
        );

    assertThat(edges).containsExactly("src/lib.rs->src/db.rs");
  }

  private static Set<String> edges(
      final RustProjectLayout layout,
      final Map<String,
      String> sources
  ) {
    final RustFileSummarizer summarizer = new RustFileSummarizer();
    final List<FileSummary> summaries =
        sources.entrySet().stream()
            .map(entry -> new SourceUnit(entry.getKey(), AnalysisLanguage.RUST, entry.getValue()))
            .map(unit -> summarizer.summarize(unit, RustScopeFixtures.tree(unit.text())))
            .toList();
    final Set<String> result = new TreeSet<>();
    for (final DependencyEdge edge : new RustDependencyResolver(layout).resolve(summaries)) {
      result.add(edge.from() + "->" + edge.to());
    }
    return result;
  }
}
