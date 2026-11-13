package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import dev.ward.analysis.domain.openapi.breaking.SchemaSites.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Finds what both documents have and a lifecycle can apply to. */
final class Subjects {

  private Subjects() {}

  /** The operations both documents have. */
  static List<Subject> operations(final DocumentPair pair) {
    return OperationPairs.each(
        pair,
        (base, now) ->
            List.of(
                new Subject(
                    base.node(), now.node(), OperationIndex.method(now), now.path(), "operation")));
  }

  /** The parameters both documents have, by place and name. */
  static List<Subject> parameters(final DocumentPair pair) {
    return OperationPairs.each(
        pair,
        (base, now) -> {
          final Map<String, DocNode> after = NamedParts.parameters(pair.revision(), now);
          final List<Subject> subjects = new ArrayList<>();
          NamedParts.parameters(pair.base(), base)
              .forEach(
                  (key, before) -> {
                    if (after.containsKey(key)) {
                      subjects.add(subject(now, before, after.get(key), "parameter"));
                    }
                  });
          return subjects;
        });
  }

  /** The properties below the request body schemas both documents have. */
  static List<Subject> requestProperties(final DocumentPair pair) {
    return properties(pair, SchemaSites.requestBodies(pair), Direction.REQUEST);
  }

  /** The properties below the response body schemas both documents have. */
  static List<Subject> responseProperties(final DocumentPair pair) {
    return properties(pair, SchemaSites.responseBodies(pair), Direction.RESPONSE);
  }

  private static List<Subject> properties(
      final DocumentPair pair, final List<Site> sites, final Direction direction) {
    final SchemaSides sides = new SchemaSides(pair.base(), pair.revision(), direction);
    final List<Subject> subjects = new ArrayList<>();
    for (final Site site : sites) {
      for (final SchemaDiff.Pair nested : SchemaDiff.nested(sides, site.base(), site.revision())) {
        subjects.add(subject(site.operation(), nested.base(), nested.revision(), "property"));
      }
    }
    return subjects;
  }

  private static Subject subject(
      final Operation operation, final DocNode base, final DocNode revision, final String what) {
    return new Subject(base, revision, OperationIndex.method(operation), operation.path(), what);
  }
}
