package dev.ward.analysis.domain.format;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** The formatter and its parser for each language, immutable after construction. */
public final class FormatterRegistry {

  private final Map<AnalysisLanguage, FormatterEntry> formatters;

  /**
   * Copies the entries, keyed by the language each formatter handles.
   *
   * @param formatters the formatters and parsers by language
   * @throws NullPointerException if the map or a key or value is missing
   * @throws IllegalArgumentException if a formatter handles a different language than its key
   */
  public FormatterRegistry(final Map<AnalysisLanguage, FormatterEntry> formatters) {
    final Map<AnalysisLanguage, FormatterEntry> copy = new EnumMap<>(AnalysisLanguage.class);
    for (final Map.Entry<AnalysisLanguage, FormatterEntry> entry : formatters.entrySet()) {
      copy.put(entry.getKey(), checkLanguage(entry.getKey(), entry.getValue()));
    }
    this.formatters = Collections.unmodifiableMap(copy);
  }

  /**
   * Finds the formatter for a language.
   *
   * @param language the language
   * @return the formatter, or empty when the language has none
   */
  public Optional<FormatterEntry> find(final AnalysisLanguage language) {
    return Optional.ofNullable(formatters.get(Objects.requireNonNull(language, "language")));
  }

  /**
   * Lists the languages that have a formatter.
   *
   * @return the languages, in enum order
   */
  public Set<AnalysisLanguage> languages() {
    return formatters.keySet();
  }

  private static FormatterEntry checkLanguage(
      final AnalysisLanguage key, final FormatterEntry entry) {
    Objects.requireNonNull(key, "language");
    Objects.requireNonNull(entry, "entry");
    if (entry.formatter().language() != key) {
      throw new IllegalArgumentException(
          "formatter for " + key + " handles " + entry.formatter().language());
    }
    return entry;
  }
}
