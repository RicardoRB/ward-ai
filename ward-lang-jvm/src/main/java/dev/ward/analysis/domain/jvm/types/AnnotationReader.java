package dev.ward.analysis.domain.jvm.types;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Reads the annotations of an annotations attribute; only their type names are kept. */
final class AnnotationReader {
  private static final int UNSIGNED_SHORT = 0xFFFF;
  private static final int UNSIGNED_BYTE = 0xFF;

  private final ByteBuffer in;
  private final ConstantPool pool;

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
    for (int pairs = in.getShort() & UNSIGNED_SHORT; pairs > 0; pairs--) {
      in.getShort();
      skipValue();
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
  void parameters(final List<List<String>> into) {
    final int parameters = in.get() & UNSIGNED_BYTE;
    for (int index = 0; index < parameters; index++) {
      final List<String> names = list();
      if (index < into.size()) {
        into.get(index).addAll(names);
      } else {
        into.add(new ArrayList<>(names));
      }
    }
  }
}
