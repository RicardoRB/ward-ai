package dev.ward.analysis.domain.jvm.types;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the annotations of an annotations attribute; only their type names are kept, besides the
 * value of {@code @CompatibleWith}, which names a type variable.
 */
final class AnnotationReader {
  private static final int UNSIGNED_SHORT = 0xFFFF;
  private static final int UNSIGNED_BYTE = 0xFF;

  private final ByteBuffer in;
  private static final String COMPATIBLE_WITH =
      "Lcom/google/errorprone/annotations/CompatibleWith;";
  private static final char STRING = 's';

  private final ConstantPool pool;
  private String compatible = "";

  AnnotationReader(final ByteBuffer in, final ConstantPool pool) {
    this.in = in;
    this.pool = pool;
  }

  /** Reads {@code u2 count} and that many annotations, returning their qualified type names. */
  List<String> list() {
    final List<String> names = new ArrayList<>();
    for (int count = in.getShort() & UNSIGNED_SHORT; count > 0; count--) {
      names.add(annotation());
    }
    return names;
  }

  private String annotation() {
    final String descriptor = pool.utf8(in.getShort() & UNSIGNED_SHORT);
    final boolean marked = COMPATIBLE_WITH.equals(descriptor);
    for (int pairs = in.getShort() & UNSIGNED_SHORT; pairs > 0; pairs--) {
      in.getShort();
      if (marked && in.get(in.position()) == STRING) {
        in.get();
        compatible = pool.utf8(in.getShort() & UNSIGNED_SHORT);
      } else {
        skipValue();
      }
    }
    return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
  }

  private void skipValue() {
    final char tag = (char) in.get();
    switch (tag) {
      case 'e' -> in.getInt();
      case '@' -> annotation();
      case '[' -> {
        for (int count = in.getShort() & UNSIGNED_SHORT; count > 0; count--) {
          skipValue();
        }
      }
      default -> in.getShort();
    }
  }

  /**
   * Reads a parameter annotations attribute, adding the names of each parameter to {@code into}.
   */
  void parameters(final List<List<String>> into, final List<String> compatibleWith) {
    final int parameters = in.get() & UNSIGNED_BYTE;
    for (int index = 0; index < parameters; index++) {
      compatible = "";
      final List<String> names = list();
      if (index < compatibleWith.size()) {
        if (!compatible.isEmpty()) {
          compatibleWith.set(index, compatible);
        }
      } else {
        compatibleWith.add(compatible);
      }
      if (index < into.size()) {
        into.get(index).addAll(names);
      } else {
        into.add(new ArrayList<>(names));
      }
    }
  }
}
