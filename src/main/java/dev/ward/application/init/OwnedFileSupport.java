package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The Ward-owned files a toolchain writes: agent rules, partial-run tables and ignore entries. */
interface OwnedFileSupport {
  /**
   * Returns the lines the toolchain adds to the agent rules section; {@code owned} holds the rule
   * files the user owns, relative to the project root.
   */
  default List<String> agentRules(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Set<String> owned) {
    return List.of();
  }

  /**
   * Returns the partial-run table of {@code gate} for a gate this toolchain owns, or empty for any
   * other gate; {@code command} is the gate's full command.
   */
  default Optional<PartialCheckDefaults.Table> partialTable(
      final String gate,
      final Project project,
      final QualityPreset preset,
      final List<String> command) {
    return Optional.empty();
  }

  /**
   * Returns the entries of the toolchain's Ward-owned {@code .gitignore} section, or empty when the
   * toolchain writes none for {@code gates}.
   */
  default Optional<String> gitignoreEntries(
      final Project project, final List<InitGateOption> gates) {
    return Optional.empty();
  }

  /** Returns the gate IDs that {@link #partialTable} answers. */
  default Set<String> partialGates() {
    return Set.of();
  }
}
