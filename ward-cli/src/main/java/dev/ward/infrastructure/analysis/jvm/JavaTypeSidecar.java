package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.jvm.JavaSourceRoot;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import dev.ward.infrastructure.analysis.ProcessTypeSidecar;
import dev.ward.infrastructure.analysis.SidecarScripts;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The Java type sidecar: {@code WardJavaTypes.java} run by the project's {@code java} in
 * source-launch mode, with the project's source roots as its source path.
 */
public final class JavaTypeSidecar implements TypeSidecar {
  private static final String SCRIPT = "WardJavaTypes.java";
  private static final String LANGUAGE = "java";
  private static final String CLASSPATH_FLAG = "--classpath=";
  private static final Logger LOGGER = Logger.getLogger(JavaTypeSidecar.class.getName());

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

  /**
   * Without a {@code java-classpath} file the sidecar does not start: library types would not
   * resolve.
   */
  @Override
  public Optional<TypeFacts> start() {
    return start(Optional.empty());
  }

  @Override
  public Optional<TypeFacts> start(final Optional<Path> classpathFile) {
    if (classpathFile.isEmpty()) {
      LOGGER.log(Level.INFO, "event=type-sidecar-unavailable language=java category=no-classpath");
      return Optional.empty();
    }
    final String classpath;
    try {
      classpath = Files.readString(classpathFile.get());
    } catch (IOException unreadable) {
      LOGGER.log(Level.WARNING, "event=type-sidecar-unavailable language=java category=classpath");
      return Optional.empty();
    }
    final Optional<Path> script = SidecarScripts.extract(projectRoot, wardVersion, SCRIPT);
    if (script.isEmpty()) {
      return Optional.empty();
    }
    final List<String> command = new ArrayList<>(List.of("java", script.get().toString()));
    command.add(projectRoot.toString());
    command.add(CLASSPATH_FLAG + classpathArgument(classpathFile.get()));
    for (final JavaSourceRoot root : JvmSourceLayouts.of(projectRoot, List.of(projectRoot))) {
      command.add(projectRoot.resolve(root.directory()).toString());
    }
    running = new ProcessTypeSidecar(LANGUAGE, command, projectRoot);
    return running
        .start()
        .map(facts -> new CachedTypeFacts(facts, CachedTypeFacts.sha256(classpath)));
  }

  /** The entries of the classpath file, made absolute and joined by the path separator. */
  private String classpathArgument(final Path file) {
    return JdtJavaDependencyResolver.classpathEntries(file).stream()
        .map(entry -> projectRoot.resolve(entry).toAbsolutePath().normalize().toString())
        .collect(Collectors.joining(File.pathSeparator));
  }

  @Override
  public void close() {
    if (running != null) {
      running.close();
    }
  }
}
