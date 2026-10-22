# 0.1.32 conversation recovery qualification

GT6SE/RMX3850, Android16/API36, existing FolkPatch Root authorization.
Application code33/name0.1.32 installed with `adb install -r`, preserving data.
Debug APK SHA256:
`4086f9f306620b4930441c2f160a6eaf1490e196be36ecc133e8036caa06f554`.

## Results

The exact installed APK passed15 tests:

- AgentLinuxDeviceTest5: production Agent tools for batch exit/output/artifact,
  interactive Unicode PTY, timeout, selected Root image publication; Root scope
  probe with attached logs; conversation-local recovery leaves another
  conversation's PTY running; explicit recovery and PTY completion both accept
  the same stopped proof and release the run lease.
- LinuxRunRegistryTest5: owner-filtered pending/count queries, concurrent duplicate
  completion, mismatched proof retention, duplicate-owner rollback, deleting-owner
  rejection and prior-schema preservation.
- CommandLogDeviceTest5: output observed before process exit, listener shutdown,
  persistent mappings for Android/Root/Linux commands and Linux environment probes,
  duplicate/path rejection, unrelated image-tool rejection and migration behavior.

Counts are test methods, not individual assertions or unique totals across prior
qualifications. Raw output: evidence/0.1.32/device-scoped-recovery.txt.
This run uses an in-process deterministic model fixture and the actual installed
Ubuntu Root generation. No network model call, real key or paid provider usage.

The recovery selector now queries only the Agent conversation via the existing
owner index. The settings page's explicit global recovery remains global. An
absent completed intent accepts duplicate stopped proof without touching any
other lease; an existing intent still requires the complete exact stored ticket.
This does not turn an unconfirmed native stop into successful completion.

## Retained state and checks

Fixture conversations, terminals and selected images cleaned by test finalizers.
Main application retained, test helper package removed, MainActivity reopened.
User data, shared Ubuntu image and existing Root authorization preserved.

Local core, Debug/test APK, Release/R8, lint and dual-ABI ELF/16KiB checks passed
before this run. No production changes were needed during qualification.
The preceding60fee70 CI39793142166 fully succeeded; f3f4e51 CI39794753012 was last
queued. The evidence-only follow-up does not establish its future outcome.

PRoot lifecycle, Root metadata GC, external Root revocation, public phone image
download and long-duration/performance/real-provider cache checks remain separate.
