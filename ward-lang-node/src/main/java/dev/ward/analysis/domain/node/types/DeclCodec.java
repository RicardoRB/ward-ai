package dev.ward.analysis.domain.node.types;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Writes a {@link FileDecls} to bytes and reads it back, so a package is parsed once. */
public final class DeclCodec {
  private static final int VERSION = 1;
  private static final int NAMED = 0;
  private static final int UNION = 1;
  private static final int INTER = 2;
  private static final int ARR = 3;
  private static final int TUPLE = 4;
  private static final int FN = 5;
  private static final int OBJ = 6;
  private static final int UNKNOWN = 7;

  private DeclCodec() {}

  /** Returns the bytes of {@code decls}. */
  public static byte[] encode(final FileDecls decls) {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(VERSION);
      out.writeBoolean(decls.isModule());
      list(out, decls.references(), (stream, text) -> stream.writeUTF(text));
      list(out, List.copyOf(decls.imports().entrySet()), DeclCodec::importEntry);
      declList(out, decls.decls());
      list(out, decls.exports(), DeclCodec::export);
      list(out, List.copyOf(decls.modules().entrySet()), DeclCodec::module);
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
      final List<String> references = read(in, DeclCodec::readText);
      final Map<String, FileDecls.Import> imports = new LinkedHashMap<>();
      read(in, DeclCodec::readImportEntry)
          .forEach(entry -> imports.put(entry.getKey(), entry.getValue()));
      final List<Decl> decls = readDecls(in);
      final List<FileDecls.Export> exports = read(in, DeclCodec::readExport);
      final Map<String, List<Decl>> modules = new LinkedHashMap<>();
      read(in, DeclCodec::readModule)
          .forEach(entry -> modules.put(entry.getKey(), entry.getValue()));
      return Optional.of(new FileDecls(isModule, imports, decls, exports, modules, references));
    } catch (IOException | RuntimeException corrupt) {
      return Optional.empty();
    }
  }

  /** A write of one value. */
  @FunctionalInterface
  private interface Writer<T> {
    void write(DataOutputStream out, T value) throws IOException;
  }

  /** A read of one value. */
  @FunctionalInterface
  private interface Reader<T> {
    T read(DataInputStream in) throws IOException;
  }

  private static <T> void list(
      final DataOutputStream out, final List<T> values, final Writer<T> writer) throws IOException {
    out.writeInt(values.size());
    for (final T value : values) {
      writer.write(out, value);
    }
  }

  private static String readText(final DataInputStream in) throws IOException {
    return in.readUTF();
  }

  private static <T> List<T> read(final DataInputStream in, final Reader<T> reader)
      throws IOException {
    final int count = in.readInt();
    final List<T> values = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      values.add(reader.read(in));
    }
    return values;
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
    declList(out, entry.getValue());
  }

  private static Map.Entry<String, List<Decl>> readModule(final DataInputStream in)
      throws IOException {
    return Map.entry(in.readUTF(), readDecls(in));
  }

  private static void declList(final DataOutputStream out, final List<Decl> decls)
      throws IOException {
    list(out, decls, DeclCodec::decl);
  }

  private static List<Decl> readDecls(final DataInputStream in) throws IOException {
    return read(in, DeclCodec::readDecl);
  }

  private static void decl(final DataOutputStream out, final Decl decl) throws IOException {
    out.writeInt(decl.kind().ordinal());
    out.writeUTF(decl.name());
    header(out, decl.header());
    list(out, decl.members(), DeclCodec::member);
    ty(out, decl.type());
    declList(out, decl.body());
    out.writeInt(decl.flags());
  }

  private static Decl readDecl(final DataInputStream in) throws IOException {
    final Decl.Kind kind = Decl.Kind.values()[in.readInt()];
    final String name = in.readUTF();
    final Decl.Header header = readHeader(in);
    final List<Member> members = read(in, DeclCodec::readMember);
    final Ty type = readTy(in);
    final List<Decl> body = readDecls(in);
    return new Decl(kind, name, header, members, type, body, in.readInt());
  }

  private static void header(final DataOutputStream out, final Decl.Header header)
      throws IOException {
    list(out, header.typeParams(), (stream, text) -> stream.writeUTF(text));
    list(out, header.extendsTypes(), DeclCodec::ty);
    list(out, header.implementsTypes(), DeclCodec::ty);
    list(out, header.calls(), DeclCodec::sig);
    list(out, header.constructs(), DeclCodec::sig);
    list(out, header.decorators(), (stream, text) -> stream.writeUTF(text));
  }

  private static Decl.Header readHeader(final DataInputStream in) throws IOException {
    return new Decl.Header(
        read(in, DeclCodec::readText),
        read(in, DeclCodec::readTy),
        read(in, DeclCodec::readTy),
        read(in, DeclCodec::readSig),
        read(in, DeclCodec::readSig),
        read(in, DeclCodec::readText));
  }

  private static void member(final DataOutputStream out, final Member member) throws IOException {
    out.writeUTF(member.name());
    ty(out, member.type());
    list(out, member.sigs(), DeclCodec::sig);
    out.writeInt(member.flags());
    list(out, member.decorators(), (stream, text) -> stream.writeUTF(text));
  }

  private static Member readMember(final DataInputStream in) throws IOException {
    return new Member(
        in.readUTF(),
        readTy(in),
        read(in, DeclCodec::readSig),
        in.readInt(),
        read(in, DeclCodec::readText));
  }

  private static void sig(final DataOutputStream out, final Sig sig) throws IOException {
    list(out, sig.typeParams(), (stream, text) -> stream.writeUTF(text));
    list(out, sig.params(), DeclCodec::param);
    ty(out, sig.returns());
  }

  private static Sig readSig(final DataInputStream in) throws IOException {
    return new Sig(read(in, DeclCodec::readText), read(in, DeclCodec::readParam), readTy(in));
  }

  private static void param(final DataOutputStream out, final Param param) throws IOException {
    out.writeUTF(param.name());
    ty(out, param.type());
    out.writeBoolean(param.optional());
    out.writeBoolean(param.rest());
  }

  private static Param readParam(final DataInputStream in) throws IOException {
    return new Param(in.readUTF(), readTy(in), in.readBoolean(), in.readBoolean());
  }

  private static void ty(final DataOutputStream out, final Ty type) throws IOException {
    switch (type) {
      case Ty.Named named -> {
        out.writeByte(NAMED);
        out.writeUTF(named.name());
        list(out, named.args(), DeclCodec::ty);
      }
      case Ty.Union union -> tagged(out, UNION, union.parts());
      case Ty.Inter inter -> tagged(out, INTER, inter.parts());
      case Ty.Tuple tuple -> tagged(out, TUPLE, tuple.elements());
      case Ty.Arr arr -> {
        out.writeByte(ARR);
        ty(out, arr.element());
      }
      case Ty.Fn fn -> {
        out.writeByte(FN);
        sig(out, fn.sig());
      }
      case Ty.Obj obj -> {
        out.writeByte(OBJ);
        list(out, obj.members(), DeclCodec::member);
      }
      case Ty.Typeof typeOf -> out.writeByte(UNKNOWN);
      case Ty.Unknown unknown -> out.writeByte(UNKNOWN);
    }
  }

  private static void tagged(final DataOutputStream out, final int tag, final List<Ty> parts)
      throws IOException {
    out.writeByte(tag);
    list(out, parts, DeclCodec::ty);
  }

  private static Ty readTy(final DataInputStream in) throws IOException {
    final int tag = in.readByte();
    return switch (tag) {
      case NAMED -> new Ty.Named(in.readUTF(), read(in, DeclCodec::readTy), null);
      case UNION -> new Ty.Union(read(in, DeclCodec::readTy));
      case INTER -> new Ty.Inter(read(in, DeclCodec::readTy));
      case TUPLE -> new Ty.Tuple(read(in, DeclCodec::readTy));
      case ARR -> new Ty.Arr(readTy(in));
      case FN -> new Ty.Fn(readSig(in));
      case OBJ -> new Ty.Obj(read(in, DeclCodec::readMember));
      default -> Ty.UNKNOWN;
    };
  }
}
