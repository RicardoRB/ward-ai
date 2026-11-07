package dev.ward.analysis.domain.node.types;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Helpers split out of {@link TypeCodec}. */
final class MemberCodec {

  private MemberCodec() {}

  static void header(final DataOutputStream out, final Decl.Header header) throws IOException {
    CodecIo.list(out, header.typeParams(), DataOutputStream::writeUTF);
    CodecIo.list(out, header.extendsTypes(), TypeCodec::ty);
    CodecIo.list(out, header.implementsTypes(), TypeCodec::ty);
    CodecIo.list(out, header.calls(), TypeCodec::sig);
    CodecIo.list(out, header.constructs(), TypeCodec::sig);
    CodecIo.list(out, header.decorators(), DataOutputStream::writeUTF);
  }

  static Decl.Header readHeader(final DataInputStream in) throws IOException {
    return new Decl.Header(
        CodecIo.read(in, CodecIo::readText),
        CodecIo.read(in, TypeCodec::readTy),
        CodecIo.read(in, TypeCodec::readTy),
        CodecIo.read(in, TypeCodec::readSig),
        CodecIo.read(in, TypeCodec::readSig),
        CodecIo.read(in, CodecIo::readText));
  }

  static void member(final DataOutputStream out, final Member member) throws IOException {
    out.writeUTF(member.name());
    TypeCodec.ty(out, member.type());
    CodecIo.list(out, member.sigs(), TypeCodec::sig);
    out.writeInt(member.flags());
    CodecIo.list(out, member.decorators(), DataOutputStream::writeUTF);
  }

  static Member readMember(final DataInputStream in) throws IOException {
    return new Member(
        in.readUTF(),
        TypeCodec.readTy(in),
        CodecIo.read(in, TypeCodec::readSig),
        in.readInt(),
        CodecIo.read(in, CodecIo::readText));
  }
}
