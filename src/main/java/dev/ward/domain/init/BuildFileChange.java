package dev.ward.domain.init;

import dev.ward.domain.project.Toolchain;
import java.nio.file.Path;
import java.util.List;

/**
 * Reversible build-file configuration change.
 *
 * @param toolchain the toolchain whose adapter made the change and can roll it back
 * @param file the edited build file
 * @param originalContent the file content before the change
 * @param tools the plugins added or updated
 * @param warnings compatibility warnings raised while editing
 * @param lockFile the lock file an install rewrote, or {@code null} when none was run
 * @param lockOriginal the lock file content before the install, or {@code null} when it did not
 *     exist
 * @param moduleScripts the module build scripts the change edited, restored on rollback
 */
public record BuildFileChange(
    Toolchain toolchain,
    Path file,
    String originalContent,
    List<String> tools,
    List<String> warnings,
    Path lockFile,
    String lockOriginal,
    List<ScriptEdit> moduleScripts) {
  /** Copies the lists; missing warnings or module scripts mean none. */
  public BuildFileChange {
    tools = List.copyOf(tools);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    moduleScripts = moduleScripts == null ? List.of() : List.copyOf(moduleScripts);
  }

  /** Creates a change that rewrote no lock file. */
  public BuildFileChange(
      final Toolchain toolchain,
      final Path file,
      final String originalContent,
      final List<String> tools,
      final List<String> warnings) {
    this(toolchain, file, originalContent, tools, warnings, null, null, List.of());
  }

  /** Creates a change without warnings. */
  public BuildFileChange(
      final Toolchain toolchain,
      final Path file,
      final String originalContent,
      final List<String> tools) {
    this(toolchain, file, originalContent, tools, List.of());
  }

  /** Returns whether any plugin was added or updated. */
  public boolean changed() {
    return !tools.isEmpty();
  }
}
