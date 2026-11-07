package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports each interface that declares fields but no methods: it only holds constants and does not
 * describe a type. A marker interface, with no members at all, is reported too unless the check
 * allows marker interfaces.
 */
public final class InterfaceIsTypeCheck implements RuleCheck {

  private static final String INTERFACE = "interfaceDeclaration";
  private static final String FIELD = "constDeclaration";
  private static final String METHOD_SUFFIX = "MethodDeclaration";

  private final boolean allowMarkerInterfaces;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowMarkerInterfaces whether an interface without any member is a legal type
   * @param message the message; {@code {value}} is the interface name
   */
  public InterfaceIsTypeCheck(final boolean allowMarkerInterfaces, final String message) {
    this.allowMarkerInterfaces = allowMarkerInterfaces;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : DesignMembers.types(root)) {
      final SyntaxNode type = visit.node();
      if (INTERFACE.equals(type.kind()) && isNotType(type)) {
        matches.add(
            CheckSupport.at(type.span(), CheckSupport.fill(message, JUnitMembers.name(type), "")));
      }
    }
    return List.copyOf(matches);
  }

  private boolean isNotType(final SyntaxNode type) {
    final List<DesignMembers.Member> members =
        DesignMembers.body(type).map(DesignMembers::members).orElse(List.of());
    final boolean hasMethod =
        members.stream().anyMatch(member -> member.declaration().kind().endsWith(METHOD_SUFFIX));
    final boolean hasField =
        members.stream().anyMatch(member -> FIELD.equals(member.declaration().kind()));
    return !hasMethod && (hasField || (!allowMarkerInterfaces && members.isEmpty()));
  }
}
