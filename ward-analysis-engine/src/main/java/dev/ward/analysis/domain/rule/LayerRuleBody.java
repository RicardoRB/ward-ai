package dev.ward.analysis.domain.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The text form of layer boundaries in a rule body: each boundary is {@code from1,from2>forbidden1,
 * forbidden2}, and boundaries are separated by {@code ;}. Layer names cannot contain those
 * characters, so the form is unambiguous.
 */
public final class LayerRuleBody {
  private static final String BOUNDARY_SEPARATOR = ";";
  private static final String ARROW = ">";
  private static final String NAME_SEPARATOR = ",";
  private static final int ARROW_SIDES = 2;

  private LayerRuleBody() {}

  /** Returns the body text of {@code boundaries}, in order. */
  public static String encode(final List<LayerBoundary> boundaries) {
    return boundaries.stream()
        .map(
            boundary ->
                String.join(NAME_SEPARATOR, boundary.from())
                    + ARROW
                    + String.join(NAME_SEPARATOR, boundary.forbidden()))
        .collect(Collectors.joining(BOUNDARY_SEPARATOR));
  }

  /**
   * Reads boundaries from body text written by {@link #encode(List)}.
   *
   * @throws IllegalArgumentException if the text is not a list of boundaries
   */
  public static List<LayerBoundary> decode(final String body) {
    final List<LayerBoundary> boundaries = new ArrayList<>();
    for (final String text : body.split(BOUNDARY_SEPARATOR, -1)) {
      final String[] sides = text.split(ARROW, -1);
      if (sides.length != ARROW_SIDES) {
        throw new IllegalArgumentException("a layer boundary must be from>forbidden: " + text);
      }
      boundaries.add(new LayerBoundary(names(sides[0]), names(sides[1])));
    }
    return List.copyOf(boundaries);
  }

  private static List<String> names(final String text) {
    return List.of(text.split(NAME_SEPARATOR, -1));
  }
}
