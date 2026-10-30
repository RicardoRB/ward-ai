package dev.ward.infrastructure.project;

import dev.ward.domain.config.WardException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Reads the distributions a {@code pyproject.toml} declares, under PEP 503 normalized names. */
public final class PyprojectDependencies {
  private static final Pattern NAME = Pattern.compile("^\\s*([A-Za-z0-9][A-Za-z0-9._-]*)");

  private PyprojectDependencies() {}

  /**
   * Returns the normalized names of every distribution {@code toml} declares: project dependencies,
   * optional dependencies, dependency groups and Poetry dependencies.
   *
   * @throws WardException if {@code toml} is not valid TOML
   */
  public static Set<String> declared(final String toml) {
    final TomlParseResult parsed = parse(toml);
    final Set<String> names = new HashSet<>();
    addRequirements(names, parsed.getArray("project.dependencies"));
    addGroups(names, parsed.getTable("project.optional-dependencies"));
    addGroups(names, parsed.getTable("dependency-groups"));
    addPoetry(names, parsed.getTable("tool.poetry.dependencies"));
    final TomlTable groups = parsed.getTable("tool.poetry.group");
    if (groups != null) {
      groups.keySet().forEach(group -> addPoetry(names, parsed.getTable(poetryGroup(group))));
    }
    return names;
  }

  /**
   * Returns each declared distribution in {@code toml} with its full requirement, by normalized
   * name.
   */
  public static Map<String, String> requirements(final String toml) {
    final Map<String, String> requirements = new HashMap<>();
    final TomlParseResult parsed = parse(toml);
    collect(requirements, parsed.getArray("project.dependencies"));
    final TomlTable groups = parsed.getTable("dependency-groups");
    if (groups != null) {
      groups.keySet().forEach(key -> collect(requirements, groups.getArray(key)));
    }
    return requirements;
  }

  private static void collect(final Map<String, String> requirements, final TomlArray items) {
    if (items == null) {
      return;
    }
    for (final Object item : items.toList()) {
      if (item instanceof String text) {
        nameOf(text).ifPresent(name -> requirements.putIfAbsent(name, text));
      }
    }
  }

  /** Returns the normalized name of a PEP 508 requirement, or empty when it has none. */
  public static Optional<String> nameOf(final String requirement) {
    final Matcher matcher = NAME.matcher(requirement);
    return matcher.find() ? Optional.of(normalize(matcher.group(1))) : Optional.empty();
  }

  /** Returns {@code name} as PEP 503 normalizes it. */
  public static String normalize(final String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[-_.]+", "-");
  }

  /**
   * Parses {@code toml}.
   *
   * @throws WardException if it is not valid TOML
   */
  public static TomlParseResult parse(final String toml) {
    final TomlParseResult parsed = Toml.parse(toml);
    if (parsed.hasErrors()) {
      throw new WardException(
          "pyproject.toml is not valid TOML: " + parsed.errors().getFirst().getMessage());
    }
    return parsed;
  }

  private static void addRequirements(final Set<String> names, final TomlArray requirements) {
    if (requirements == null) {
      return;
    }
    for (final Object item : requirements.toList()) {
      if (item instanceof String text) {
        nameOf(text).ifPresent(names::add);
      }
    }
  }

  private static void addGroups(final Set<String> names, final TomlTable groups) {
    if (groups == null) {
      return;
    }
    groups.keySet().forEach(key -> addRequirements(names, groups.getArray(key)));
  }

  private static void addPoetry(final Set<String> names, final TomlTable dependencies) {
    if (dependencies == null) {
      return;
    }
    dependencies.keySet().stream()
        .filter(name -> !"python".equals(name))
        .map(PyprojectDependencies::normalize)
        .forEach(names::add);
  }

  private static String poetryGroup(final String group) {
    return "tool.poetry.group." + group + ".dependencies";
  }
}
