package dev.ward.infrastructure.license;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BundledLicenseDataTest {
  private final BundledLicenseCatalog catalog = new BundledLicenseCatalog();
  private final BundledLicenseTexts texts = new BundledLicenseTexts();

  @Test
  void everyAliasAndClassifierTargetIsAListedIdentifier() {
    final Set<String> listed = lowerCase(catalog.identifiers());

    assertThat(listed).containsAll(lowerCase(catalog.aliasTargets()));
    assertThat(listed).containsAll(lowerCase(catalog.classifierTargets()));
  }

  private static Set<String> lowerCase(final List<String> ids) {
    final Set<String> lower = new HashSet<>();
    ids.forEach(id -> lower.add(id.toLowerCase(Locale.ROOT)));
    return lower;
  }

  @Test
  void everyTextFileIsNamedByAnIdentifierAndIsIndexed() throws IOException {
    for (final String id : texts.ids()) {
      assertThat(catalog.id(id)).as(id).isPresent();
      assertThat(texts.text(id)).as(id).isPresent();
    }
    final String index = read("/dev/ward/license/texts/index.txt");
    assertThat(index.lines().toList()).hasSize(texts.ids().size());
  }

  @Test
  void headerNamesThePinnedSpdxListVersion() throws IOException {
    assertThat(read("/dev/ward/license/spdx-ids.txt")).startsWith("# SPDX License List 3.");
  }

  @Test
  void deprecatedIdentifiersMapToTheirCurrentForm() {
    assertThat(catalog.id("GPL-2.0")).hasValue("GPL-2.0-only");
    assertThat(catalog.id("gpl-2.0+")).hasValue("GPL-2.0-or-later");
    assertThat(catalog.id("LGPL-2.1")).hasValue("LGPL-2.1-only");
    assertThat(catalog.id("Apache-2.0")).hasValue("Apache-2.0");
    assertThat(catalog.id("Nope-9")).isEmpty();
  }

  @Test
  void aliasKeysIgnoreCaseSchemeAndTrailingSlash() {
    assertThat(catalog.alias("HTTPS://WWW.Apache.org/licenses/LICENSE-2.0.txt/"))
        .hasValue("Apache-2.0");
    assertThat(catalog.alias("  The   MIT   License ")).hasValue("MIT");
    assertThat(
            catalog.classifier(
                "License :: OSI Approved :: MIT License"
                    .substring("License :: OSI Approved :: ".length())))
        .hasValue("MIT");
  }

  @Test
  void familyTextServesOnlyAndOrLaterIdentifiers() {
    assertThat(texts.text("GPL-2.0-only")).isPresent();
    assertThat(texts.text("GPL-2.0-or-later")).isEqualTo(texts.text("GPL-2.0-only"));
    assertThat(texts.text("EPL-2.0")).isEmpty();
    assertThat(texts.ids()).containsAll(List.of("Apache-2.0", "MIT", "BSD-3-Clause", "ISC"));
  }

  private static String read(final String resource) throws IOException {
    try (InputStream stream = BundledLicenseDataTest.class.getResourceAsStream(resource)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
