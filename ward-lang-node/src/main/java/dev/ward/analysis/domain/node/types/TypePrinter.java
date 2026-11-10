package dev.ward.analysis.domain.node.types;

import java.util.List;
import java.util.stream.Collectors;

/** Helpers split out of {@link TsType}. */
final class TypePrinter {

  private TypePrinter() {}

  static String printNamed(final TsType.Named named) {
    return named.args().isEmpty()
        ? named.name()
        : named.name() + "<" + join(named.args(), ", ") + ">";
  }

  static String join(final List<TsType> types, final String separator) {
    return types.stream()
        .map(
            type ->
                type instanceof TsType.Fn && !",".equals(separator.trim())
                    ? "(" + type.print() + ")"
                    : type.print())
        .collect(Collectors.joining(separator));
  }

  static String wrap(final TsType element) {
    final boolean parens =
        element instanceof TsType.Union
            || element instanceof TsType.Inter
            || element instanceof TsType.Fn;
    return parens ? "(" + element.print() + ")" : element.print();
  }

  /** Writes a signature with {@code arrow} between the parameters and the return type. */
  static String printSig(final Sig sig, final String arrow) {
    final String generics =
        sig.typeParams().isEmpty() ? "" : "<" + String.join(", ", sig.typeParams()) + ">";
    final String params =
        sig.params().stream().map(TypePrinter::printParam).collect(Collectors.joining(", "));
    return generics + "(" + params + ")" + arrow + sig.returns().print();
  }

  static String printParam(final Param param) {
    final String optional = param.optional() ? "?" : "";
    return (param.rest() ? "..." : "") + param.name() + optional + ": " + param.type().print();
  }

  static String printObject(final TsType.Obj obj) {
    if (obj.members().isEmpty()) {
      return "{}";
    }
    return obj.members().stream()
        .map(member -> printMember(member) + ";")
        .collect(Collectors.joining(" ", "{ ", " }"));
  }

  static String printMember(final Member member) {
    if (member.isMethod()) {
      return member.name() + printSig(member.sigs().get(0), ": ");
    }
    final String optional = member.has(Member.OPTIONAL) ? "?" : "";
    final String readonly = member.has(Member.READONLY) ? "readonly " : "";
    return readonly + member.name() + optional + ": " + member.type().print();
  }
}
