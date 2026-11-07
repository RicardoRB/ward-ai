package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a type from the annotations on it and on its members, as Error Prone's Dagger, JUnit and
 * Android checks do. A type is selected when every {@code class-has} pattern matches one of its
 * annotations, no {@code class-lacks} pattern matches an annotation of it (or of an enclosing type,
 * when asked), and the number of members whose annotations match {@code members} lies within the
 * bounds. The patterns run over the compact annotation text, such as
 * {@code @RunWith(Theories.class)}. An annotation that is written differently is not matched, so
 * the check stays silent.
 */
public final class AnnotationStructureCheck implements RuleCheck {

  private static final Map<String, String> KEYWORDS =
      Map.of(
          "classDeclaration", "class",
          "interfaceDeclaration", "interface",
          "enumDeclaration", "enum",
          "recordDeclaration", "record",
          "annotationTypeDeclaration", "@interface");
  private static final Set<String> BODIES =
      Set.of("classBody", "interfaceBody", "recordBody", "enumBodyDeclarations");
  private static final Set<String> MEMBERS =
      Set.of("classBodyDeclaration", "interfaceBodyDeclaration");
  private static final Set<String> WRAPPERS =
      Set.of("memberDeclaration", "interfaceMemberDeclaration");
  private static final String ANNOTATION_MARK = "@";

  private final Criteria criteria;
  private final String message;

  /**
   * What a reported type meets.
   *
   * @param types keywords of the declarations that are checked, empty for every kind
   * @param classHas patterns that each match an annotation of the type
   * @param classLacks a pattern no annotation of the type may match, or {@code null}
   * @param enclosing whether the annotations of enclosing types count for {@code classLacks}
   * @param members a pattern an annotation of a member matches, or {@code null} for no condition
   * @param memberKind part of the kind of the member's declaration, such as {@code method}
   * @param least the fewest such members
   * @param most the most such members, negative for no limit
   * @param reportMembers whether each counted member is reported instead of the type
   */
  public record Criteria(
      Set<String> types,
      List<Pattern> classHas,
      Pattern classLacks,
      boolean enclosing,
      Pattern members,
      String memberKind,
      int least,
      int most,
      boolean reportMembers) {

    /** Copies the collections. */
    public Criteria {
      types = Set.copyOf(types);
      classHas = List.copyOf(classHas);
      Objects.requireNonNull(memberKind, "memberKind");
    }
  }

  /**
   * Creates the check.
   *
   * @param criteria what a reported type meets
   * @param message the message reported at the type or member
   */
  public AnnotationStructureCheck(final Criteria criteria, final String message) {
    this.criteria = Objects.requireNonNull(criteria, "criteria");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, KEYWORDS.keySet())) {
      final SyntaxNode type = visit.node();
      if (selects(KEYWORDS.get(type.kind())) && ownAnnotationsMeet(visit)) {
        report(type, counted(type), matches);
      }
    }
    return List.copyOf(matches);
  }

  private boolean selects(final String keyword) {
    return criteria.types().isEmpty() || criteria.types().contains(keyword);
  }

  private boolean ownAnnotationsMeet(final SyntaxWalk.Visit visit) {
    final List<String> own = annotationsOf(visit.ancestors(), false);
    final List<String> lacking = annotationsOf(visit.ancestors(), criteria.enclosing());
    return criteria.classHas().stream().allMatch(pattern -> anyMatch(own, pattern))
        && (criteria.classLacks() == null || !anyMatch(lacking, criteria.classLacks()));
  }

  private static List<String> annotationsOf(final List<SyntaxNode> ancestors, final boolean all) {
    final List<String> texts = new ArrayList<>();
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode ancestor = ancestors.get(index);
      if (MEMBERS.contains(ancestor.kind()) || "typeDeclaration".equals(ancestor.kind())) {
        texts.addAll(annotations(ancestor));
        if (!all) {
          break;
        }
      }
    }
    return texts;
  }

  private static List<String> annotations(final SyntaxNode node) {
    return MemberDeclarations.modifiers(node).stream()
        .filter(text -> text.startsWith(ANNOTATION_MARK))
        .toList();
  }

  private static boolean anyMatch(final List<String> texts, final Pattern pattern) {
    return texts.stream().anyMatch(text -> pattern.matcher(text).find());
  }

  private List<SyntaxNode> counted(final SyntaxNode type) {
    if (criteria.members() == null) {
      return List.of();
    }
    return type.children().stream()
        .filter(child -> BODIES.contains(child.kind()))
        .flatMap(body -> body.children().stream())
        .filter(child -> MEMBERS.contains(child.kind()))
        .filter(member -> anyMatch(annotations(member), criteria.members()))
        .filter(this::ofKind)
        .toList();
  }

  private boolean ofKind(final SyntaxNode member) {
    return criteria.memberKind().isEmpty()
        || member.children().stream()
            .filter(child -> WRAPPERS.contains(child.kind()))
            .flatMap(wrapper -> wrapper.children().stream())
            .findFirst()
            .filter(
                declaration ->
                    declaration
                        .kind()
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains(criteria.memberKind()))
            .isPresent();
  }

  private void report(
      final SyntaxNode type, final List<SyntaxNode> members, final List<RuleMatch> out) {
    final boolean withinBounds =
        members.size() >= criteria.least()
            && (criteria.most() < 0 || members.size() <= criteria.most());
    if (!withinBounds) {
      return;
    }
    if (criteria.reportMembers()) {
      members.forEach(member -> out.add(CheckSupport.at(member.span(), message)));
    } else {
      out.add(CheckSupport.at(type.span(), message));
    }
  }
}
