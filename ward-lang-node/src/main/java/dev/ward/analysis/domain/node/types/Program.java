package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The declarations a project can see: its own files, the declaration files of the packages it
 * imports and the TypeScript library. Files are parsed when first needed, once per run, and the
 * index of a file in {@code node_modules} is kept between runs under the hash of its package.
 */
public final class Program {
  private static final Logger LOGGER = Logger.getLogger(Program.class.getName());
  private static final String NODE_MODULES = "node_modules";

  private final DeclarationFiles files;
  private final DeclarationCache cache;
  private final LanguageFrontend frontend;
  private final BiFunction<String, String, Optional<String>> projectResolver;
  private final Globals globals;
  private final PackageLocator locator;
  private final ReentrantLock lock = new ReentrantLock();
  private final Map<String, FileDecls> parsed = new HashMap<>();
  private final Map<String, Module> modules = new HashMap<>();

  /**
   * Creates the program of a project.
   *
   * @param files where files are read from
   * @param cache where indexes of packages are kept
   * @param frontend the parser
   * @param projectFiles the TypeScript and JavaScript files of the project, as the layout lists
   *     them
   * @param projectResolver resolves a specifier written in a project file to a project file
   */
  public Program(
      final DeclarationFiles files,
      final DeclarationCache cache,
      final LanguageFrontend frontend,
      final Set<String> projectFiles,
      final BiFunction<String, String, Optional<String>> projectResolver) {
    this.files = files;
    this.cache = cache;
    this.frontend = frontend;
    this.projectResolver = projectResolver;
    this.locator = new PackageLocator(files);
    this.globals =
        new Globals(
            this,
            projectFiles.stream()
                .filter(path -> path.endsWith(".d.ts") && !path.contains(NODE_MODULES + "/"))
                .collect(java.util.stream.Collectors.toSet()));
  }

  Globals globals() {
    return globals;
  }

  DeclarationFiles files() {
    return files;
  }

  /** Returns the index of the file at {@code path}, which is empty when the file is unreadable. */
  FileDecls file(final String path) {
    lock.lock();
    try {
      final FileDecls known = parsed.get(path);
      if (known != null) {
        return known;
      }
      final FileDecls loaded = load(path);
      parsed.put(path, loaded);
      return loaded;
    } finally {
      lock.unlock();
    }
  }

  private FileDecls load(final String path) {
    final String key = cacheKey(path);
    if (key != null) {
      final Optional<FileDecls> cached = cache.load(key).flatMap(DeclCodec::decode);
      if (cached.isPresent()) {
        return cached.get();
      }
    }
    final Optional<String> text = files.read(path);
    if (text.isEmpty()) {
      return FileDecls.EMPTY;
    }
    final FileDecls decls = parse(path, text.get());
    if (key != null) {
      cache.store(key, DeclCodec.encode(decls));
    }
    return decls;
  }

  private FileDecls parse(final String path, final String text) {
    final AnalysisLanguage language =
        path.matches(".*\\.[cm]?jsx?$") ? AnalysisLanguage.JAVASCRIPT : AnalysisLanguage.TYPESCRIPT;
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=declaration-file-parsed language=" + language);
    }
    return DeclReader.read(frontend.parse(new SourceUnit(path, language, text)).tree(), text);
  }

  /** The key of a file of a package, which changes when the package does, or null for no cache. */
  private String cacheKey(final String path) {
    final String directory = PackageLocator.packageDirectory(path);
    if (directory == null) {
      return null;
    }
    return files.packageHash(directory).map(hash -> hash + ":" + path).orElse(null);
  }

  /** Returns the scope of the file at {@code path}. */
  Module fileModule(final String path) {
    lock.lock();
    try {
      final FileDecls decls = file(path);
      return modules.computeIfAbsent(
          path, key -> new Module(this, key, decls, decls.decls(), Module.Kind.FILE));
    } finally {
      lock.unlock();
    }
  }

  /** Returns the scope of a unit whose text was given rather than read. */
  Module unitModule(final String path, final FileDecls decls) {
    return new Module(this, path, decls, decls.decls(), Module.Kind.FILE);
  }

  /** Returns the module that {@code specifier}, written in file {@code from}, names, or null. */
  Module moduleFor(final String from, final String specifier) {
    final boolean relative = specifier.startsWith(".");
    final Optional<String> inProject = projectResolver.apply(from, specifier);
    if (inProject.isPresent()) {
      return fileModule(inProject.get());
    }
    if (relative) {
      return locator.relative(from, specifier).map(this::fileModule).orElse(null);
    }
    final Optional<String> installed = locator.installed(from, specifier);
    return installed.isPresent()
        ? fileModule(installed.get())
        : globals.ambientModule(PackageLocator.bare(specifier));
  }
}
