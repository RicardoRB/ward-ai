package dev.ward.analysis.domain.jvm;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The type of a {@code val} with no declared type whose value is a call of a well-known static
 * factory, such as {@code val log = LoggerFactory.getLogger(Service::class.java)}, the usual way a
 * Kotlin class holds its logger. The type is what a taint specification names as the receiver of a
 * sink, so a call on the property is matched like a call on a typed Java field.
 *
 * <p>shortcut: a fixed table of logging factories; upgrade with return types read from the
 * dependency when another factory costs a finding.
 */
final class KotlinFactoryTypes {
  private static final Pattern CALL =
      Pattern.compile("^(?<owner>[A-Za-z_][\\w.]*)\\.(?<name>\\w+)\\(");
  private static final Map<String, String> RETURNS =
      Map.of(
          "org.slf4j.LoggerFactory#getLogger",
          "org.slf4j.Logger",
          "java.util.logging.Logger#getLogger",
          "java.util.logging.Logger",
          "org.apache.logging.log4j.LogManager#getLogger",
          "org.apache.logging.log4j.Logger",
          "org.apache.commons.logging.LogFactory#getLog",
          "org.apache.commons.logging.Log"
      );

  private KotlinFactoryTypes() {}

  /**
   * The qualified type that a factory call returns.
   *
   * @param expression the text of the initializer
   * @param types the imports of the file, which give the factory its qualified name
   * @return the qualified return type, or empty when the call is not a known factory
   */
  static Optional<String> returnType(final String expression, final JavaTypeNames types) {
    final Matcher call = CALL.matcher(expression.replaceAll("\\s", ""));
    if (!call.find()) {
      return Optional.empty();
    }
    return types
        .resolve(call.group("owner"))
        .map(owner -> RETURNS.get(owner + "#" + call.group("name")));
  }
}
