package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a non-transient instance field of a serializable class whose type is known not to be
 * serializable: a well-known library type, or a class of the same file that is not serializable and
 * extends nothing. A class that defines {@code readObject} or {@code writeObject}, or that is
 * Externalizable, handles its own serialization and is not checked. A type that is not known is not
 * reported.
 */
public final class BadSerializableFieldCheck implements RuleCheck {

  private static final Set<String> NOT_SERIALIZABLE =
      Set.of(
          "Thread",
          "InputStream",
          "OutputStream",
          "Reader",
          "Writer",
          "FileInputStream",
          "FileOutputStream",
          "FileReader",
          "FileWriter",
          "BufferedReader",
          "BufferedWriter",
          "PrintStream",
          "PrintWriter",
          "Socket",
          "ServerSocket",
          "Process",
          "ProcessBuilder",
          "Connection",
          "Statement",
          "PreparedStatement",
          "ResultSet",
          "Optional",
          "Stream",
          "Iterator",
          "Scanner",
          "Matcher",
          "ExecutorService",
          "ThreadLocal",
          "Lock",
          "ReentrantLock",
          "Timer",
          "Logger");
  private static final Set<String> CUSTOM = Set.of("readObject", "writeObject");
  private static final String TRANSIENT = "transient";
  private static final String EXTERNALIZABLE = "Externalizable";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field type
   */
  public BadSerializableFieldCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> local = localNonSerializable(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final SyntaxNode clazz = visit.node();
      if (ClassHeaders.implementsSerializable(clazz) && !handlesItself(clazz)) {
        for (final SyntaxNode field : ClassMembers.fieldMembers(clazz)) {
          final String type = typeOf(field);
          if (isInstanceState(field) && (NOT_SERIALIZABLE.contains(type) || local.contains(type))) {
            matches.add(CheckSupport.at(field.span(), CheckSupport.fill(message, type, "")));
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isInstanceState(final SyntaxNode field) {
    final List<String> modifiers = MemberDeclarations.modifiers(field);
    return !modifiers.contains(TRANSIENT) && !modifiers.contains(MemberDeclarations.STATIC);
  }

  private static String typeOf(final SyntaxNode field) {
    final String text = ClassMembers.typeText(ClassMembers.declarationOf(field)).orElse("");
    return text.contains("[") ? "" : ClassHeaders.simple(text);
  }

  private static boolean handlesItself(final SyntaxNode clazz) {
    return ClassHeaders.implemented(clazz).contains(EXTERNALIZABLE)
        || ClassMembers.callableMembers(clazz).stream()
            .map(member -> MemberDeclarations.name(ClassMembers.declarationOf(member)))
            .anyMatch(CUSTOM::contains);
  }

  private static Set<String> localNonSerializable(final SyntaxNode root) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final SyntaxNode clazz = visit.node();
      if (!ClassHeaders.implementsSerializable(clazz)
          && ClassHeaders.extendsType(clazz).isEmpty()) {
        names.add(ClassHeaders.name(clazz));
      }
    }
    return names;
  }
}
