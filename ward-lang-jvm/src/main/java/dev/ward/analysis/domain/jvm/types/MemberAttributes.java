package dev.ward.analysis.domain.jvm.types;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** The attributes of one class, field or method that type facts need. */
final class MemberAttributes {
  private static final int UNSIGNED_SHORT = 0xFFFF;

  String signature;
  Object constant;
  boolean sealed;
  boolean record;
  final List<String> annotations = new ArrayList<>();
  final List<List<String>> parameterAnnotations = new ArrayList<>();
  final List<String> compatibleWith = new ArrayList<>();
  final List<String> thrown = new ArrayList<>();
  final List<String> memberClasses = new ArrayList<>();

  /** Reads {@code u2 count} attributes from {@code in}; {@code owner} is the class being read. */
  static MemberAttributes read(final ByteBuffer in, final ConstantPool pool, final String owner) {
    final MemberAttributes found = new MemberAttributes();
    final int count = in.getShort() & UNSIGNED_SHORT;
    for (int index = 0; index < count; index++) {
      final String attribute = pool.utf8(in.getShort() & UNSIGNED_SHORT);
      final int end = in.position() + Integer.BYTES + in.getInt(in.position());
      in.position(in.position() + Integer.BYTES);
      found.attribute(in, pool, owner, attribute);
      in.position(end);
    }
    return found;
  }

  private void attribute(
      final ByteBuffer in, final ConstantPool pool, final String owner, final String attribute) {
    switch (attribute) {
      case "Signature" -> signature(in, pool);
      case "ConstantValue" -> constant(in, pool);
      case "Exceptions" -> exceptions(in, pool);
      case "RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations" ->
          annotations.addAll(new AnnotationReader(in, pool).list());
      default -> structure(in, pool, owner, attribute);
    }
  }

  private void structure(
      final ByteBuffer in, final ConstantPool pool, final String owner, final String attribute) {
    switch (attribute) {
      case "RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations" ->
          new AnnotationReader(in, pool).parameters(parameterAnnotations, compatibleWith);
      case "PermittedSubclasses", "Record" -> flag(attribute);
      case "InnerClasses" -> innerClasses(in, pool, owner);
      default -> {
        // Other attributes are skipped by the caller.
      }
    }
  }

  private void signature(final ByteBuffer in, final ConstantPool pool) {
    this.signature = pool.utf8(in.getShort() & UNSIGNED_SHORT);
  }

  private void constant(final ByteBuffer in, final ConstantPool pool) {
    this.constant = pool.constant(in.getShort() & UNSIGNED_SHORT);
  }

  private void flag(final String attribute) {
    this.sealed = this.sealed || "PermittedSubclasses".equals(attribute);
    this.record = this.record || "Record".equals(attribute);
  }

  private void exceptions(final ByteBuffer in, final ConstantPool pool) {
    final int count = in.getShort() & UNSIGNED_SHORT;
    for (int index = 0; index < count; index++) {
      thrown.add(pool.className(in.getShort() & UNSIGNED_SHORT));
    }
  }

  private void innerClasses(final ByteBuffer in, final ConstantPool pool, final String owner) {
    final int count = in.getShort() & UNSIGNED_SHORT;
    for (int index = 0; index < count; index++) {
      final int inner = in.getShort() & UNSIGNED_SHORT;
      final int outer = in.getShort() & UNSIGNED_SHORT;
      in.position(in.position() + 2 * Short.BYTES);
      if (outer != 0 && pool.className(outer).equals(owner)) {
        memberClasses.add(pool.className(inner));
      }
    }
  }
}
