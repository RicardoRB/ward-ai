package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.types.ConstantValue;

/** Helpers split out of {@link FactBuilder}. */
final class ConstantFacts {

  private ConstantFacts() {}

  /** Returns the constant a literal type stands for, or null when the type is not a literal. */
  static ConstantValue of(final TsType type) {
    if (!(type instanceof TsType.Named named) || !named.args().isEmpty()) {
      return null;
    }
    final String name = named.name();
    if (name.startsWith("\"")) {
      return new ConstantValue(name.substring(1, name.length() - 1));
    }
    if ("true".equals(name) || "false".equals(name)) {
      return new ConstantValue(Boolean.parseBoolean(name));
    }
    return number(name);
  }

  private static ConstantValue number(final String text) {
    final String cleaned = text.replace("_", "");
    try {
      if (cleaned.matches("-?\\d+")) {
        return new ConstantValue(Long.parseLong(cleaned));
      }
      if (cleaned.matches("-?0[xX][0-9a-fA-F]+")) {
        return new ConstantValue(Long.decode(cleaned));
      }
      return cleaned.matches("-?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")
          ? new ConstantValue(Double.parseDouble(cleaned))
          : null;
    } catch (NumberFormatException overflow) {
      return null;
    }
  }
}
