package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a class method or accessor whose name and static-ness repeat an earlier member, like
 * ESLint {@code no-dupe-class-members}. A getter and a setter of one name are a valid pair; any
 * other repetition is reported at the name of the later member.
 */
public final class NodeDupeClassMembersCheck implements RuleCheck {

  private static final String CLASS_ELEMENT = "classElement";
  private static final String METHOD = "methodDefinition";
  private static final String PROPERTY_MEMBER = "propertyMemberDeclaration";
  private static final String GET_ACCESSOR = "getAccessor";
  private static final String SET_ACCESSOR = "setAccessor";
  private static final String PROPERTY_NAME = "propertyName";
  private static final String CALL_SIGNATURE = "callSignature";
  private static final int NAME_DEPTH = 3;
  private static final String GETTER = "getter";
  private static final String SETTER = "setter";
  private static final String NAME = "classElementName";
  private static final String STATIC = "static";
  private static final String QUOTES = "'\"";

  private enum Kind {
    METHOD,
    GET,
    SET
  }

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding, with {@code {value}} as the name
   */
  public NodeDupeClassMembersCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    for (final SyntaxNode child : node.children()) {
      collect(child, matches);
    }
    if (isClassBody(node)) {
      checkBody(node, matches);
    }
  }

  private static boolean isClassBody(final SyntaxNode node) {
    return node.children().stream().anyMatch(child -> CLASS_ELEMENT.equals(child.kind()));
  }

  private void checkBody(final SyntaxNode body, final List<RuleMatch> matches) {
    final Map<String, Set<Kind>> seen = new HashMap<>();
    for (final SyntaxNode element : body.children()) {
      if (!CLASS_ELEMENT.equals(element.kind())) {
        continue;
      }
      member(element)
          .ifPresent(
              member -> {
                final String key = (member.isStatic() ? STATIC + " " : "") + member.name();
                final Set<Kind> kinds = seen.computeIfAbsent(key, k -> EnumSet.noneOf(Kind.class));
                if (repeats(kinds, member.kind())) {
                  matches.add(
                      new RuleMatch(member.nameSpan(), message.replace("{value}", member.name())));
                }
                kinds.add(member.kind());
              });
    }
  }

  /** A repetition is valid only as the second half of a getter and setter pair. */
  private static boolean repeats(final Set<Kind> seen, final Kind kind) {
    if (seen.isEmpty()) {
      return false;
    }
    if (seen.contains(Kind.METHOD) || kind == Kind.METHOD) {
      return true;
    }
    return seen.contains(kind) || seen.size() > 1;
  }

  private static Optional<Member> member(final SyntaxNode element) {
    final Optional<SyntaxNode> method =
        element.children().stream().filter(NodeDupeClassMembersCheck::isMember).findFirst();
    if (method.isEmpty()) {
      return Optional.empty();
    }
    final boolean isStatic = isStatic(element) || isStatic(method.get());
    final Optional<SyntaxNode> name = nameOf(method.get(), NAME_DEPTH);
    if (name.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Member(
            unquote(LeafText.of(name.get())), name.get().span(), isStatic, kindOf(method.get())));
  }

  /**
   * A method or accessor: a method definition, or a TypeScript member with a signature or accessor.
   */
  private static boolean isMember(final SyntaxNode child) {
    return METHOD.equals(child.kind())
        || PROPERTY_MEMBER.equals(child.kind())
            && child.children().stream().anyMatch(NodeDupeClassMembersCheck::isSignatureOrAccessor);
  }

  private static boolean isSignatureOrAccessor(final SyntaxNode grand) {
    return CALL_SIGNATURE.equals(grand.kind())
        || GET_ACCESSOR.equals(grand.kind())
        || SET_ACCESSOR.equals(grand.kind());
  }

  private static boolean isStatic(final SyntaxNode node) {
    return node.children().stream().anyMatch(child -> STATIC.equals(LeafText.of(child)));
  }

  /** The name of a member, searched within a few levels so that a body is never read. */
  private static Optional<SyntaxNode> nameOf(final SyntaxNode node, final int depth) {
    for (final SyntaxNode child : node.children()) {
      if (NAME.equals(child.kind()) || PROPERTY_NAME.equals(child.kind())) {
        return Optional.of(child);
      }
    }
    if (depth == 0) {
      return Optional.empty();
    }
    return node.children().stream()
        .map(child -> nameOf(child, depth - 1))
        .flatMap(Optional::stream)
        .findFirst();
  }

  private static Kind kindOf(final SyntaxNode method) {
    for (final SyntaxNode child : method.children()) {
      if (GETTER.equals(child.kind()) || GET_ACCESSOR.equals(child.kind())) {
        return Kind.GET;
      }
      if (SETTER.equals(child.kind()) || SET_ACCESSOR.equals(child.kind())) {
        return Kind.SET;
      }
    }
    return Kind.METHOD;
  }

  private static String unquote(final String text) {
    return text.length() >= 2
            && QUOTES.indexOf(text.charAt(0)) >= 0
            && text.charAt(text.length() - 1) == text.charAt(0)
        ? text.substring(1, text.length() - 1)
        : text;
  }

  /** A class member: its name, where the name is, whether it is static, and its kind. */
  private record Member(String name, Span nameSpan, boolean isStatic, Kind kind) {}
}
