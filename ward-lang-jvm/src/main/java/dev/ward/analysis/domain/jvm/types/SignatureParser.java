package dev.ward.analysis.domain.jvm.types;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the generic {@code Signature} attributes and the descriptors of class files into {@link
 * JType}s. A descriptor is a signature without type parameters and arguments, so one parser reads
 * both.
 */
final class SignatureParser {
  private static final char CLASS = 'L';
  private static final char TYPE_VARIABLE = 'T';
  private static final char ARRAY = '[';
  private static final char END = ';';
  private static final char ARGUMENTS_START = '<';
  private static final char ARGUMENTS_END = '>';
  private static final String PRIMITIVES = "BCDFIJSZV";

  private final String text;
  private int position;

  SignatureParser(final String text) {
    this.text = text;
  }

  /** The parsed parts of a method signature. */
  record MethodSignature(
      List<TypeParameter> typeParameters,
      List<JType> parameters,
      JType returnType,
      List<JType> thrown) {}

  /** The parsed parts of a class signature. */
  record ClassSignature(
      List<TypeParameter> typeParameters, JType.ClassType superclass, List<JType.ClassType> interfaces) {}

  /** Parses a field signature or descriptor. */
  static JType field(final String signature) {
    return new SignatureParser(signature).type();
  }

  /** Parses a method signature or descriptor. */
  static MethodSignature method(final String signature) {
    final SignatureParser parser = new SignatureParser(signature);
    final List<TypeParameter> typeParameters = parser.typeParameters();
    parser.expect('(');
    final List<JType> parameters = new ArrayList<>();
    while (parser.peek() != ')') {
      parameters.add(parser.type());
    }
    parser.expect(')');
    final JType returnType = parser.type();
    final List<JType> thrown = new ArrayList<>();
    while (parser.hasMore() && parser.peek() == '^') {
      parser.position++;
      thrown.add(parser.type());
    }
    return new MethodSignature(typeParameters, parameters, returnType, thrown);
  }

  /** Parses a class signature. */
  static ClassSignature classSignature(final String signature) {
    final SignatureParser parser = new SignatureParser(signature);
    final List<TypeParameter> typeParameters = parser.typeParameters();
    final JType.ClassType superclass = parser.classType();
    final List<JType.ClassType> interfaces = new ArrayList<>();
    while (parser.hasMore()) {
      interfaces.add(parser.classType());
    }
    return new ClassSignature(typeParameters, superclass, interfaces);
  }

  private List<TypeParameter> typeParameters() {
    final List<TypeParameter> parameters = new ArrayList<>();
    if (!hasMore() || peek() != ARGUMENTS_START) {
      return parameters;
    }
    position++;
    while (peek() != ARGUMENTS_END) {
      final int colon = text.indexOf(':', position);
      final String name = text.substring(position, colon);
      position = colon;
      final List<JType> bounds = new ArrayList<>();
      while (peek() == ':') {
        position++;
        if (peek() != ':') {
          bounds.add(type());
        }
      }
      parameters.add(new TypeParameter(name, bounds));
    }
    position++;
    return parameters;
  }

  private JType type() {
    final char next = peek();
    if (next == ARRAY) {
      position++;
      return new JType.ArrayType(type());
    }
    if (next == TYPE_VARIABLE) {
      final int end = text.indexOf(END, position);
      final String name = text.substring(position + 1, end);
      position = end + 1;
      return new JType.TypeVariable(name);
    }
    if (next == CLASS) {
      return classType();
    }
    if (PRIMITIVES.indexOf(next) < 0) {
      throw new IllegalArgumentException("bad signature");
    }
    position++;
    return new JType.Primitive(primitiveName(next));
  }

  private JType.ClassType classType() {
    expect(CLASS);
    final StringBuilder name = new StringBuilder();
    List<JType> arguments = List.of();
    while (true) {
      final char next = text.charAt(position++);
      if (next == END) {
        return new JType.ClassType(name.toString(), arguments);
      }
      if (next == ARGUMENTS_START) {
        arguments = typeArguments();
      } else if (next == '.') {
        name.append('$');
        arguments = List.of();
      } else {
        name.append(next == '/' ? '.' : next);
      }
    }
  }

  private List<JType> typeArguments() {
    final List<JType> arguments = new ArrayList<>();
    while (peek() != ARGUMENTS_END) {
      arguments.add(typeArgument());
    }
    position++;
    return arguments;
  }

  private JType typeArgument() {
    final char next = peek();
    if (next == '*') {
      position++;
      return new JType.Wildcard(null, true);
    }
    if (next == '+' || next == '-') {
      position++;
      return new JType.Wildcard(type(), next == '+');
    }
    return type();
  }

  private char peek() {
    return text.charAt(position);
  }

  private boolean hasMore() {
    return position < text.length();
  }

  private void expect(final char expected) {
    if (peek() != expected) {
      throw new IllegalArgumentException("bad signature");
    }
    position++;
  }

  private static String primitiveName(final char code) {
    return switch (code) {
      case 'B' -> "byte";
      case 'C' -> "char";
      case 'D' -> "double";
      case 'F' -> "float";
      case 'I' -> "int";
      case 'J' -> "long";
      case 'S' -> "short";
      case 'Z' -> "boolean";
      default -> "void";
    };
  }
}
