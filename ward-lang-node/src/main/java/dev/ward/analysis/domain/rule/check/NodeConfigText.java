package dev.ward.analysis.domain.rule.check;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the packages that a tsconfig or an Angular workspace config names, from its text: the
 * packages it extends, its language-service plugins and JSX import source, its Angular builders,
 * and the tslib that {@code importHelpers} uses. Each is knip's rule for the package it names.
 */
final class NodeConfigText {

  private static final Pattern IMPORT_HELPERS = Pattern.compile("\"importHelpers\"\\s*:\\s*true");
  private static final Pattern EXTENDS =
      Pattern.compile("\"extends\"\\s*:\\s*(\\[[^\\]]*\\]|\"[^\"]*\")");
  private static final Pattern PLUGINS = Pattern.compile("\"plugins\"\\s*:\\s*\\[([^\\]]*)\\]");
  private static final Pattern PLUGIN_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern JSX_IMPORT_SOURCE =
      Pattern.compile("\"jsxImportSource\"\\s*:\\s*\"([^\"]+)\"");

  /** The package of an Angular builder, as {@code "builder": "@angular/build:application"}. */
  private static final Pattern BUILDER = Pattern.compile("\"builder\"\\s*:\\s*\"([^\":]+):");

  private static final Pattern QUOTED = Pattern.compile("[\"']([^\"'\\s]+)[\"']");

  private NodeConfigText() {}

  /** Adds the packages a tsconfig names: typescript, tslib, extended and plugin packages. */
  static void addTsconfig(final String text, final Set<String> implied) {
    implied.add("typescript");
    if (IMPORT_HELPERS.matcher(text).find()) {
      implied.add("tslib");
    }
    extendedPackages(text).forEach(implied::add);
    pluginPackages(text).forEach(implied::add);
    firstGroup(JSX_IMPORT_SOURCE, text)
        .map(NodeConfigText::packageOf)
        .filter(name -> !name.isEmpty())
        .ifPresent(implied::add);
  }

  /** Adds the package of each Angular builder a workspace config names. */
  static void addAngular(final String text, final Set<String> implied) {
    allMatches(BUILDER, text).stream().map(NodeConfigText::packageOf).forEach(implied::add);
  }

  /**
   * A tsconfig implies typescript, and tslib when it sets {@code importHelpers}, which knip's
   * typescript plugin reads as tslib in use. A tsconfig that extends another is read only in its
   * own folder: shortcut: an {@code importHelpers} set in a parent folder's tsconfig is missed,
   * upgrade by following {@code extends}.
   */
  /** The packages a tsconfig {@code extends} names, such as {@code @vue/tsconfig}. */
  private static Set<String> extendedPackages(final String text) {
    final Set<String> packages = new HashSet<>();
    firstGroup(EXTENDS, text)
        .ifPresent(
            value ->
                QUOTED
                    .matcher(value)
                    .results()
                    .map(match -> packageOf(match.group(1)))
                    .filter(name -> !name.isEmpty())
                    .forEach(packages::add));
    return packages;
  }

  /** The packages a tsconfig's language-service {@code plugins} name, such as {@code next}. */
  private static Set<String> pluginPackages(final String text) {
    final Set<String> packages = new HashSet<>();
    firstGroup(PLUGINS, text)
        .ifPresent(
            list ->
                PLUGIN_NAME
                    .matcher(list)
                    .results()
                    .map(match -> packageOf(match.group(1)))
                    .filter(name -> !name.isEmpty())
                    .forEach(packages::add));
    return packages;
  }

  /** The package a specifier names, or empty for a relative or absolute path. */
  private static String packageOf(final String specifier) {
    if (specifier.startsWith(".") || specifier.startsWith("/")) {
      return "";
    }
    final String[] parts = specifier.split("/");
    if (specifier.startsWith("@") && parts.length >= 2) {
      return parts[0] + "/" + parts[1];
    }
    return parts[0];
  }

  /** The first group of every match of {@code pattern} in {@code text}. */
  private static List<String> allMatches(final Pattern pattern, final String text) {
    return pattern.matcher(text).results().map(match -> match.group(1)).toList();
  }

  private static Optional<String> firstGroup(final Pattern pattern, final String text) {
    final Matcher match = pattern.matcher(text);
    return match.find() ? Optional.of(match.group(1)) : Optional.empty();
  }
}
