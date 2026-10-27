# Only the opt-in instrumented Release needs these cross-APK test entrypoints.
# Preserve their contracts while still optimizing their bodies and BC internals.
-keep,allowoptimization class io.github.asagnc.aurora.core.SkillSignatures { public *; }
-keep,allowoptimization class io.github.asagnc.aurora.core.SkillSourceProfile { public *; }
