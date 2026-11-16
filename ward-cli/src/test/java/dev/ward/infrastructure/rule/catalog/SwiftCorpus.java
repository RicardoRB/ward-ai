package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * One pinned open-source Swift project (MIT), read from its extracted source archive. The source is
 * never part of the repository and is only read: nothing in it is installed or run.
 *
 * @param name the short name that starts every path of the report
 * @param pin the tag or commit the committed report measures
 * @param archiveSha256 the SHA-256 of the GitHub source archive ({@code .tar.gz}) of that pin
 * @param folder the folder the archive extracts to
 */
record SwiftCorpus(String name, String pin, String archiveSha256, String folder) {
  /** Alamofire, a networking library with its XCTest suite. */
  static final SwiftCorpus ALAMOFIRE =
      new SwiftCorpus(
          "alamofire",
          "5.12.2",
          "866871d47bd878ed393090d2fd643d0cdcc3517f83931852da6bc17ec317115b",
          "Alamofire-5.12.2"
      );

  /** Apple's sample Food Truck, a SwiftUI application; the project has no tags. */
  static final SwiftCorpus FOOD_TRUCK =
      new SwiftCorpus(
          "sample-food-truck",
          "3954a769e99f3cc53297d94f2b960ceb2665b3d6",
          "d9041c035e9201dcb080de90226a9b5eb876b4a04db75ec321ae4674c0b4a86c",
          "sample-food-truck-3954a769e99f3cc53297d94f2b960ceb2665b3d6"
      );

  private static final String SWIFT = ".swift";

  /** Returns every corpus, in report order. */
  static List<SwiftCorpus> all() {
    return List.of(ALAMOFIRE, FOOD_TRUCK);
  }

  /**
   * Returns the Swift sources below this corpus's folder in {@code directory}, in path order; a
   * path is {@code <name>/<path in the archive>}.
   */
  List<SourceUnit> units(final Path directory) throws IOException {
    final Path root = directory.resolve(folder);
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.toString().endsWith(SWIFT))
          .sorted()
          .map(file -> unit(root, file))
          .toList();
    }
  }

  private SourceUnit unit(final Path root, final Path file) {
    try {
      return new SourceUnit(
          name + "/" + root.relativize(file).toString().replace('\\', '/'),
          AnalysisLanguage.SWIFT,
          Files.readString(file, StandardCharsets.UTF_8)
      );
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
