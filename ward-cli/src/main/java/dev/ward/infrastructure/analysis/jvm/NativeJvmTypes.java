package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.jvm.JavaScopeAnalysis;
import dev.ward.analysis.domain.jvm.KotlinScopeAnalysis;
import dev.ward.analysis.domain.jvm.types.JvmClasses;
import dev.ward.analysis.domain.jvm.types.NativeJvmTypeFacts;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.ClasspathBuiltinNames;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The type facts of a project's Java and Kotlin units, read natively from its sources, the jars of
 * its {@code java-classpath} and the JDK's {@code lib/modules}. It starts no process and needs no
 * JDK on the machine: without one, JDK types are unknown.
 */
public final class NativeJvmTypes implements TypeSidecar {
  private static final Logger LOGGER = Logger.getLogger(NativeJvmTypes.class.getName());
  private static final String CACHE_DIRECTORY = ".ward/cache/types";

  private final Path projectRoot;
  private final ReentrantLock lock = new ReentrantLock();
  private final AtomicReference<Running> running = new AtomicReference<>();

  /** The libraries that are open and the facts that read them. */
  private record Running(JvmClassLibraries libraries, TypeFacts facts) {}

  /** Creates the facts of the project at {@code projectRoot}. */
  public NativeJvmTypes(final Path projectRoot) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
  }

  /** Starts without a classpath: library types then resolve as unknown. */
  @Override
  public Optional<TypeFacts> start() {
    return start(Optional.empty());
  }

  @Override
  public Optional<TypeFacts> start(final Optional<Path> classpathFile) {
    lock.lock();
    try {
      if (running.get() == null) {
        running.set(open(classpathFile));
      }
      return Optional.of(running.get().facts());
    } finally {
      lock.unlock();
    }
  }

  private Running open(final Optional<Path> classpathFile) {
    String classpathText = "";
    List<String> entries = List.of();
    if (classpathFile.isPresent()) {
      try {
        classpathText = Files.readString(classpathFile.get());
        entries = JdtJavaDependencyResolver.classpathEntries(classpathFile.get());
      } catch (IOException | UncheckedIOException unreadable) {
        LOGGER.log(Level.WARNING, "event=native-types-classpath-unreadable");
      }
    }
    final UnaryOperator<String> environment = System::getenv;
    final JvmClassLibraries libraries =
        JvmClassLibraries.open(
            projectRoot,
            entries,
            JvmClassLibraries.jdkHome(
                Optional.ofNullable(environment.apply("JAVA_HOME")),
                Optional.ofNullable(System.getProperty("java.home"))),
            Optional.of(projectRoot.resolve(CACHE_DIRECTORY)));
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final JvmClasses classes =
        new JvmClasses(
            libraries,
            new ProjectJvmSources(
                projectRoot, JvmSourceLayouts.of(projectRoot, List.of(projectRoot))),
            frontend);
    final TypeFacts facts =
        new CachedTypeFacts(
            new NativeJvmTypeFacts(
                classes,
                frontend,
                Map.of(
                    AnalysisLanguage.JAVA,
                    new JavaScopeAnalysis(ClasspathBuiltinNames.java()),
                    AnalysisLanguage.KOTLIN,
                    new KotlinScopeAnalysis(ClasspathBuiltinNames.kotlin()))),
            CachedTypeFacts.sha256(classpathText));
    return new Running(libraries, facts);
  }

  @Override
  public void close() {
    final Running current = running.getAndSet(null);
    if (current != null) {
      current.libraries().close();
    }
  }
}
