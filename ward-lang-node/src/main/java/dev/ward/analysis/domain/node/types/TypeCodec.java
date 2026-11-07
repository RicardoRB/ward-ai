package dev.ward.analysis.domain.node.types;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Helpers split out of {@link DeclCodec}. */
final class TypeCodec {
  private static final int NAMED = 0;
  private static final int UNION = 1;
  private static final int INTER = 2;
  private static final int ARR = 3;
  private static final int TUPLE = 4;
  private static final int FN = 5;
  private static final int OBJ = 6;
  private static final int UNKNOWN = 7;

  private TypeCodec() {}

  static void sig(final DataOutputStream out, final Sig sig) throws IOException {
    CodecIo.list(out, sig.typeParams(), DataOutputStream::writeUTF);
    CodecIo.list(out, sig.params(), TypeCodec::param);
    ty(out, sig.returns());
  }

  static Sig readSig(final DataInputStream in) throws IOException {
    return new Sig(
        CodecIo.read(in, CodecIo::readText), CodecIo.read(in, TypeCodec::readParam), readTy(in));
  }

  static void param(final DataOutputStream out, final Param param) throws IOException {
    out.writeUTF(param.name());
    ty(out, param.type());
    out.writeBoolean(param.optional());
    out.writeBoolean(param.rest());
  }

  static Param readParam(final DataInputStream in) throws IOException {
    return new Param(in.readUTF(), readTy(in), in.readBoolean(), in.readBoolean());
  }

  static void ty(final DataOutputStream out, final TsType type) throws IOException {
    switch (type) {
      case TsType.Named named -> {
        out.writeByte(NAMED);
        out.writeUTF(named.name());
        CodecIo.list(out, named.args(), TypeCodec::ty);
      }
      case TsType.Union union -> tagged(out, UNION, union.parts());
      case TsType.Inter inter -> tagged(out, INTER, inter.parts());
      case TsType.Tuple tuple -> tagged(out, TUPLE, tuple.elements());
      case TsType.Arr arr -> {
        out.writeByte(ARR);
        ty(out, arr.element());
      }
      case TsType.Fn fn -> {
        out.writeByte(FN);
        sig(out, fn.sig());
      }
      case TsType.Obj obj -> {
        out.writeByte(OBJ);
        CodecIo.list(out, obj.members(), MemberCodec::member);
      }
      case TsType.Typeof typeOf -> out.writeByte(UNKNOWN);
      case TsType.Unknown unknown -> out.writeByte(UNKNOWN);
    }
  }

  static void tagged(final DataOutputStream out, final int tag, final List<TsType> parts)
      throws IOException {
    out.writeByte(tag);
    CodecIo.list(out, parts, TypeCodec::ty);
  }

  static TsType readTy(final DataInputStream in) throws IOException {
    final int tag = in.readByte();
    return switch (tag) {
      case NAMED -> new TsType.Named(in.readUTF(), CodecIo.read(in, TypeCodec::readTy), null);
      case UNION -> new TsType.Union(CodecIo.read(in, TypeCodec::readTy));
      case INTER -> new TsType.Inter(CodecIo.read(in, TypeCodec::readTy));
      case TUPLE -> new TsType.Tuple(CodecIo.read(in, TypeCodec::readTy));
      case ARR -> new TsType.Arr(readTy(in));
      case FN -> new TsType.Fn(readSig(in));
      case OBJ -> new TsType.Obj(CodecIo.read(in, MemberCodec::readMember));
      default -> TsType.UNKNOWN;
    };
  }
}
