-keep class io.github.asagnc.aurora.root.AuroraRootService { public <init>(); }
# SkillArchive accepts only ZIP STORED/DEFLATED before opening entry streams.
# Commons Compress's optional Zstandard/XZ codecs are deliberately not packaged.
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn org.tukaani.xz.MemoryLimitException
-dontwarn org.tukaani.xz.SingleXZInputStream
-dontwarn org.tukaani.xz.XZInputStream
