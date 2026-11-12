package dev.ward.domain.license;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The license a package declares, as written: one entry per declared license, which are
 * alternatives of each other (a choice of one), as for a dual-licensed package.
 *
 * @param entries the declared licenses; never empty
 */
public record DeclaredLicense(List<Entry> entries) {
  /** Copies the entries and checks that there is one. */
  public DeclaredLicense {
    entries = List.copyOf(entries);
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("A declared license needs at least one entry");
    }
  }

  /**
   * Creates a declaration of one name.
   *
   * @param name the declared name or identifier
   */
  public static DeclaredLicense of(final String name) {
    return new DeclaredLicense(List.of(new Entry(name, "")));
  }

  /** Returns the declared text for reports: the names, joined by {@code " | "}. */
  public String describe() {
    return entries.stream().map(Entry::describe).collect(Collectors.joining(" | "));
  }

  /**
   * One declared license.
   *
   * @param name the declared name or SPDX identifier, empty when only a URL is declared
   * @param url the declared license URL, empty when there is none
   */
  public record Entry(String name, String url) {
    /** Trims both texts and replaces a missing one by the empty string. */
    public Entry {
      name = name == null ? "" : name.trim();
      url = url == null ? "" : url.trim();
    }

    private String describe() {
      return name.isEmpty() ? url : name;
    }
  }
}
