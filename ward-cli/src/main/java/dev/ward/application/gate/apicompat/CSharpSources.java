package dev.ward.application.gate.apicompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Finds the C# projects of a tree and the {@code .cs} files below them. A project is a directory
 * with a {@code .csproj} file; its {@code bin} and {@code obj} directories hold build output, not
 * sources.
 */
final class CSharpSources {
  private static final String PROJECT = ".csproj";
  private static final String SOURCE = ".cs";

  private CSharpSources() {}

  /** The project directories, with {@code ""} for the root, and the source files below them. */
  record Found(List<String> modules, List<String> files) {
    Found {
      modules = List.copyOf(modules);
      files = List.copyOf(files);
    }
  }

  /** Reads the tree below the root through {@code files}, which lists a directory recursively. */
  static Found find(final Function<String, List<String>> files) {
    final List<String> all = files.apply("");
    final List<String> modules = new ArrayList<>();
    all.stream()
        .filter(file -> file.endsWith(PROJECT))
        .map(CSharpSources::parent)
        .distinct()
        .sorted()
        .forEach(modules::add);
    final List<String> sources =
        all.stream()
            .filter(file -> file.endsWith(SOURCE) && !hasBuildOutput(file))
            .filter(file -> modules.stream().anyMatch(module -> inside(file, module)))
            .toList();
    return new Found(modules, sources);
  }

  private static boolean hasBuildOutput(final String file) {
    return ("/" + file).contains("/bin/") || ("/" + file).contains("/obj/");
  }

  private static boolean inside(final String file, final String module) {
    return module.isEmpty() || file.startsWith(module + "/");
  }

  private static String parent(final String file) {
    final int slash = file.lastIndexOf('/');
    return slash < 0 ? "" : file.substring(0, slash);
  }
}
