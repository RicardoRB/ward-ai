package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The global scope: the TypeScript library when the {@code typescript} package is installed, the
 * declaration files of the project that are scripts, {@code declare global} blocks, and the
 * {@code @types} packages. Without them a global name is unknown.
 */
final class Globals implements Lookup {
  private static final String TYPESCRIPT_LIB = "node_modules/typescript/lib/";
  private static final String TYPES_ROOT = "node_modules/@types";
  private static final List<String> LIB_ENTRIES = List.of("lib.esnext.full.d.ts", "lib.d.ts");
  private static final Pattern TYPES_FIELD =
      Pattern.compile("\"(?:types|typings)\"\\s*:\\s*\"([^\"]+)\"");

  private final Program program;
  private final Set<String> projectDeclarationFiles;
  private Map<String, Sym> symbols;
  private Map<String, List<Decl>> ambient;
  private Map<String, Module> ambientModules;

  Globals(final Program program, final Set<String> projectDeclarationFiles) {
    this.program = program;
    this.projectDeclarationFiles = Set.copyOf(projectDeclarationFiles);
  }

  @Override
  public Sym find(final String name) {
    return index().get(name);
  }

  @Override
  public Sym exported(final String name) {
    return find(name);
  }

  /** Returns the ambient module declared as {@code declare module "specifier"}, or null. */
  Module ambientModule(final String specifier) {
    index();
    final List<Decl> decls = ambient.get(specifier);
    if (decls == null) {
      return null;
    }
    return ambientModules.computeIfAbsent(
        specifier,
        key -> new Module(program, "module:" + key, FileDecls.EMPTY, decls, Module.Kind.AMBIENT));
  }

  private synchronized Map<String, Sym> index() {
    if (symbols == null) {
      symbols = new HashMap<>();
      ambient = new HashMap<>();
      ambientModules = new HashMap<>();
      for (final Module module : modules()) {
        module.names().forEach(name -> symbols.merge(name, module.own(name), Sym::join));
      }
    }
    return symbols;
  }

  private List<Module> modules() {
    final List<Module> modules = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    final List<String> queue = new ArrayList<>(libraryEntries());
    queue.addAll(projectDeclarationFiles.stream().sorted().toList());
    queue.addAll(typesEntries());
    for (int index = 0; index < queue.size(); index++) {
      final String path = queue.get(index);
      if (seen.add(path)) {
        load(path, modules, queue);
      }
    }
    return modules;
  }

  private void load(final String path, final List<Module> modules, final List<String> queue) {
    final FileDecls file = program.file(path);
    if (!file.isModule()) {
      modules.add(new Module(program, path, file, file.decls(), Module.Kind.GLOBAL));
    }
    final List<Decl> augmentation = file.modules().get(DeclReader.globalKey());
    if (augmentation != null) {
      modules.add(new Module(program, path + "#global", file, augmentation, Module.Kind.GLOBAL));
    }
    file.modules()
        .forEach(
            (specifier, decls) -> {
              if (!DeclReader.globalKey().equals(specifier)) {
                ambient.computeIfAbsent(specifier, key -> new ArrayList<>()).addAll(decls);
              }
            });
    for (final String reference : file.references()) {
      referenceTarget(path, reference).ifPresent(queue::add);
    }
  }

  private Optional<String> referenceTarget(final String from, final String reference) {
    final int colon = reference.indexOf(':');
    final String kind = reference.substring(0, colon);
    final String value = reference.substring(colon + 1);
    final String directory = from.contains("/") ? from.substring(0, from.lastIndexOf('/') + 1) : "";
    final String candidate =
        switch (kind) {
          case "lib" -> TYPESCRIPT_LIB + "lib." + value + ".d.ts";
          case "path" -> Paths.normalize(directory + value);
          default -> typesEntry(TYPES_ROOT + "/" + Program.typesName(value));
        };
    return program.exists(candidate) ? Optional.of(candidate) : Optional.empty();
  }

  private List<String> libraryEntries() {
    for (final String entry : LIB_ENTRIES) {
      if (program.exists(TYPESCRIPT_LIB + entry)) {
        return List.of(TYPESCRIPT_LIB + entry);
      }
    }
    return List.of();
  }

  private List<String> typesEntries() {
    return program.list(TYPES_ROOT).stream()
        .sorted()
        .map(name -> typesEntry(TYPES_ROOT + "/" + name))
        .filter(program::exists)
        .toList();
  }

  /** Returns the declaration file that the package at {@code directory} names as its types. */
  String typesEntry(final String directory) {
    final String manifest = program.read(directory + "/package.json").orElse("");
    final Matcher matcher = TYPES_FIELD.matcher(manifest);
    final String field = matcher.find() ? matcher.group(1) : "index.d.ts";
    return Paths.normalize(directory + "/" + field);
  }
}
