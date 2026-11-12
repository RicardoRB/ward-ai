package dev.ward.infrastructure.license.python;

import static dev.ward.infrastructure.license.LicenseReaderFixtures.dependency;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.project;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.infrastructure.license.BundledLicenseCatalog;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PythonLicenseMetadataTest {
  private static final String VERSION = "1.0";

  private static final String SITE = ".venv/lib/python3.12/site-packages/";

  @TempDir Path root;

  private LicenseLookup lookup(final String virtualEnv, final String name, final String version) {
    return new PythonLicenseMetadata(new BundledLicenseCatalog(), virtualEnv)
        .lookup(
            project(root, Language.PYTHON, BuildTool.UV),
            "",
            dependency(Ecosystem.PYPI, name, version));
  }

  private void metadata(final String directory, final String headers) throws IOException {
    write(root, SITE + directory + "/METADATA", "Metadata-Version: 2.4\n" + headers + "\n\nBody\n");
  }

  @Test
  void prefersTheLicenseExpression() throws IOException {
    metadata(
        "requests-2.32.3.dist-info",
        "Name: requests\nVersion: 2.32.3\nLicense-Expression: Apache-2.0\nLicense: ignored\n"
            + "Classifier: License :: OSI Approved :: MIT License");

    assertThat(lookup(null, "requests", "2.32.3").license().describe()).isEqualTo("Apache-2.0");
  }

  @Test
  void usesAShortRecognizableLicenseField() throws IOException {
    metadata("pkg-1.0.dist-info", "Name: pkg\nVersion: 1.0\nLicense: MIT License");

    assertThat(lookup(null, "pkg", VERSION).license().describe()).isEqualTo("MIT License");
  }

  @Test
  void fallsBackToClassifiersWhenTheLicenseFieldIsLongText() throws IOException {
    metadata(
        "pkg-1.0.dist-info",
        "Name: pkg\nVersion: 1.0\nLicense: Permission is hereby granted, free of charge, to any person"
            + " obtaining a copy of this software and associated documentation files\n"
            + "Classifier: Programming Language :: Python\n"
            + "Classifier: License :: OSI Approved :: MIT License");

    assertThat(lookup(null, "pkg", VERSION).license().describe()).isEqualTo("MIT");
  }

  @Test
  void severalClassifiersAreAlternatives() throws IOException {
    metadata(
        "pkg-1.0.dist-info",
        "Name: pkg\nVersion: 1.0\nClassifier: License :: OSI Approved :: MIT License\n"
            + "Classifier: License :: OSI Approved :: Apache Software License");

    assertThat(lookup(null, "pkg", VERSION).license().entries()).hasSize(2);
  }

  @Test
  void aBareOsiClassifierIsNotALicense() throws IOException {
    metadata("pkg-1.0.dist-info", "Name: pkg\nVersion: 1.0\nClassifier: License :: OSI Approved");

    assertThat(lookup(null, "pkg", VERSION).reason())
        .isEqualTo(LicenseLookup.Reason.NO_LICENSE_DECLARED);
  }
}
