package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The classes a module declares at its top level, with their bases, members and method resolution
 * order, for the Pylint class checks. A base that is not a class of the module, {@code object} or
 * {@code ABC} makes the class and its subclasses unknown, so the checks report nothing for them.
 */
final class PythonClassModel {

  static final String OBJECT = "object";
  static final String ABC = "ABC";
  static final String ABC_QUALIFIED = "abc.ABC";

  private static final Pattern ABSTRACT =
      Pattern.compile("@(?:abc\\.)?abstract(?:method|property|classmethod|staticmethod)(?=[@(]|$)");
  private static final Pattern PROTOCOL = Pattern.compile("^(?:typing\\.)?Protocol$");

  private final Map<String, ClassInfo> classes;
  private final PythonClassOrder orders;

  /** Whether a method resolution order is known, cannot be computed, or is inconsistent. */
  enum Status {
    KNOWN,
    UNKNOWN,
    INCONSISTENT
  }

  /** A method resolution order, most derived class first, when the status is known. */
  record Order(Status status, List<String> names) {

    /** Copies the names. */
    Order {
      names = List.copyOf(names);
    }

    /** A known order of the given names. */
    static Order known(final List<String> names) {
      return new Order(Status.KNOWN, names);
    }

    /** The order of a class whose bases are not all known. */
    static Order unknown() {
      return new Order(Status.UNKNOWN, List.of());
    }

    /** The order of a class whose bases have no consistent linearization. */
    static Order inconsistent() {
      return new Order(Status.INCONSISTENT, List.of());
    }
  }

  /** A function defined directly in a class body, with the text of its decorators. */
  record Method(String name, SyntaxNode node, String decorators) {

    /** Whether the function is decorated as abstract. */
    boolean isAbstract() {
      return ABSTRACT.matcher(decorators).find();
    }

    /** Whether the function is decorated with the given name, bare or as an attribute. */
    boolean decoratedWith(final String decorator) {
      return Pattern.compile("@(?:[\\w.]+\\.)?" + Pattern.quote(decorator) + "(?=[@(]|$)")
          .matcher(decorators)
          .find();
    }
  }

  /** A class of the module: its raw base texts, its metaclass keyword and its members. */
  record ClassInfo(
      String name,
      SyntaxNode node,
      List<String> rawBases,
      boolean abcMetaclass,
      Map<String, Method> methods,
      Set<String> attributes) {

    /** Copies the lists, the sorted method map and the attribute set. */
    ClassInfo {
      rawBases = List.copyOf(rawBases);
      methods = Collections.unmodifiableMap(new TreeMap<>(methods));
      attributes = Set.copyOf(attributes);
    }

    /** Whether the class is abstract by its own declaration, as Pylint's class_is_abstract. */
    boolean declaresAbstract() {
      return abcMetaclass
          || rawBases.stream().anyMatch(base -> ABC.equals(base) || ABC_QUALIFIED.equals(base))
          || rawBases.stream().anyMatch(base -> PROTOCOL.matcher(base).matches())
          || methods.values().stream().anyMatch(Method::isAbstract);
    }
  }

  private PythonClassModel(final Map<String, ClassInfo> classes) {
    this.classes = classes;
    this.orders = new PythonClassOrder(classes);
  }

  /** Builds the model of the classes a module declares at its top level. */
  static PythonClassModel of(final SyntaxNode root) {
    return new PythonClassModel(PythonClassInfos.of(root));
  }

  /** The module's classes by name. */
  Map<String, ClassInfo> classes() {
    return classes;
  }

  /** The class with the given name, when the module declares it. */
  Optional<ClassInfo> classNamed(final String name) {
    return Optional.ofNullable(classes.get(name));
  }

  /** The method resolution order of a class. */
  Order order(final String name) {
    return orders.order(name);
  }

  /** Whether the class derives from {@code ABC} or has the {@code ABCMeta} metaclass. */
  boolean isAbc(final String name) {
    return order(name).names().stream()
        .anyMatch(
            ancestor ->
                ABC.equals(ancestor)
                    || (classes.containsKey(ancestor) && classes.get(ancestor).abcMetaclass()));
  }

  /**
   * The abstract methods the class leaves unimplemented, by name, with the class that declares
   * them. A name is taken from the first class of the order that defines it.
   */
  Map<String, String> unimplemented(final String name) {
    final Map<String, String> owners = new TreeMap<>();
    final Set<String> seen = new HashSet<>();
    for (final String ancestor : order(name).names()) {
      final ClassInfo info = classes.get(ancestor);
      if (info != null) {
        for (final String member : union(info)) {
          if (seen.add(member) && isAbstractMember(info, member)) {
            owners.put(member, ancestor);
          }
        }
      }
    }
    return owners;
  }

  private static boolean isAbstractMember(final ClassInfo info, final String member) {
    final Method method = info.methods().get(member);
    return method != null && method.isAbstract();
  }

  private static Set<String> union(final ClassInfo info) {
    final Set<String> names = new HashSet<>(info.methods().keySet());
    names.addAll(info.attributes());
    return names;
  }
}
