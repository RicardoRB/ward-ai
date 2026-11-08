package dev.ward.analysis.domain.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The pnpm catalog entries of a {@code pnpm-workspace.yaml}: the {@code catalog:} section, which
 * names the {@value #DEFAULT} catalog, and the named sections under {@code catalogs:}. The reader
 * is line based and reads only the two-level shape pnpm documents; any other line is ignored.
 */
public final class NodeCatalogs {

  /** The name of the catalog that the {@code catalog:} section declares. */
  public static final String DEFAULT = "default";

  private static final String CATALOG = "catalog";
  private static final String CATALOGS = "catalogs";
  private static final String COLON = ":";
  private static final String COMMENT = " #";

  /**
   * One dependency that a catalog pins.
   *
   * @param catalog the catalog name, {@value #DEFAULT} for the {@code catalog:} section
   * @param dependency the dependency name, without quotes
   * @param version the version range as written, without quotes
   * @param line the 1-based line where the dependency is written
   * @param column the 1-based column where the dependency name starts
   */
  public record CatalogEntry(
      String catalog, String dependency, String version, int line, int column) {

    /** Validates the parts. @throws NullPointerException if a text part is missing */
    public CatalogEntry {
      Objects.requireNonNull(catalog, "catalog");
      Objects.requireNonNull(dependency, "dependency");
      Objects.requireNonNull(version, "version");
    }
  }

  private NodeCatalogs() {}

  /** Returns the catalog entries of {@code text}, in file order. */
  public static List<CatalogEntry> parse(final String text) {
    Objects.requireNonNull(text, "text");
    final List<CatalogEntry> entries = new ArrayList<>();
    String top = null;
    String named = null;
    int childIndent = -1;
    final String[] lines = text.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      final String raw = stripComment(lines[i]);
      if (raw.isBlank()) {
        continue;
      }
      final int indent = raw.length() - raw.stripLeading().length();
      final String content = raw.strip();
      final int line = i + 1;
      if (indent == 0) {
        top = header(content, CATALOG, CATALOGS);
        named = null;
        childIndent = -1;
        continue;
      }
      if (CATALOG.equals(top)) {
        addEntry(entries, DEFAULT, content, line, indent);
      } else if (CATALOGS.equals(top)) {
        if (childIndent < 0) {
          childIndent = indent;
        }
        if (indent == childIndent && content.endsWith(COLON)) {
          named = unquote(content.substring(0, content.length() - 1));
        } else if (indent > childIndent && named != null) {
          addEntry(entries, named, content, line, indent);
        }
      }
    }
    return List.copyOf(entries);
  }

  private static String header(final String content, final String... names) {
    if (!content.endsWith(COLON)) {
      return null;
    }
    final String key = content.substring(0, content.length() - 1);
    for (final String name : names) {
      if (name.equals(key)) {
        return name;
      }
    }
    return null;
  }

  private static void addEntry(
      final List<CatalogEntry> entries,
      final String catalog,
      final String content,
      final int line,
      final int indent) {
    final int colon = content.indexOf(COLON);
    if (colon <= 0) {
      return;
    }
    final String name = unquote(content.substring(0, colon).strip());
    final String version = unquote(content.substring(colon + 1).strip());
    if (!name.isEmpty() && !version.isEmpty()) {
      entries.add(new CatalogEntry(catalog, name, version, line, indent + 1));
    }
  }

  private static String stripComment(final String line) {
    final String trimmed = line.stripLeading();
    if (trimmed.startsWith("#")) {
      return "";
    }
    final int at = line.indexOf(COMMENT);
    return at < 0 ? line : line.substring(0, at);
  }

  private static String unquote(final String text) {
    if (text.length() >= 2
        && (text.charAt(0) == '"' || text.charAt(0) == '\'')
        && text.charAt(text.length() - 1) == text.charAt(0)) {
      return text.substring(1, text.length() - 1);
    }
    return text;
  }
}
