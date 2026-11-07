package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.JavaSourceRoot;
import dev.ward.analysis.domain.jvm.types.JvmSources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The Java and Kotlin source files of a project, found by the layout convention: the top-level type
 * {@code a.b.C} lives in {@code a/b/C.java} or {@code a/b/C.kt} under a source root.
 */
public final class ProjectJvmSources implements JvmSources {
  private static final Logger LOGGER = Logger.getLogger(ProjectJvmSources.class.getName());
  private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_$]+(\\.[\\p{L}\\p{N}_$]+)*");

  private final Path projectRoot;
  private final List<JavaSourceRoot> roots;

  /** Creates the sources of the project at {@code projectRoot} under {@code roots}. */
  public ProjectJvmSources(final Path projectRoot, final List<JavaSourceRoot> roots) {
    this.projectRoot = projectRoot;
    this.roots = List.copyOf(roots);
  }

  @Override
  public Optional<SourceUnit> declaring(final String qualifiedName) {
    if (!NAME.matcher(qualifiedName).matches()) {
      return Optional.empty();
    }
    final String relative = qualifiedName.replace('.', '/');
    for (final JavaSourceRoot root : roots) {
      for (final String suffix : List.of(".java", ".kt")) {
        final Path file = projectRoot.resolve(root.directory()).resolve(relative + suffix);
        if (Files.isRegularFile(file)) {
          return read(file, suffix);
        }
      }
    }
    return Optional.empty();
  }

  private Optional<SourceUnit> read(final Path file, final String suffix) {
    try {
      final String path = projectRoot.relativize(file).toString().replace('\\', '/');
      return Optional.of(
          new SourceUnit(
              path,
              ".kt".equals(suffix) ? AnalysisLanguage.KOTLIN : AnalysisLanguage.JAVA,
              Files.readString(file)));
    } catch (IOException unreadable) {
      LOGGER.log(Level.FINE, "event=jvm-source-unreadable");
      return Optional.empty();
    }
  }
}
