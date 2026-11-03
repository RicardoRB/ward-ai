package dev.ward.analysis.domain.rule.metric;

import java.util.Arrays;
import java.util.Optional;

/** The measurements a metric rule can take, by the key that names them in a rule body. */
public enum Measurement {
  /** Lines from the first to the last line of the node. */
  LINES("lines", new LineCount()),
  /** Parameters of a function, not counting the receiver of a method. */
  PARAMETERS("parameters", new ParameterCount()),
  /** Cyclomatic complexity: one plus the decision points of the node. */
  DECISION_POINTS("decision-points", new DecisionCount()),
  /** Whether a declared name follows the style its node kind requires. */
  IDENTIFIER_CASE("identifier-case", new IdentifierCase());

  private final String key;
  private final Measure measure;

  Measurement(final String key, final Measure measure) {
    this.key = key;
    this.measure = measure;
  }

  /** Returns the measurement with {@code key}, if there is one. */
  static Optional<Measurement> byKey(final String key) {
    return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
  }

  Measure measure() {
    return measure;
  }
}
