package dev.ward.analysis.domain.jvm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
  private static final char THROWS = '^';
  private static final Map<Character, String> PRIMITIVES =
      Map.of(
          'B', "byte", 'C', "char", 'D', "double", 'F', "float", 'I', "int", 'J', "long",
          'S', "short", 'Z', "boolean", 'V', "void");

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
      List<TypeParameter> typeParameters,
      JType.ClassType superclass,
      List<JType.ClassType> interfaces) {}

  /** Parses a field signature or descriptor. */
  static JType field(final String signature) {
    return new SignatureParser(signature).type();
  }

  /** Parses a method signature or descriptor. */
  static MethodSignature method(final String signature) {
    final SignatureParser parser = new SignatureParser(signature);
    final List<TypeParameter> typeParameters = parser.typeParameters();
    parser.position++;
    final List<JType> parameters = new ArrayList<>();
    while (parser.peek() != ')') {
      parameters.add(parser.type());
    }
    parser.position++;
    final JType returnType = parser.type();
    final List<JType> thrown = new ArrayList<>();
    while (parser.position < signature.length() && parser.peek() == THROWS) {
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
    while (parser.position < signature.length()) {
      interfaces.add(parser.classType());
    }
    return new ClassSignature(typeParameters, superclass, interfaces);
  }

  private List<TypeParameter> typeParameters() {
    final List<TypeParameter> parameters = new ArrayList<>();
    if (position >= text.length() || peek() != ARGUMENTS_START) {
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
    final String primitive = PRIMITIVES.get(next);
    if (primitive == null) {
      throw new IllegalArgumentException("bad signature");
    }
    position++;
    return new JType.Primitive(primitive);
  }

  private JType.ClassType classType() {
    position++;
    final StringBuilder name = new StringBuilder();
    List<JType> arguments = List.of();
    char next = peek();
    position++;
    while (next != END) {
      if (next == ARGUMENTS_START) {
        arguments = typeArguments();
      } else if (next == '.') {
        name.append('$');
        arguments = List.of();
      } else {
        name.append(next == '/' ? '.' : next);
      }
      next = peek();
      position++;
    }
    return new JType.ClassType(name.toString(), arguments);
  }

  private List<JType> typeArguments() {
    final List<JType> arguments = new ArrayList<>();
    while (peek() != ARGUMENTS_END) {
      final char next = peek();
      if (next == '*') {
        position++;
        arguments.add(new JType.Wildcard(null, true));
      } else if (next == '+' || next == '-') {
        position++;
        arguments.add(new JType.Wildcard(type(), next == '+'));
      } else {
        arguments.add(type());
      }
    }
    position++;
    return arguments;
  }

  private char peek() {
    return text.charAt(position);
  }
}
