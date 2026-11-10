package dev.ward.application.rules;

import java.util.Map;

/** Port that reads the top-level values of a rule body, which is TOML text. */
@FunctionalInterface
public interface RuleBodyReader {

  /** Returns the values of {@code body} by key, or an empty map when the body is not TOML. */
  Map<String, Object> values(String body);
}
