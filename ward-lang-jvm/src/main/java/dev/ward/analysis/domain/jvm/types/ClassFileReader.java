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
      throw new ClassFileException("malformed class file", bad);
    }
  }

  /** The class header: access flags, superclass index and interface names. */
  private record Header(int access, int superIndex, List<String> interfaceNames) {}

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
      final int interfaceCount = ushort();
      for (int index = 0; index < interfaceCount; index++) {
        interfaceNames.add(pool.className(ushort()));
      }
      final List<JvmField> fields = new ArrayList<>();
      final int fieldCount = ushort();
      for (int index = 0; index < fieldCount; index++) {
        fields.add(field());
      }
      final List<JvmMethod> methods = new ArrayList<>();
      final int methodCount = ushort();
      for (int index = 0; index < methodCount; index++) {
        methods.add(method());
      }
      final MemberAttributes attributes = MemberAttributes.read(in, pool, name);
      return build(new Header(access, superIndex, interfaceNames), fields, methods, attributes);
    }

    private int ushort() {
      return in.getShort() & UNSIGNED_SHORT;
    }

    private JvmClass build(
        final Header header,
        final List<JvmField> fields,
        final List<JvmMethod> methods,
        final MemberAttributes attributes) {
      final boolean hasSuper = header.superIndex() != 0;
      Optional<JType.ClassType> superclass =
          hasSuper
              ? Optional.of(JType.ClassType.of(pool.className(header.superIndex())))
              : Optional.empty();
      List<JType.ClassType> interfaces =
          header.interfaceNames().stream().map(JType.ClassType::of).toList();
      List<TypeParameter> typeParameters = List.of();
      if (attributes.signature != null) {
        final SignatureParser.ClassSignature parsed =
            SignatureParser.classSignature(attributes.signature);
        typeParameters = parsed.typeParameters();
        superclass = hasSuper ? Optional.of(parsed.superclass()) : Optional.empty();
        interfaces = parsed.interfaces();
      }
      return new JvmClass(
          name,
          kind(header.access(), attributes.record),
          header.access(),
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
      if (Flags.has(access, Flags.ANNOTATION)) {
        return JvmClass.Kind.ANNOTATION;
      }
      if (Flags.has(access, Flags.INTERFACE)) {
        return JvmClass.Kind.INTERFACE;
      }
      if (Flags.has(access, Flags.ENUM)) {
        return JvmClass.Kind.ENUM;
      }
      return record ? JvmClass.Kind.RECORD : JvmClass.Kind.CLASS;
    }

    private JvmField field() {
      final int access = ushort();
      final String fieldName = pool.utf8(ushort());
      final String descriptor = pool.utf8(ushort());
      final MemberAttributes attributes = MemberAttributes.read(in, pool, name);
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
      final MemberAttributes attributes = MemberAttributes.read(in, pool, name);
      final SignatureParser.MethodSignature parsed =
          SignatureParser.method(attributes.signature != null ? attributes.signature : descriptor);
      final List<JType> thrown =
          parsed.thrown().isEmpty()
              ? attributes.thrown.stream().map(JType.ClassType::of).map(JType.class::cast).toList()
              : parsed.thrown();
      return new JvmMethod(
          methodName,
          access,
          parsed.typeParameters(),
          parsed.parameters(),
          parsed.returnType(),
          thrown,
          attributes.annotations,
          attributes.parameterAnnotations,
          attributes.compatibleWith);
    }
  }
}
