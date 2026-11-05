package dev.ward.analysis.domain.format;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** The formatter for each language, immutable after construction. */
public final class FormatterRegistry {

  private final Map<AnalysisLanguage, SourceFormatter> formatters;

  /**
   * Copies the formatters, keyed by the language each one handles.
   *
   * @param formatters the formatters by language
   * @throws NullPointerException if the map or a key or value is missing
   * @throws IllegalArgumentException if a formatter handles a different language than its key
   */
  public FormatterRegistry(final Map<AnalysisLanguage, SourceFormatter> formatters) {
    final Map<AnalysisLanguage, SourceFormatter> copy = new EnumMap<>(AnalysisLanguage.class);
    for (final Map.Entry<AnalysisLanguage, SourceFormatter> entry : formatters.entrySet()) {
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
  public Optional<SourceFormatter> find(final AnalysisLanguage language) {
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

  private static SourceFormatter checkLanguage(
      final AnalysisLanguage key, final SourceFormatter formatter) {
    Objects.requireNonNull(key, "language");
    Objects.requireNonNull(formatter, "formatter");
    if (formatter.language() != key) {
      throw new IllegalArgumentException(
          "formatter for " + key + " handles " + formatter.language());
    }
    return formatter;
  }
}
