package dev.ward.infrastructure.license.swift;

import static dev.ward.infrastructure.license.LicenseReaderFixtures.dependency;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.project;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.license.LicenseTexts;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.infrastructure.license.BundledLicenseTexts;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SwiftLicenseMetadataTest {
  private static final String CHECKOUT = ".build/checkouts/swift-nio/";
  private static final String NAME = "github.com/apple/swift-nio";

  @TempDir Path root;

  private final LicenseTexts texts = new BundledLicenseTexts();

  private LicenseLookup lookup() {
    return new SwiftLicenseMetadata(texts)
        .lookup(
            project(root, Language.SWIFT, BuildTool.SWIFTPM),
            "",
            dependency(Ecosystem.SWIFT_URL, NAME, "2.0.0"));
  }

  @Test
  void matchesTheApacheTextOfALicenseFile() throws IOException {
    write(root, CHECKOUT + "LICENSE", texts.text("Apache-2.0").orElseThrow());

    final LicenseLookup result = lookup();

    assertThat(result.license().describe()).isEqualTo("Apache-2.0");
    assertThat(result.detail()).isEqualTo("license-file");
  }

  @Test
  void matchesAnMitFileWithItsOwnCopyrightLine() throws IOException {
    write(
        root,
        CHECKOUT + "LICENSE.md",
        texts.text("MIT").orElseThrow().replace("<year> <copyright holders>", "2021 Someone"));

    assertThat(lookup().license().describe()).isEqualTo("MIT");
  }

  @Test
  void aHeaderOfALongTextIsEnoughButAChangedShortTextIsNot() throws IOException {
    write(
        root,
        CHECKOUT + "COPYING",
        texts.text("GPL-3.0").orElseThrow() + "\nAn addendum the standard text lacks.\n");
    assertThat(lookup().license().describe()).isEqualTo("GPL-3.0");

    write(
        root,
        CHECKOUT + "COPYING",
        "MIT License\n\nPermission is hereby granted, but only for non-commercial use.\n");
    assertThat(lookup().reason()).isEqualTo(LicenseLookup.Reason.UNRECOGNIZED);
  }

  @Test
  void anUnmatchedTextIsUnrecognizedAndNamesTheFile() throws IOException {
    write(root, CHECKOUT + "LICENSE.txt", "All rights reserved. Do whatever we say.");

    final LicenseLookup result = lookup();

    assertThat(result.reason()).isEqualTo(LicenseLookup.Reason.UNRECOGNIZED);
    assertThat(result.detail()).contains("LICENSE.txt");
  }

  @Test
  void aCheckoutWithoutALicenseFileDeclaresNone() throws IOException {
    write(root, CHECKOUT + "Package.swift", "// swift-tools-version: 5.9");

    assertThat(lookup().reason()).isEqualTo(LicenseLookup.Reason.NO_LICENSE_DECLARED);
  }

  @Test
  void aMissingCheckoutIsNotInstalled() {
    assertThat(lookup().reason()).isEqualTo(LicenseLookup.Reason.NOT_INSTALLED);
  }

  @Test
  void findsAnXcodeSourcePackagesCheckoutIgnoringCase() throws IOException {
    write(root, "SourcePackages/checkouts/Swift-NIO/LICENSE", texts.text("ISC").orElseThrow());

    assertThat(lookup().license().describe()).isEqualTo("ISC");
  }
}
