package dev.ward.analysis.domain.node.types;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes counted lists for the declaration codecs. */
final class CodecIo {
  private CodecIo() {}

  /** A write of one value. */
  @FunctionalInterface
  interface Writer<T> {
    void write(DataOutputStream out, T value) throws IOException;
  }

  /** A read of one value. */
  @FunctionalInterface
  interface Reader<T> {
    T read(DataInputStream in) throws IOException;
  }

  static <T> void list(final DataOutputStream out, final List<T> values, final Writer<T> writer)
      throws IOException {
    out.writeInt(values.size());
    for (final T value : values) {
      writer.write(out, value);
    }
  }

  static <T> List<T> read(final DataInputStream in, final Reader<T> reader) throws IOException {
    final int count = in.readInt();
    final List<T> values = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      values.add(reader.read(in));
    }
    return values;
  }

  static String readText(final DataInputStream in) throws IOException {
    return in.readUTF();
  }
}
