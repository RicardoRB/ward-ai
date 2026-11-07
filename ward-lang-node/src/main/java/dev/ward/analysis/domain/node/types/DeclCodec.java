package dev.ward.analysis.domain.node.types;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Writes a {@link FileDecls} to bytes and reads it back, so a package is parsed once. */
public final class DeclCodec {
  private static final int VERSION = 1;

  private DeclCodec() {}

  /** Returns the bytes of {@code decls}. */
  public static byte[] encode(final FileDecls decls) {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(VERSION);
      out.writeBoolean(decls.isModule());
      CodecIo.list(out, decls.references(), DataOutputStream::writeUTF);
      CodecIo.list(out, List.copyOf(decls.imports().entrySet()), DeclCodec::importEntry);
      DeclBodyCodec.declList(out, decls.decls());
      CodecIo.list(out, decls.exports(), DeclCodec::export);
      CodecIo.list(out, List.copyOf(decls.modules().entrySet()), DeclCodec::module);
    } catch (IOException failed) {
      throw new UncheckedIOException(failed);
    }
    return bytes.toByteArray();
  }

  /** Returns the index in {@code bytes}, or empty when they are not an index of this version. */
  public static Optional<FileDecls> decode(final byte[] bytes) {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      if (in.readInt() != VERSION) {
        return Optional.empty();
      }
      final boolean isModule = in.readBoolean();
      final List<String> references = CodecIo.read(in, CodecIo::readText);
      final Map<String, FileDecls.Import> imports = new LinkedHashMap<>();
      CodecIo.read(in, DeclCodec::readImportEntry)
          .forEach(entry -> imports.put(entry.getKey(), entry.getValue()));
      final List<Decl> decls = DeclBodyCodec.readDecls(in);
      final List<FileDecls.Export> exports = CodecIo.read(in, DeclCodec::readExport);
      final Map<String, List<Decl>> modules = new LinkedHashMap<>();
      CodecIo.read(in, DeclCodec::readModule)
          .forEach(entry -> modules.put(entry.getKey(), entry.getValue()));
      return Optional.of(new FileDecls(isModule, imports, decls, exports, modules, references));
    } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException corrupt) {
      return Optional.empty();
    }
  }

  private static void importEntry(
      final DataOutputStream out, final Map.Entry<String, FileDecls.Import> entry)
      throws IOException {
    out.writeUTF(entry.getKey());
    out.writeUTF(entry.getValue().specifier());
    out.writeUTF(entry.getValue().member());
  }

  private static Map.Entry<String, FileDecls.Import> readImportEntry(final DataInputStream in)
      throws IOException {
    final String local = in.readUTF();
    return Map.entry(local, new FileDecls.Import(in.readUTF(), in.readUTF()));
  }

  private static void export(final DataOutputStream out, final FileDecls.Export export)
      throws IOException {
    out.writeUTF(export.exported());
    out.writeUTF(export.local());
    out.writeUTF(export.specifier());
  }

  private static FileDecls.Export readExport(final DataInputStream in) throws IOException {
    return new FileDecls.Export(in.readUTF(), in.readUTF(), in.readUTF());
  }

  private static void module(final DataOutputStream out, final Map.Entry<String, List<Decl>> entry)
      throws IOException {
    out.writeUTF(entry.getKey());
    DeclBodyCodec.declList(out, entry.getValue());
  }

  private static Map.Entry<String, List<Decl>> readModule(final DataInputStream in)
      throws IOException {
    return Map.entry(in.readUTF(), DeclBodyCodec.readDecls(in));
  }
}
