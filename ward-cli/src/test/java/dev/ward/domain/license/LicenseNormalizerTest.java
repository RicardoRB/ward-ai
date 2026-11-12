package dev.ward.domain.license;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.license.BundledLicenseCatalog;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LicenseNormalizerTest {
  private final LicenseNormalizer normalizer = new LicenseNormalizer(new BundledLicenseCatalog());

  static Stream<Arguments> spellings() {
    return Stream.of(
        Arguments.of("The Apache Software License, Version 2.0", "", "Apache-2.0"),
        Arguments.of("Apache License, Version 2.0", "", "Apache-2.0"),
        Arguments.of("", "https://www.apache.org/licenses/LICENSE-2.0.txt", "Apache-2.0"),
        Arguments.of("Whatever", "http://www.apache.org/licenses/LICENSE-2.0.txt", "Apache-2.0"),
        Arguments.of("GPL-3.0+", "", "GPL-3.0-or-later"),
        Arguments.of("GPL-2.0", "", "GPL-2.0-only"),
        Arguments.of("gpl-2.0+", "", "GPL-2.0-or-later"),
        Arguments.of("mit", "", "MIT"),
        Arguments.of("The MIT License", "", "MIT"),
        Arguments.of(
            "(MIT OR GPL-3.0) AND bsd-3-clause", "", "(MIT OR GPL-3.0-only) AND BSD-3-Clause"),
        Arguments.of(
            "GPL-2.0-only WITH classpath-exception-2.0",
            "",
            "GPL-2.0-only WITH Classpath-exception-2.0"),
        Arguments.of("LicenseRef-Custom", "", "LicenseRef-Custom"));
  }

  @ParameterizedTest
  @MethodSource("spellings")
  void normalizesToSpdx(final String name, final String url, final String expected) {
    final DeclaredLicense declared =
        new DeclaredLicense(List.of(new DeclaredLicense.Entry(name, url)));

    assertThat(normalizer.normalize(declared).map(LicenseExpression::render))
        .isEqualTo(Optional.of(expected));
  }

  @ParameterizedTest
  @MethodSource("unknown")
  void keepsUnmappableTextUnknown(final String name) {
    assertThat(normalizer.normalize(DeclaredLicense.of(name))).isEmpty();
  }

  static Stream<String> unknown() {
    return Stream.of(
        "See LICENSE file", "Proprietary", "UNLICENSED", "Apache-2.0+", "MIT WITH made-up", "");
  }

  @Test
  void severalDeclaredLicensesAreAlternatives() {
    final DeclaredLicense declared =
        new DeclaredLicense(
            List.of(
                new DeclaredLicense.Entry("MIT", ""), new DeclaredLicense.Entry("Apache-2.0", "")));

    assertThat(normalizer.normalize(declared).orElseThrow().render())
        .isEqualTo("MIT OR Apache-2.0");
  }

  @Test
  void oneUnmappableAlternativeMakesTheWholeLicenseUnknown() {
    final DeclaredLicense declared =
        new DeclaredLicense(
            List.of(new DeclaredLicense.Entry("MIT", ""), new DeclaredLicense.Entry("???", "")));

    assertThat(normalizer.normalize(declared)).isEmpty();
  }
}
