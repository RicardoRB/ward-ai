package dev.ward.application.gate.apicompat;

import dev.ward.domain.gate.RevisionFiles;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Chooses the baseline: the configured ref, else the highest release tag before {@code HEAD}. */
final class BaselineResolver {
  private static final Logger LOGGER = Logger.getLogger(BaselineResolver.class.getName());
  private static final String HEAD = "HEAD";
  private static final String HINT = "Fetch tags and history or set 'base' in [checks.api-compat].";
  private static final Pattern RELEASE = Pattern.compile("v?(\\d{1,9}(?:\\.\\d{1,9})+)");
  private static final String SEPARATOR = "\\.";

  private final RevisionFiles revisions;

  BaselineResolver(final RevisionFiles revisions) {
    this.revisions = revisions;
  }

  /**
   * Returns the baseline of the project at {@code root}.
   *
   * @param base the configured ref, or {@code null} for the latest release tag
   * @throws ApiCompatException if no baseline can be used
   */
  Baseline resolve(final Path root, final String base) {
    final String head =
        revisions
            .resolve(root, HEAD)
            .orElseThrow(
                () ->
                    new ApiCompatException(
                        "api-compat needs a Git repository with a commit, and "
                            + root
                            + " has none."));
    final Baseline baseline = base == null ? latestRelease(root, head) : configured(root, base);
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info(
          "event=api-compat-baseline ref="
              + baseline.ref()
              + " commit="
              + baseline.commit().substring(0, Math.min(baseline.commit().length(), 12)));
    }
    return baseline;
  }

  private Baseline configured(final Path root, final String base) {
    return new Baseline(
        base,
        revisions
            .resolve(root, base)
            .orElseThrow(
                () ->
                    new ApiCompatException(
                        "The base ref '" + base + "' of api-compat names no commit. " + HINT)));
  }

  private Baseline latestRelease(final Path root, final String head) {
    final List<String> atHead = revisions.tagsAt(root, head);
    final Optional<String> tag =
        revisions.tagsReachableFrom(root, head).stream()
            .filter(name -> RELEASE.matcher(name).matches() && !atHead.contains(name))
            .max(Comparator.comparing(BaselineResolver::version, BaselineResolver::compare));
    if (tag.isEmpty()) {
      final String clone = revisions.isShallow(root) ? " The repository is a shallow clone." : "";
      throw new ApiCompatException(
          "api-compat found no release tag (such as v1.2.3) before HEAD." + clone + " " + HINT);
    }
    return new Baseline(
        tag.get(),
        revisions
            .resolve(root, "refs/tags/" + tag.get())
            .orElseThrow(
                () ->
                    new ApiCompatException("The release tag " + tag.get() + " names no commit.")));
  }

  private static int[] version(final String tag) {
    final java.util.regex.Matcher matcher = RELEASE.matcher(tag);
    return matcher.matches()
        ? java.util.Arrays.stream(matcher.group(1).split(SEPARATOR))
            .mapToInt(Integer::parseInt)
            .toArray()
        : new int[0];
  }

  private static int compare(final int[] first, final int[] second) {
    for (int index = 0; index < Math.max(first.length, second.length); index++) {
      final int left = index < first.length ? first[index] : 0;
      final int right = index < second.length ? second[index] : 0;
      if (left != right) {
        return Integer.compare(left, right);
      }
    }
    return 0;
  }
}
