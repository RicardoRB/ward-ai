package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The declarations a project can see: its own files, the declaration files of the packages it
 * imports and the TypeScript library. Files are parsed when first needed, once per run, and the
 * index of a file in {@code node_modules} is kept between runs under the hash of its package.
 */
public final class Program {
  private static final Logger LOGGER = Logger.getLogger(Program.class.getName());
  private static final String NODE_MODULES = "node_modules";
  private static final Pattern ENTRY_FIELD =
      Pattern.compile("\"(?:types|typings)\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern MAIN_FIELD = Pattern.compile("\"main\"\\s*:\\s*\"([^\"]+)\"");
  private static final List<String> SUFFIXES =
      List.of(".d.ts", ".ts", ".tsx", "/index.d.ts", "/index.ts", ".d.mts", ".d.cts");

  private final DeclarationFiles files;
  private final DeclarationCache cache;
  private final LanguageFrontend frontend;
  private final BiFunction<String, String, Optional<String>> projectResolver;
  private final Globals globals;
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

  boolean exists(final String path) {
    return files.exists(path);
  }

  Optional<String> read(final String path) {
    return files.read(path);
  }

  List<String> list(final String directory) {
    return files.list(directory);
  }

  /** Returns the name of the {@code @types} directory of a package. */
  static String typesName(final String packageName) {
    return packageName.startsWith("@") ? packageName.substring(1).replace("/", "__") : packageName;
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
    final String directory = packageDirectory(path);
    if (directory == null) {
      return null;
    }
    return files.packageHash(directory).map(hash -> hash + ":" + path).orElse(null);
  }

  private static String packageDirectory(final String path) {
    final int marker = path.lastIndexOf(NODE_MODULES + "/");
    if (marker < 0) {
      return null;
    }
    final String[] segments = path.substring(marker + NODE_MODULES.length() + 1).split("/");
    final int length = segments.length > 0 && segments[0].startsWith("@") ? 2 : 1;
    if (segments.length <= length) {
      return null;
    }
    return path.substring(0, marker)
        + NODE_MODULES
        + "/"
        + String.join("/", List.of(segments).subList(0, length));
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
      return relativeDeclaration(from, specifier);
    }
    final Optional<String> installed = installed(from, specifier);
    return installed.isPresent()
        ? fileModule(installed.get())
        : globals.ambientModule(bare(specifier));
  }

  private static String bare(final String specifier) {
    return specifier.startsWith("node:") ? specifier.substring("node:".length()) : specifier;
  }

  private Module relativeDeclaration(final String from, final String specifier) {
    final String directory = from.contains("/") ? from.substring(0, from.lastIndexOf('/') + 1) : "";
    final String base = Paths.normalize(directory + specifier);
    return firstExisting(base).map(this::fileModule).orElse(null);
  }

  private Optional<String> firstExisting(final String base) {
    if (files.exists(base) && base.matches(".*\\.[cm]?[jt]sx?$")) {
      return Optional.of(base);
    }
    for (final String suffix : SUFFIXES) {
      if (files.exists(base + suffix)) {
        return Optional.of(base + suffix);
      }
    }
    return Optional.empty();
  }

  /** Finds the declaration file of a package under the nearest {@code node_modules}. */
  private Optional<String> installed(final String from, final String specifier) {
    final String[] segments = specifier.split("/");
    final int length = specifier.startsWith("@") ? 2 : 1;
    if (segments.length < length) {
      return Optional.empty();
    }
    final String name = String.join("/", List.of(segments).subList(0, length));
    final String subpath = String.join("/", List.of(segments).subList(length, segments.length));
    String directory = from.contains("/") ? from.substring(0, from.lastIndexOf('/')) : "";
    while (true) {
      final String root = directory.isEmpty() ? NODE_MODULES : directory + "/" + NODE_MODULES;
      final Optional<String> own = entry(root + "/" + name, subpath);
      if (own.isPresent()) {
        return own;
      }
      final Optional<String> types = entry(root + "/@types/" + typesName(name), subpath);
      if (types.isPresent()) {
        return types;
      }
      if (directory.isEmpty()) {
        return Optional.empty();
      }
      directory = directory.contains("/") ? directory.substring(0, directory.lastIndexOf('/')) : "";
    }
  }

  private Optional<String> entry(final String packageDirectory, final String subpath) {
    if (!files.exists(packageDirectory + "/package.json")
        && !files.exists(packageDirectory + "/index.d.ts")
        && subpath.isEmpty()) {
      return Optional.empty();
    }
    if (!subpath.isEmpty()) {
      return firstExisting(packageDirectory + "/" + subpath);
    }
    final String manifest = files.read(packageDirectory + "/package.json").orElse("");
    final Matcher types = ENTRY_FIELD.matcher(manifest);
    if (types.find()) {
      final String named = Paths.normalize(packageDirectory + "/" + types.group(1));
      if (files.exists(named)) {
        return Optional.of(named);
      }
    }
    final Matcher main = MAIN_FIELD.matcher(manifest);
    if (main.find()) {
      final String stem =
          Paths.normalize(packageDirectory + "/" + main.group(1)).replaceAll("\\.[cm]?jsx?$", "");
      final Optional<String> declared = firstExisting(stem);
      if (declared.isPresent()) {
        return declared;
      }
    }
    return firstExisting(packageDirectory + "/index");
  }
}
