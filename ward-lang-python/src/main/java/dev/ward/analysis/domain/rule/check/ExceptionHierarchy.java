package dev.ward.analysis.domain.rule.check;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The builtin exception classes and their direct base classes, from the exception hierarchy of the
 * Python 3.14 documentation, with the aliases that name a builtin class. A class of the module
 * shadows a builtin of the same name.
 */
final class ExceptionHierarchy {

  private static final Map<String, List<String>> BUILTIN_PARENTS = parents();
  private static final Map<String, String> ALIASES =
      Map.of("EnvironmentError", "OSError", "IOError", "OSError");

  private ExceptionHierarchy() {}

  /** The name a builtin alias stands for, or the name itself. */
  static String canonical(final String name) {
    return ALIASES.getOrDefault(name, name);
  }

  /**
   * Every base class of {@code name}, transitively, by the module's own classes first and then the
   * builtin table. A name that is neither gives no base class.
   */
  static Set<String> ancestors(final String name, final Map<String, List<String>> moduleBases) {
    final Set<String> ancestors = new HashSet<>();
    final Deque<String> pending = new ArrayDeque<>(parentsOf(canonical(name), moduleBases));
    while (!pending.isEmpty()) {
      final String current = canonical(pending.pop());
      if (ancestors.add(current)) {
        pending.addAll(parentsOf(current, moduleBases));
      }
    }
    return ancestors;
  }

  private static List<String> parentsOf(
      final String name, final Map<String, List<String>> moduleBases) {
    return moduleBases.getOrDefault(name, BUILTIN_PARENTS.getOrDefault(name, List.of()));
  }

  private static Map<String, List<String>> parents() {
    final Map<String, List<String>> parents = new java.util.HashMap<>();
    parents.put("BaseException", List.of());
    for (final String base :
        List.of(
            "BaseExceptionGroup",
            "GeneratorExit",
            "KeyboardInterrupt",
            "SystemExit",
            "Exception")) {
      parents.put(base, List.of("BaseException"));
    }
    parents.put("ExceptionGroup", List.of("BaseExceptionGroup", "Exception"));
    addAll(
        parents,
        "Exception",
        "ArithmeticError",
        "AssertionError",
        "AttributeError",
        "BufferError",
        "EOFError",
        "ImportError",
        "LookupError",
        "MemoryError",
        "NameError",
        "OSError",
        "ReferenceError",
        "RuntimeError",
        "StopAsyncIteration",
        "StopIteration",
        "SyntaxError",
        "SystemError",
        "TypeError",
        "ValueError",
        "Warning");
    addAll(parents, "ArithmeticError", "FloatingPointError", "OverflowError", "ZeroDivisionError");
    addLeafParents(parents);
    return Map.copyOf(parents);
  }

  /** The builtin classes below the second level of the table. */
  private static void addLeafParents(final Map<String, List<String>> parents) {
    addAll(parents, "ImportError", "ModuleNotFoundError");
    addAll(parents, "LookupError", "IndexError", "KeyError");
    addAll(parents, "NameError", "UnboundLocalError");
    addAll(
        parents,
        "OSError",
        "BlockingIOError",
        "ChildProcessError",
        "ConnectionError",
        "FileExistsError",
        "FileNotFoundError",
        "InterruptedError",
        "IsADirectoryError",
        "NotADirectoryError",
        "PermissionError",
        "ProcessLookupError",
        "TimeoutError");
    addAll(
        parents,
        "ConnectionError",
        "BrokenPipeError",
        "ConnectionAbortedError",
        "ConnectionRefusedError",
        "ConnectionResetError");
    addAll(
        parents,
        "RuntimeError",
        "NotImplementedError",
        "PythonFinalizationError",
        "RecursionError");
    addAll(parents, "SyntaxError", "IndentationError");
    addAll(parents, "IndentationError", "TabError");
    addAll(parents, "ValueError", "UnicodeError");
    addAll(
        parents,
        "UnicodeError",
        "UnicodeDecodeError",
        "UnicodeEncodeError",
        "UnicodeTranslateError");
    addAll(
        parents,
        "Warning",
        "BytesWarning",
        "DeprecationWarning",
        "EncodingWarning",
        "FutureWarning",
        "ImportWarning",
        "PendingDeprecationWarning",
        "ResourceWarning",
        "RuntimeWarning",
        "SyntaxWarning",
        "UnicodeWarning",
        "UserWarning");
  }

  /** Records each name as a direct subclass of {@code base}. */
  private static void addAll(
      final Map<String, List<String>> parents, final String base, final String... names) {
    for (final String name : names) {
      parents.put(name, List.of(base));
    }
  }
}
