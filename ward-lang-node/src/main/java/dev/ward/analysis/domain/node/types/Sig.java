package dev.ward.analysis.domain.node.types;

import java.util.List;

/**
 * A call, construct or method signature.
 *
 * @param typeParams the names of its own type parameters
 * @param params the parameters in order
 * @param returns the declared return type, or {@link TsType#UNKNOWN}
 */
public record Sig(List<String> typeParams, List<Param> params, TsType returns) {

  /** Copies the lists. */
  public Sig {
    typeParams = List.copyOf(typeParams);
    params = List.copyOf(params);
  }

  /** Returns whether the last parameter collects the remaining arguments. */
  public boolean isVarargs() {
    return !params.isEmpty() && params.get(params.size() - 1).rest();
  }

  /** Returns whether {@code count} arguments can be passed to this signature. */
  public boolean accepts(final int count) {
    final long required = params.stream().filter(p -> !p.optional() && !p.rest()).count();
    return count >= required && (isVarargs() || count <= params.size());
  }
}
