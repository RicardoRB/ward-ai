package dev.ward.analysis.domain.jvm.types;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the parts of a class file that type facts need: the class header, supertypes, generic
 * signatures, fields, methods, annotations, constants, thrown exceptions, member classes and
 * sealing. Method bodies are skipped. Annotations are read from both the runtime-visible and the
 * class-retention attributes, which is where Kotlin and the JetBrains nullness annotations are.
 */
public final class ClassFileReader {
  private static final int MAGIC = 0xCAFEBABE;
  private static final int UNSIGNED_SHORT = 0xFFFF;
  private static final String KOTLIN_METADATA = "kotlin.Metadata";

  private ClassFileReader() {}

  /**
   * Reads one class file.
   *
   * @param bytes the content of a {@code .class} file
   * @return the class
   * @throws ClassFileException if the bytes are not a class file this reader understands
   */
  public static JvmClass read(final byte[] bytes) throws ClassFileException {
    try {
      return new Parse(ByteBuffer.wrap(bytes)).parse();
    } catch (BufferUnderflowException | IndexOutOfBoundsException | IllegalArgumentException bad) {
      throw new ClassFileException("malformed class file");
    }
  }

  /** The attributes of one class, field or method. */
  private static final class Attributes {
    private String signature;
    private Object constant;
    private final List<String> annotations = new ArrayList<>();
    private final List<List<String>> parameterAnnotations = new ArrayList<>();
    private final List<String> thrown = new ArrayList<>();
    private final List<String> memberClasses = new ArrayList<>();
    private boolean sealed;
    private boolean record;
  }

  /** The state of reading one class file. */
  private static final class Parse {
    private final ByteBuffer in;
    private ConstantPool pool;
    private String name;

    Parse(final ByteBuffer in) {
      this.in = in;
    }

    JvmClass parse() throws ClassFileException {
      if (in.getInt() != MAGIC) {
        throw new ClassFileException("not a class file");
      }
      in.getInt();
      pool = ConstantPool.read(in);
      final int access = ushort();
      name = pool.className(ushort());
      final int superIndex = ushort();
      final List<String> interfaceNames = new ArrayList<>();
      for (int count = ushort(); count > 0; count--) {
        interfaceNames.add(pool.className(ushort()));
      }
      final List<JvmField> fields = new ArrayList<>();
      for (int count = ushort(); count > 0; count--) {
        fields.add(field());
      }
      final List<JvmMethod> methods = new ArrayList<>();
      for (int count = ushort(); count > 0; count--) {
        methods.add(method());
      }
      final Attributes attributes = attributes();
      return build(access, superIndex, interfaceNames, fields, methods, attributes);
    }

    private int ushort() {
      return in.getShort() & UNSIGNED_SHORT;
    }

    private JvmClass build(
        final int access,
        final int superIndex,
        final List<String> interfaceNames,
        final List<JvmField> fields,
        final List<JvmMethod> methods,
        final Attributes attributes) {
      Optional<JType.ClassType> superclass =
          superIndex == 0
              ? Optional.empty()
              : Optional.of(JType.ClassType.of(pool.className(superIndex)));
      List<JType.ClassType> interfaces = interfaceNames.stream().map(JType.ClassType::of).toList();
      List<TypeParameter> typeParameters = List.of();
      if (attributes.signature != null) {
        final SignatureParser.ClassSignature parsed =
            SignatureParser.classSignature(attributes.signature);
        typeParameters = parsed.typeParameters();
        superclass = superIndex == 0 ? Optional.empty() : Optional.of(parsed.superclass());
        interfaces = parsed.interfaces();
      }
      return new JvmClass(
          name,
          kind(access, attributes.record),
          access,
          superclass,
          interfaces,
          typeParameters,
          fields,
          methods,
          attributes.annotations,
          attributes.memberClasses,
          attributes.sealed,
          attributes.annotations.contains(KOTLIN_METADATA));
    }

    private static JvmClass.Kind kind(final int access, final boolean record) {
      if ((access & Flags.ANNOTATION) != 0) {
        return JvmClass.Kind.ANNOTATION;
      }
      if ((access & Flags.INTERFACE) != 0) {
        return JvmClass.Kind.INTERFACE;
      }
      if ((access & Flags.ENUM) != 0) {
        return JvmClass.Kind.ENUM;
      }
      return record ? JvmClass.Kind.RECORD : JvmClass.Kind.CLASS;
    }

    private JvmField field() {
      final int access = ushort();
      final String fieldName = pool.utf8(ushort());
      final String descriptor = pool.utf8(ushort());
      final Attributes attributes = attributes();
      final String written = attributes.signature != null ? attributes.signature : descriptor;
      final JType type = SignatureParser.field(written);
      return new JvmField(
          fieldName,
          access,
          type,
          Optional.ofNullable(constantOf(type, attributes.constant)),
          attributes.annotations);
    }

    private static Object constantOf(final JType type, final Object raw) {
      if (raw instanceof Long value && type instanceof JType.Primitive primitive) {
        return "boolean".equals(primitive.name()) ? (Object) (value != 0) : value;
      }
      return raw;
    }

    private JvmMethod method() {
      final int access = ushort();
      final String methodName = pool.utf8(ushort());
      final String descriptor = pool.utf8(ushort());
      final Attributes attributes = attributes();
      final SignatureParser.MethodSignature parsed =
          SignatureParser.method(attributes.signature != null ? attributes.signature : descriptor);
      final List<JType> thrown =
          parsed.thrown().isEmpty()
              ? attributes.thrown.stream().map(thrownName -> (JType) JType.ClassType.of(thrownName)).toList()
              : parsed.thrown();
      return new JvmMethod(
          methodName,
          access,
          parsed.typeParameters(),
          parsed.parameters(),
          parsed.returnType(),
          thrown,
          attributes.annotations,
          attributes.parameterAnnotations);
    }

    private Attributes attributes() {
      final Attributes found = new Attributes();
      for (int count = ushort(); count > 0; count--) {
        final String attribute = pool.utf8(ushort());
        final int length = in.getInt();
        final int end = in.position() + length;
        attribute(found, attribute);
        in.position(end);
      }
      return found;
    }

    private void attribute(final Attributes found, final String attribute) {
      switch (attribute) {
        case "Signature" -> found.signature = pool.utf8(ushort());
        case "ConstantValue" -> found.constant = pool.constant(ushort());
        case "Exceptions" -> {
          for (int count = ushort(); count > 0; count--) {
            found.thrown.add(pool.className(ushort()));
          }
        }
        case "RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations" ->
            found.annotations.addAll(new AnnotationReader(in, pool).list());
        case "RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations" ->
            parameterAnnotations(found);
        case "PermittedSubclasses" -> found.sealed = true;
        case "Record" -> found.record = true;
        case "InnerClasses" -> innerClasses(found);
        default -> {
          // Other attributes are skipped by the caller.
        }
      }
    }

    private void parameterAnnotations(final Attributes found) {
      final int parameters = in.get() & 0xFF;
      for (int index = 0; index < parameters; index++) {
        final List<String> names = new AnnotationReader(in, pool).list();
        if (index < found.parameterAnnotations.size()) {
          found.parameterAnnotations.get(index).addAll(names);
        } else {
          found.parameterAnnotations.add(new ArrayList<>(names));
        }
      }
    }

    private void innerClasses(final Attributes found) {
      for (int count = ushort(); count > 0; count--) {
        final int inner = ushort();
        final int outer = ushort();
        ushort();
        ushort();
        if (outer != 0 && pool.className(outer).equals(name)) {
          found.memberClasses.add(pool.className(inner));
        }
      }
    }
  }
}
