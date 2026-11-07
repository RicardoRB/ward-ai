package dev.ward.analysis.domain.node.types;

import java.util.List;

/**
 * A TypeScript type as far as Ward models it. A type that needs full checking is {@link #UNKNOWN}.
 * {@link #print()} writes the type the way the compiler prints it.
 */
public sealed interface TsType
    permits TsType.Named,
        TsType.Union,
        TsType.Inter,
        TsType.Arr,
        TsType.Tuple,
        TsType.Fn,
        TsType.Obj,
        TsType.Typeof,
        TsType.Unknown {

  /** The type nothing is known about. */
  TsType UNKNOWN = new Unknown();

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
  record Named(String name, List<TsType> args, NameRef ref) implements TsType {
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
  record Union(List<TsType> parts) implements TsType {
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
  record Inter(List<TsType> parts) implements TsType {
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
  record Arr(TsType element) implements TsType {}

  /**
   * A tuple.
   *
   * @param elements the element types
   */
  record Tuple(List<TsType> elements) implements TsType {
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
  record Fn(Sig sig) implements TsType {}

  /**
   * An object type.
   *
   * @param members the members
   */
  record Obj(List<Member> members) implements TsType {
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
  record Typeof(String name, NameRef ref) implements TsType {}

  /** The unknown type. */
  record Unknown() implements TsType {}

  /** Returns the plain reference to {@code name}. */
  static TsType named(final String name) {
    return new Named(name, List.of(), null);
  }

  /** Returns the reference to {@code name} with {@code args}. */
  static TsType named(final String name, final List<TsType> args) {
    return new Named(name, args, null);
  }

  /** Returns the array of {@code element}. */
  static TsType arrayOf(final TsType element) {
    return new Arr(element);
  }

  /** Returns whether anything is known about this type, including its parts. */
  default boolean isKnown() {
    return switch (this) {
      case Unknown unknown -> false;
      case Named named -> named.args().stream().allMatch(TsType::isKnown);
      case Union union -> union.parts().stream().allMatch(TsType::isKnown);
      case Inter inter -> inter.parts().stream().allMatch(TsType::isKnown);
      case Arr arr -> arr.element().isKnown();
      case Tuple tuple -> tuple.elements().stream().allMatch(TsType::isKnown);
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
      case Named named -> TypePrinter.printNamed(named);
      case Union union -> TypePrinter.join(union.parts(), " | ");
      case Inter inter -> TypePrinter.join(inter.parts(), " & ");
      case Arr arr -> TypePrinter.wrap(arr.element()) + "[]";
      case Tuple tuple -> "[" + TypePrinter.join(tuple.elements(), ", ") + "]";
      case Fn fn -> TypePrinter.printSig(fn.sig(), " => ");
      case Obj obj -> TypePrinter.printObject(obj);
      case Typeof typeOf -> "typeof " + typeOf.name();
    };
  }
}
