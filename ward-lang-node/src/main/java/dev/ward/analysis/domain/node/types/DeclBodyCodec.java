package dev.ward.analysis.domain.node.types;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Helpers split out of {@link DeclCodec}. */
final class DeclBodyCodec {

  private DeclBodyCodec() {}

  static void declList(final DataOutputStream out, final List<Decl> decls) throws IOException {
    CodecIo.list(out, decls, DeclBodyCodec::decl);
  }

  static List<Decl> readDecls(final DataInputStream in) throws IOException {
    return CodecIo.read(in, DeclBodyCodec::readDecl);
  }

  static void decl(final DataOutputStream out, final Decl decl) throws IOException {
    out.writeInt(decl.kind().ordinal());
    out.writeUTF(decl.name());
    MemberCodec.header(out, decl.header());
    CodecIo.list(out, decl.members(), MemberCodec::member);
    TypeCodec.ty(out, decl.type());
    declList(out, decl.body());
    out.writeInt(decl.flags());
  }

  static Decl readDecl(final DataInputStream in) throws IOException {
    final Decl.Kind kind = Decl.Kind.values()[in.readInt()];
    final String name = in.readUTF();
    final Decl.Header header = MemberCodec.readHeader(in);
    final List<Member> members = CodecIo.read(in, MemberCodec::readMember);
    final TsType type = TypeCodec.readTy(in);
    final List<Decl> body = readDecls(in);
    return new Decl(kind, name, header, members, type, body, in.readInt());
  }
}
