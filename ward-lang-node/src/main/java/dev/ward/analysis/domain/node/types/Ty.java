package dev.ward.analysis.domain.node.types;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A TypeScript type as far as Ward models it. A type that needs full checking is {@link #UNKNOWN}.
 * {@link #print()} writes the type the way the compiler prints it.
 */
public sealed interface Ty
    permits Ty.Named, Ty.Union, Ty.Inter, Ty.Arr, Ty.Tuple, Ty.Fn, Ty.Obj, Ty.Typeof, Ty.Unknown {

  /** The type nothing is known about. */
  Ty UNKNOWN = new Unknown();

  /** The keywords that name primitive types. */
  List<String> PRIMITIVES =
      List.of("number", "string", "boolean", "bigint", "symbol", "void", "null", "undefined");

  /**
   * A named type: a keyword, a literal such as {@code "a"} or {@code 1}, or a declared type.
   *
   * @param name the name as written, qualified with dots
   * @param args the type arguments
   * @param ref where the name resolves, or null while it is unbound
   */
  record Named(String name, List<Ty> args, Ref ref) implements Ty {
    /** Copies the arguments. */
    public Named {
      args = List.copyOf(args);
    }
  }

  /**
   * A union.
   *
   * @param parts the alternatives
   */
  record Union(List<Ty> parts) implements Ty {
    /** Copies the parts. */
    public Union {
      parts = List.copyOf(parts);
    }
  }

  /**
   * An intersection.
   *
   * @param parts the combined types
   */
  record Inter(List<Ty> parts) implements Ty {
    /** Copies the parts. */
    public Inter {
      parts = List.copyOf(parts);
    }
  }

  /**
   * An array.
   *
   * @param element the element type
   */
  record Arr(Ty element) implements Ty {}

  /**
   * A tuple.
   *
   * @param elements the element types
   */
  record Tuple(List<Ty> elements) implements Ty {
    /** Copies the elements. */
    public Tuple {
      elements = List.copyOf(elements);
    }
  }

  /**
   * A function type.
   *
   * @param sig the signature
   */
  record Fn(Sig sig) implements Ty {}

  /**
   * An object type.
   *
   * @param members the members
   */
  record Obj(List<Member> members) implements Ty {
    /** Copies the members. */
    public Obj {
      members = List.copyOf(members);
    }
  }

  /**
   * The type of a declared value that is not a plain object: a class, enum, namespace or function.
   *
   * @param name the name as written
   * @param ref where the name resolves
   */
  record Typeof(String name, Ref ref) implements Ty {}

  /** The unknown type. */
  record Unknown() implements Ty {}

  /** Returns the plain reference to {@code name}. */
  static Ty named(final String name) {
    return new Named(name, List.of(), null);
  }

  /** Returns the reference to {@code name} with {@code args}. */
  static Ty named(final String name, final List<Ty> args) {
    return new Named(name, args, null);
  }

  /** Returns whether anything is known about this type, including its parts. */
  default boolean isKnown() {
    return switch (this) {
      case Unknown unknown -> false;
      case Named named -> named.args().stream().allMatch(Ty::isKnown);
      case Union union -> union.parts().stream().allMatch(Ty::isKnown);
      case Inter inter -> inter.parts().stream().allMatch(Ty::isKnown);
      case Arr arr -> arr.element().isKnown();
      case Tuple tuple -> tuple.elements().stream().allMatch(Ty::isKnown);
      case Fn fn -> fn.sig().returns().isKnown();
      case Obj obj -> obj.members().stream().allMatch(member -> member.type().isKnown());
      case Typeof typeOf -> typeOf.ref() != null;
    };
  }

  /** Returns whether this is a primitive keyword type, including {@code void}. */
  default boolean isPrimitive() {
    return this instanceof Named named
        && named.args().isEmpty()
        && PRIMITIVES.contains(named.name());
  }

  /** Writes the type the way the compiler prints it. */
  default String print() {
    return switch (this) {
      case Unknown unknown -> "unknown";
      case Named named -> printNamed(named);
      case Union union -> join(union.parts(), " | ");
      case Inter inter -> join(inter.parts(), " & ");
      case Arr arr -> wrap(arr.element()) + "[]";
      case Tuple tuple -> "[" + join(tuple.elements(), ", ") + "]";
      case Fn fn -> printSig(fn.sig(), " => ");
      case Obj obj -> printObject(obj);
      case Typeof typeOf -> "typeof " + typeOf.name();
    };
  }

  private static String printNamed(final Named named) {
    return named.args().isEmpty()
        ? named.name()
        : named.name() + "<" + join(named.args(), ", ") + ">";
  }

  private static String join(final List<Ty> types, final String separator) {
    return types.stream()
        .map(
            type ->
                type instanceof Fn && !",".equals(separator.trim())
                    ? "(" + type.print() + ")"
                    : type.print())
        .collect(Collectors.joining(separator));
  }

  private static String wrap(final Ty element) {
    final boolean parens =
        element instanceof Union || element instanceof Inter || element instanceof Fn;
    return parens ? "(" + element.print() + ")" : element.print();
  }

  /** Writes a signature with {@code arrow} between the parameters and the return type. */
  static String printSig(final Sig sig, final String arrow) {
    final String generics =
        sig.typeParams().isEmpty() ? "" : "<" + String.join(", ", sig.typeParams()) + ">";
    final String params =
        sig.params().stream().map(Ty::printParam).collect(Collectors.joining(", "));
    return generics + "(" + params + ")" + arrow + sig.returns().print();
  }

  private static String printParam(final Param param) {
    final String optional = param.optional() ? "?" : "";
    return (param.rest() ? "..." : "") + param.name() + optional + ": " + param.type().print();
  }

  private static String printObject(final Obj obj) {
    if (obj.members().isEmpty()) {
      return "{}";
    }
    return obj.members().stream()
        .map(member -> printMember(member) + ";")
        .collect(Collectors.joining(" ", "{ ", " }"));
  }

  private static String printMember(final Member member) {
    if (member.isMethod()) {
      return member.name() + printSig(member.sigs().get(0), ": ");
    }
    final String optional = member.has(Member.OPTIONAL) ? "?" : "";
    final String readonly = member.has(Member.READONLY) ? "readonly " : "";
    return readonly + member.name() + optional + ": " + member.type().print();
  }
}
