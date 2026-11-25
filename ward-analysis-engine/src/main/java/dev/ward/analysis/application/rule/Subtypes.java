package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.SourceUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds, by the text of the Java units, which files name a type after {@code extends} or {@code
 * implements}, and which files bind a type from the request with a {@code @RequestBody} or
 * {@code @ModelAttribute} parameter (the getters of such a type are assumed sources, see the taint
 * {@code BoundGetters}), by the simple name of that type. It over-approximates (a comment can match), which
 * only widens the files a taint run reads; the supertype graphs of the lowering decide the dispatch.
 */
final class Subtypes {
  private static final String JAVA = ".java";
  private static final Pattern CLAUSE =
      Pattern.compile(
          "\\b(?:extends|implements)\\s+([\\w$.,\\s<>?]+?)\\s*(?:\\{|\\bimplements\\b|\\bpermits\\b)"
      );
  private static final Pattern BINDING =
      Pattern.compile(
          "@(?:RequestBody|ModelAttribute)\\b(?:\\([^)]*\\))?\\s+(?:(?:@[\\w.]+(?:\\([^)]*\\))?|final)\\s+)*"
              + "([\\w$]+)"
      );
  private static final Pattern NAME = Pattern.compile("([\\w$]+)\\s*(?:<[^>]*>)?\\s*(?:,|$)");

  private Subtypes() {}

  /** Maps each simple supertype name to the sorted paths of the files that extend or implement it. */
  static Map<String, Set<String>> index(final Map<String, SourceUnit> units) {
    final Map<String, Set<String>> found = new HashMap<>();
    for (final SourceUnit unit : units.values()) {
      if (unit.path().endsWith(JAVA)) {
        names(unit.text()).forEach(name -> add(found, name, unit.path()));
        bound(unit.text()).forEach(name -> add(found, name, unit.path()));
      }
    }
    return found;
  }

  private static void add(
      final Map<String,
      Set<String>> found,
      final String name,
      final String path
  ) {
    found.computeIfAbsent(name, key -> new TreeSet<>()).add(path);
  }

  private static Set<String> bound(final String text) {
    final Set<String> names = new TreeSet<>();
    final Matcher binding = BINDING.matcher(text);
    while (binding.find()) {
      names.add(binding.group(1));
    }
    return names;
  }

  private static Set<String> names(final String text) {
    final Set<String> names = new TreeSet<>();
    final Matcher clause = CLAUSE.matcher(text);
    while (clause.find()) {
      final Matcher name = NAME.matcher(stripGenerics(clause.group(1)));
      while (name.find()) {
        names.add(name.group(1));
      }
    }
    return names;
  }

  private static String stripGenerics(final String written) {
    String text = written;
    String previous;
    do {
      previous = text;
      text = text.replaceAll("<[^<>]*>", "");
    } while (!text.equals(previous));
    return text.replaceAll("\\s+", "").replaceAll("[\\w$]+\\.", "");
  }

  /** The last segment of a declared type name, so {@code Outer.Inner} is {@code Inner}. */
  static String simple(final String declared) {
    return declared.substring(declared.lastIndexOf('.') + 1);
  }
}
