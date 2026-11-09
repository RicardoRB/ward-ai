package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.Symbol;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Python unused-function and unused-class rules: a definition is exempt when it is a dunder, a
 * test, or the {@code main} entry point, and the files scanned exclude package roots, test modules
 * and setup scripts, whose definitions are found by name.
 */
final class PythonUnusedDefinitions implements UnusedDefinitionStrategy {

  /** The rule id for unused functions. */
  static final String FUNCTION_ID = "ward:python/unused-function";

  /** The rule id for unused classes. */
  static final String CLASS_ID = "ward:python/unused-class";

  /** The rule id for unused methods. */
  static final String METHOD_ID = "ward:python/unused-method";

  /** The rule id for unused properties. */
  static final String PROPERTY_ID = "ward:python/unused-property";

  /** The rule id for unused instance attributes. */
  static final String ATTRIBUTE_ID = "ward:python/unused-attribute";

  private static final Map<String, UnusedDefinitionKind> KINDS =
      Map.of(
          FUNCTION_ID, UnusedDefinitionKind.FUNCTION,
          CLASS_ID, UnusedDefinitionKind.CLASS,
          METHOD_ID, UnusedDefinitionKind.METHOD,
          PROPERTY_ID, UnusedDefinitionKind.UNUSED_PROPERTY,
          ATTRIBUTE_ID, UnusedDefinitionKind.ATTRIBUTE);
  private static final String PYTHON_SUFFIX = ".py";
  private static final String DUNDER = "__";
  private static final String TEST_PREFIX = "test";
  private static final String ENTRY_FUNCTION = "main";
  // Files whose definitions are found by name: package roots, test discovery and setup scripts.
  private static final Set<String> DISCOVERED_FILES =
      Set.of("__init__.py", "__main__.py", "conftest.py", "setup.py");

  @Override
  public Optional<UnusedDefinitionKind> kindOf(final String ruleId) {
    return Optional.ofNullable(KINDS.get(ruleId));
  }

  @Override
  public boolean scans(final String path) {
    final String name = path.substring(path.lastIndexOf('/') + 1);
    return path.endsWith(PYTHON_SUFFIX)
        && !DISCOVERED_FILES.contains(name)
        && !name.startsWith(TEST_PREFIX + "_")
        && !name.endsWith("_" + TEST_PREFIX + PYTHON_SUFFIX);
  }

  @Override
  public boolean exempt(final Symbol symbol) {
    final String name = symbol.name();
    return (name.startsWith(DUNDER) && name.endsWith(DUNDER))
        || name.startsWith(TEST_PREFIX)
        || ENTRY_FUNCTION.equals(name);
  }
}
