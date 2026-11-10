package dev.ward.infrastructure.rules;

import dev.ward.application.rules.RuleBodyReader;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/** Reads a rule body with the TOML reader. A body that is not TOML has no values. */
public final class TomlRuleBodyReader implements RuleBodyReader {

  /** Creates the reader; it holds no state. */
  @Inject
  public TomlRuleBodyReader() {
    // Stateless reader; Dagger constructs it through this constructor.
  }

  @Override
  public Map<String, Object> values(final String body) {
    final TomlParseResult toml = Toml.parse(body);
    return toml.hasErrors() ? Map.of() : new LinkedHashMap<>(toml.toMap());
  }
}
