package dev.ward.analysis.domain.jvm.types;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the annotations of an annotations attribute: their type names, and their element values by
 * {@code <annotation>#<element>}. A parameter annotation also keeps the value of
 * {@code @CompatibleWith}, which names a type variable.
 */
final class AnnotationReader {
  private static final int UNSIGNED_SHORT = 0xFFFF;
  private static final int UNSIGNED_BYTE = 0xFF;

  private final ByteBuffer in;
  private static final String COMPATIBLE_WITH =
      "Lcom/google/errorprone/annotations/CompatibleWith;";

  private final ConstantPool pool;
  private final List<String> compatible = new ArrayList<>();
  private final Map<String, List<String>> values = new LinkedHashMap<>();

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
    final String name = className(descriptor);
    final boolean marked = COMPATIBLE_WITH.equals(descriptor);
    for (int pairs = in.getShort() & UNSIGNED_SHORT; pairs > 0; pairs--) {
      final String element = pool.utf8(in.getShort() & UNSIGNED_SHORT);
      final List<String> found = elementValue();
      if (marked && !found.isEmpty()) {
        compatible.add(found.get(0));
      }
      keep(name + "#" + element, found);
    }
    return name;
  }

  private void keep(final String key, final List<String> found) {
    values.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(found);
  }

  private List<String> elementValue() {
    final List<String> found = new ArrayList<>();
    value(found);
    return found;
  }

  /** Reads an element value, adding its constants, class names and enum names to {@code into}. */
  private void value(final List<String> into) {
    final char tag = (char) in.get();
    switch (tag) {
      case 's' -> into.add(pool.utf8(in.getShort() & UNSIGNED_SHORT));
      case 'c' -> into.add(className(pool.utf8(in.getShort() & UNSIGNED_SHORT)));
      case 'e' -> {
        in.getShort();
        into.add(pool.utf8(in.getShort() & UNSIGNED_SHORT));
      }
      case '@' -> annotation();
      case '[' -> {
        for (int count = in.getShort() & UNSIGNED_SHORT; count > 0; count--) {
          value(into);
        }
      }
      case 'Z' -> into.add(String.valueOf(!"0".equals(String.valueOf(constant()))));
      default -> into.add(String.valueOf(constant()));
    }
  }

  private Object constant() {
    return pool.constant(in.getShort() & UNSIGNED_SHORT);
  }

  /** The element values of the annotations read so far, by {@code <annotation>#<element>}. */
  Map<String, List<String>> values() {
    return values;
  }

  private static String className(final String descriptor) {
    final String inner =
        descriptor.startsWith("L") && descriptor.endsWith(";")
            ? descriptor.substring(1, descriptor.length() - 1)
            : descriptor;
    return inner.replace('/', '.');
  }

  /**
   * Reads a parameter annotations attribute, adding the names of each parameter to {@code into}.
   */
  void parameters(final List<List<String>> into, final List<String> compatibleWith) {
    final int parameters = in.get() & UNSIGNED_BYTE;
    for (int index = 0; index < parameters; index++) {
      compatible.clear();
      final List<String> names = list();
      final String value = compatible.isEmpty() ? "" : compatible.get(0);
      record(into, compatibleWith, index, names, value);
    }
  }

  private static void record(
      final List<List<String>> into,
      final List<String> compatibleWith,
      final int index,
      final List<String> names,
      final String value) {
    if (index < compatibleWith.size()) {
      if (!value.isEmpty()) {
        compatibleWith.set(index, value);
      }
    } else {
      compatibleWith.add(value);
    }
    if (index < into.size()) {
      into.get(index).addAll(names);
    } else {
      into.add(new ArrayList<>(names));
    }
  }
}
