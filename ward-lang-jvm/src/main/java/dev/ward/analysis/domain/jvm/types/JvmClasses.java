package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The classes a unit can see: those of the unit itself, those of the project's other source files,
 * which are read from source on first use, and those of the libraries. A class found nowhere is
 * unknown, never an error.
 */
public final class JvmClasses {
  private static final Logger LOGGER = Logger.getLogger(JvmClasses.class.getName());

  private final Shared shared;
  private final Map<String, JvmClass> local;

  /**
   * Creates the classes of a project.
   *
   * @param library the compiled classes: the classpath and the JDK
   * @param sources the project's source files
   * @param frontend the parser for source files
   */
  public JvmClasses(
      final JvmLibrary library, final JvmSources sources, final LanguageFrontend frontend) {
    this(new Shared(library, sources, frontend), Map.of());
  }

  private JvmClasses(final Shared shared, final Map<String, JvmClass> local) {
    this.shared = shared;
    this.local = Map.copyOf(local);
  }

  /** Returns these classes with {@code declared}, the classes of the unit being analyzed, added. */
  JvmClasses withLocal(final Map<String, JvmClass> declared) {
    return new JvmClasses(shared, declared);
  }

  /** Returns the class with the binary name {@code name}. */
  Optional<JvmClass> find(final String name) {
    final JvmClass own = local.get(name);
    return own != null ? Optional.of(own) : shared.find(name);
  }

  /** Returns whether the class exists, without reading it when it is compiled. */
  boolean exists(final String name) {
    return local.containsKey(name) || shared.exists(name);
  }

  /** The state that every unit shares. */
  private static final class Shared {
    private final JvmLibrary library;
    private final JvmSources sources;
    private final LanguageFrontend frontend;
    private final Map<String, Map<String, JvmClass>> parsed = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final ReentrantLock lock = new ReentrantLock();

    Shared(final JvmLibrary library, final JvmSources sources, final LanguageFrontend frontend) {
      this.library = library;
      this.sources = sources;
      this.frontend = frontend;
    }

    Optional<JvmClass> find(final String name) {
      lock.lock();
      try {
        final Optional<JvmClass> source = Optional.ofNullable(declared(topLevel(name)).get(name));
        return source.isPresent() ? source : library.find(name);
      } finally {
        lock.unlock();
      }
    }

    boolean exists(final String name) {
      lock.lock();
      try {
        if (library.exists(name)) {
          return true;
        }
        final String top = topLevel(name);
        return top.equals(name)
            ? sources.declaring(top).isPresent()
            : declared(top).containsKey(name);
      } finally {
        lock.unlock();
      }
    }

    private static String topLevel(final String name) {
      final int nested = name.indexOf('$');
      return nested < 0 ? name : name.substring(0, nested);
    }

    private Map<String, JvmClass> declared(final String top) {
      if (parsed.containsKey(top)) {
        return parsed.get(top);
      }
      if (!loading.add(top)) {
        return Map.of();
      }
      try {
        final Map<String, JvmClass> found = sources.declaring(top).map(this::read).orElse(Map.of());
        parsed.put(top, found);
        return found;
      } finally {
        loading.remove(top);
      }
    }

    private Map<String, JvmClass> read(final SourceUnit unit) {
      if (LOGGER.isLoggable(Level.FINE)) {
        LOGGER.fine("event=jvm-source-class-read language=" + unit.language());
      }
      if (unit.language() == AnalysisLanguage.JAVA) {
        return JavaUnitModel.of(frontend.parse(unit).tree(), this::externalExists).declared();
      }
      if (unit.language() == AnalysisLanguage.KOTLIN) {
        return KotlinUnitModel.of(frontend.parse(unit).tree(), this::externalExists).declared();
      }
      return Map.of();
    }

    private boolean externalExists(final String name) {
      return exists(name);
    }
  }
}
