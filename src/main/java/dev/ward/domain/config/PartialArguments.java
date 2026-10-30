package dev.ward.domain.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Renders the {@code args} and {@code item} of a partial check from the edited paths. */
final class PartialArguments {
  private static final String PATHS = "{paths}";
  private static final String PATH = "{path}";
  private static final String NAME = "{name}";
  private static final String PACKAGE = "{package}";

  private PartialArguments() {}

  /**
   * Renders one item per edited path, dropping a path that no package holds for a {@code {package}}
   * item and passing each rendered item once.
   */
  static List<String> items(
      final String item,
      final List<EditedPath> paths,
      final Function<Path, Optional<String>> packages) {
    return paths.stream()
        .map(path -> renderItem(item, path, packages))
        .flatMap(Optional::stream)
        .distinct()
        .toList();
  }

  private static Optional<String> renderItem(
      final String item, final EditedPath path, final Function<Path, Optional<String>> packages) {
    if (!item.contains(PACKAGE)) {
      return Optional.of(fill(item, path));
    }
    return packages.apply(path.absolute()).map(name -> fill(item, path).replace(PACKAGE, name));
  }

  private static String fill(final String item, final EditedPath path) {
    return item.replace(PATH, path.relative()).replace(NAME, path.name());
  }

  static List<String> render(final PartialGateConfiguration partial, final List<String> items) {
    final List<String> rendered = new ArrayList<>();
    for (final String argument : partial.args()) {
      if (argument.contains(PATHS)) {
        rendered.addAll(substituted(argument, items, partial.separator()));
      } else {
        rendered.add(argument);
      }
    }
    return rendered;
  }

  /** One argument per item, or one argument joining the items with {@code separator}. */
  private static List<String> substituted(
      final String argument, final List<String> items, final String separator) {
    if (separator == null) {
      return items.stream().map(value -> argument.replace(PATHS, value)).toList();
    }
    return List.of(argument.replace(PATHS, String.join(separator, items)));
  }
}
