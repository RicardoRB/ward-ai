package dev.ward.application.init;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.function.Predicate;

/** Writes the member projects of a multi-package root, and undoes what it wrote. */
interface MemberWriter {
  /** The writer of a toolchain without members: it writes nothing and undoes nothing. */
  MemberWriter NONE =
      new MemberWriter() {
        @Override
        public void apply(
            final Project root,
            final List<InitGateOption> gates,
            final QualityPreset preset,
            final boolean replaceScripts,
            final Predicate<? super CompatibilityFinding> approval) {}

        @Override
        public List<String> kept() {
          return List.of();
        }

        @Override
        public void rollback() {}
      };

  /** Writes the members of {@code root}; does nothing for any other project. */
  void apply(
      Project root,
      List<InitGateOption> gates,
      QualityPreset preset,
      boolean replaceScripts,
      Predicate<? super CompatibilityFinding> approval);

  /** Returns the scripts the last {@link #apply} kept, for the summary. */
  List<String> kept();

  /** Undoes what {@link #apply} changed. */
  void rollback();
}
