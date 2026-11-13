package dev.ward.domain.apicompat;

import dev.ward.analysis.domain.api.ApiLocation;
import java.util.Objects;

/**
 * One incompatible change between two versions of an API.
 *
 * @param kind the kind, such as {@code member-removed}; the rule id is {@code api-compat:<kind>}
 * @param typeName the qualified name of the type
 * @param member the member name, or an empty text for a change of the type itself
 * @param before the signature at the baseline
 * @param after the signature in the working tree, or {@code removed}
 * @param location where to report it
 */
public record ApiChange(
    String kind,
    String typeName,
    String member,
    String before,
    String after,
    ApiLocation location) {

  /** The text of {@code after} for a declaration that no longer exists. */
  public static final String REMOVED = "removed";

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public ApiChange {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(typeName, "typeName");
    Objects.requireNonNull(member, "member");
    Objects.requireNonNull(before, "before");
    Objects.requireNonNull(after, "after");
    Objects.requireNonNull(location, "location");
  }

  /** Returns the message: the type, the member if any, and both signatures. */
  public String message() {
    final String owner = member.isEmpty() ? typeName : typeName + "#" + member;
    return owner + ": " + before + " -> " + after;
  }
}
