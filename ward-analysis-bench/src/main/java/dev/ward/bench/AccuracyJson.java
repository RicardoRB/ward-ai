package dev.ward.bench;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders an {@link AccuracyResult} as an accuracy document of schema version 2, with {@code mode}
 * set to {@code accuracy}. Precision and recall are null when their denominator is zero.
 */
final class AccuracyJson {

  static final String ACCURACY_MODE = "accuracy";

  private AccuracyJson() {}

  static String render(final AccuracyResult result, final Instant generatedAt) {
    final Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", ResultJson.SCHEMA_VERSION);
    root.put("mode", ACCURACY_MODE);
    root.put("generatedAt", generatedAt.toString());
    root.put("corpora", corpora(result.corpora()));
    root.put("rules", rows(result.rules()));
    root.put("total", row(result.total()));
    return JsonDocument.toJson(root);
  }

  private static List<Object> corpora(final List<CorpusAccuracy> corpora) {
    return corpora.stream().map(AccuracyJson::corpus).map(entry -> (Object) entry).toList();
  }

  private static Map<String, Object> corpus(final CorpusAccuracy corpus) {
    final Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("name", corpus.name());
    entry.put("commit", corpus.commit());
    entry.put("rules", rows(corpus.rules()));
    entry.put("total", row(corpus.total()));
    return entry;
  }

  private static List<Object> rows(final List<RuleScore> scores) {
    return scores.stream().map(score -> (Object) row(score)).toList();
  }

  private static Map<String, Object> row(final RuleScore score) {
    final Map<String, Object> row = new LinkedHashMap<>();
    row.put("rule", score.rule());
    row.put("truePositives", score.truePositives());
    row.put("falsePositives", score.falsePositives());
    row.put("falseNegatives", score.falseNegatives());
    row.put("precision", score.precision());
    row.put("recall", score.recall());
    return row;
  }
}
