package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.jvm.JavaSourceRoot;
import dev.ward.analysis.domain.port.TypeResolver;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.infrastructure.analysis.ProcessTypeSidecar;
import dev.ward.infrastructure.analysis.SidecarScripts;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Java type sidecar: {@code WardJavaTypes.java} run by the project's {@code java} in
 * source-launch mode, with the project's source roots as its source path.
 */
public final class JavaTypeSidecar implements TypeSidecar {
  private static final String SCRIPT = "WardJavaTypes.java";
  private static final String LANGUAGE = "java";

  private final Path projectRoot;
  private final String wardVersion;
  private ProcessTypeSidecar running;

  /**
   * Creates the sidecar for the project at {@code projectRoot}, cached under {@code wardVersion}.
   */
  public JavaTypeSidecar(final Path projectRoot, final String wardVersion) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
    this.wardVersion = wardVersion;
  }

  @Override
  public Optional<TypeResolver> start() {
    final Optional<Path> script = SidecarScripts.extract(projectRoot, wardVersion, SCRIPT);
    if (script.isEmpty()) {
      return Optional.empty();
    }
    final List<String> command = new ArrayList<>(List.of("java", script.get().toString()));
    command.add(projectRoot.toString());
    for (final JavaSourceRoot root : JvmSourceLayouts.of(projectRoot, List.of(projectRoot))) {
      command.add(projectRoot.resolve(root.directory()).toString());
    }
    running = new ProcessTypeSidecar(LANGUAGE, command, projectRoot);
    return running.start();
  }

  @Override
  public void close() {
    if (running != null) {
      running.close();
    }
  }
}
