package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the JDK documents about methods that may return null, as a table written from the Javadoc.
 * A call is matched by the simple name of its receiver's declared type, the method name and the
 * argument count, so only a receiver that this file declares or names (such as {@code System}) is
 * known; any other receiver is left alone.
 */
final class NullLibrary {
  private static final String CLASS = "Class";
  private static final String CLASS_LITERAL = ".class";
  private static final String EXCEPTION = "Exception";
  private static final String ERROR = "Error";
  private static final String GET = "get/1";
  private static final String GET_PROPERTY = "getProperty/1";

  private static final List<Model> MODELS =
      List.of(
          new Model(
              Set.of(
                  "Map",
                  "HashMap",
                  "TreeMap",
                  "LinkedHashMap",
                  "SortedMap",
                  "NavigableMap",
                  "ConcurrentMap",
                  "ConcurrentHashMap",
                  "WeakHashMap",
                  "IdentityHashMap",
                  "Hashtable",
                  "EnumMap"
              ),
              Set.of(GET, "put/2", "remove/1", "putIfAbsent/2", "replace/2")
          ),
          new Model(Set.of("Properties"), Set.of(GET_PROPERTY, GET)),
          new Model(Set.of("System"), Set.of(GET_PROPERTY, "getenv/1")),
          new Model(
              Set.of(CLASS),
              Set.of(
                  "getResource/1",
                  "getResourceAsStream/1",
                  "getPackage/0",
                  "getEnclosingClass/0",
                  "getDeclaringClass/0",
                  "getSuperclass/0",
                  "getCanonicalName/0",
                  "getClassLoader/0"
              )
          ),
          new Model(
              Set.of("ClassLoader"),
              Set.of("getResource/1", "getResourceAsStream/1", "getParent/0")
          ),
          new Model(
              Set.of("Throwable"),
              Set.of("getMessage/0", "getLocalizedMessage/0", "getCause/0")
          ),
          new Model(
              Set.of("Reference", "WeakReference", "SoftReference", "PhantomReference"),
              Set.of("get/0")
          ),
          new Model(
              Set.of(
                  "Queue",
                  "Deque",
                  "ArrayDeque",
                  "LinkedList",
                  "PriorityQueue",
                  "BlockingQueue",
                  "BlockingDeque"
              ),
              Set.of("poll/0", "peek/0", "pollFirst/0", "pollLast/0", "peekFirst/0", "peekLast/0")
          ),
          new Model(Set.of("BufferedReader"), Set.of("readLine/0")),
          new Model(Set.of("File"), Set.of("getParent/0", "getParentFile/0", "list/0")),
          new Model(Set.of("Path"), Set.of("getParent/0", "getFileName/0", "getRoot/0")),
          new Model(Set.of("Matcher"), Set.of("group/1"))
      );

  /** Methods whose second argument is a default that may be null, as written {@code name/arity}. */
  private static final Map<String, Set<String>> DEFAULTED =
      Map.of(
          "getOrDefault/2",
          Set.of("Map", "HashMap", "TreeMap", "LinkedHashMap", "ConcurrentHashMap"),
          "getProperty/2",
          Set.of("System", "Properties")
      );

  private NullLibrary() {}

  /**
   * Tells whether {@code expression} is a call of a JDK method that may return null, on a receiver
   * whose type is known from this file, and that the method does not check.
   */
  static boolean nullable(
      final SyntaxNode expression,
      final NullModel.Type type,
      final NullModel.Method method
  ) {
    final Optional<NullFlow.Call> call = NullFlow.call(expression);
    if (call.isEmpty() || call.get().receiver().isEmpty()) {
      return false;
    }
    final NullFlow.Call found = call.get();
    final Optional<String> receiver = receiverType(found.receiver(), type, method);
    return receiver.isPresent()
        && returnsNull(receiver.get(), found)
        && !NullLibraryGuards.checked(found, method);
  }

  private static boolean returnsNull(final String receiver, final NullFlow.Call call) {
    final String key = call.name() + "/" + call.args().size();
    final boolean modelled =
        MODELS.stream()
            .anyMatch(model -> model.types().contains(receiver) && model.methods().contains(key));
    if (modelled) {
      return true;
    }
    return DEFAULTED.getOrDefault(key, Set.of()).contains(receiver)
        && NullSyntax.isNull(call.args().get(1));
  }

  private static Optional<String> receiverType(
      final String receiver,
      final NullModel.Type type,
      final NullModel.Method method
  ) {
    if (receiver.endsWith(CLASS_LITERAL)) {
      return Optional.of(CLASS);
    }
    final Optional<String> declared = NullLibraryTypes.declared(receiver, type, method);
    final Optional<String> written = declared.isPresent() ? declared : staticName(receiver);
    return written.map(NullLibrary::family);
  }

  private static Optional<String> staticName(final String receiver) {
    return "System".equals(receiver) ? Optional.of(receiver) : Optional.empty();
  }

  /** Maps every exception or error class name to {@code Throwable}, which declares the methods. */
  private static String family(final String name) {
    return name.endsWith(EXCEPTION) || name.endsWith(ERROR) ? "Throwable" : name;
  }

  /** Methods that return null, written {@code name/arity}, for each set of receiver types. */
  private record Model(Set<String> types, Set<String> methods) {}
}
