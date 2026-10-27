package io.github.asagnc.aurora.data;

import android.app.Instrumentation;
import android.os.Bundle;
import io.github.asagnc.aurora.core.SkillSignatures;
import io.github.asagnc.aurora.core.SkillSourceProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

/** Test-APK-only Java entrypoint: avoids AndroidX/Kotlin facade references that
 * a separately shrunk production APK is entitled to inline or remove. */
public final class R8SkillInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            verify();
            result.putString("stream", "R8_SKILL_SIGNATURE_SMOKE_PASS\n");
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("shortMsg", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            finish(0, result);
        }
    }

    private void verify() throws Exception {
        Path directory = Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "r8-skill-signature-");
        Provider[] providers = Security.getProviders();
        Path archive = directory.resolve("archive");
        try {
            String encoded = "302a300506032b6570032100d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a";
            byte[] key = new byte[44];
            for (int i = 0; i < key.length; i++) key[i] = (byte)Integer.parseInt(encoded.substring(i * 2, i * 2 + 2), 16);
            SkillSourceProfile profile = new SkillSourceProfile("00000000-0000-0000-0000-000000000001",
                "example/skills", null, Base64.getEncoder().encodeToString(key), "", false);
            Files.write(archive, "owned skill archive bytes\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String sidecar = "aurora-skill-ed25519-v1\nwDk1GqajP3hQJLkOrjdoIXCZMoj1/PELFtiF/oYsGQhFHXNxR6AF1EpruW+QGxbj/B3VUyyQ0iQI5mWb8iRHCQ==\n";
            SkillSignatures.INSTANCE.verify(archive, profile, sidecar);
            Files.write(archive, "tampered archive\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            boolean rejected = false;
            try { SkillSignatures.INSTANCE.verify(archive, profile, sidecar); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected || !Arrays.equals(providers, Security.getProviders())) throw new AssertionError("Signature/provider invariant");
        } finally { Files.deleteIfExists(archive); Files.delete(directory); }
    }
}
