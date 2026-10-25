# Aurora Java bridge build tool (development host only)

Pinned Frida Core/Node17.24.3 + official Java bridge7.0.13 + frida-compile19.1.0. package-lock.json fixes the full npm closure and integrity values. Node24+ is used only on the development/CI host; Android does not need Node, npm or Python.

From the repository root:

```text
npm ci --prefix tools/frida-bridge --ignore-scripts --no-audit --no-fund
python scripts/prepare-frida-node.py
npm run build --prefix tools/frida-bridge
```

The preparation adapter accepts only the pinned official Windows/Linux x64 N-API8 native binding, checks its SHA256/length and exact archive inventory, and never falls back to a local native source build. It does not run the upstream npm install hook. The Frida Node package still declares prebuild-install7.1.3 in its dependency metadata; that deprecated installer is unused by this build path, not an Android runtime dependency.

`build/java-bridge.js` is an ignored generated Frida bundle packaged by Gradle after checking bundle-inputs.json. Source maps are omitted and minification is enabled. The entrypoint installs `globalThis.Java`, emits initial bridge availability, and accepts exactly one `aurora:run` message containing the selected user script. Java availability is an observation at load time, not a qualification promise for every later target or spawn phase. Script evaluation exceptions remain Frida error events.

The packaged ARM64 native worker can now optionally post that control message from a second selected script file. The generated bridge acknowledges receipt and synchronous evaluation return; neither acknowledgement proves completion of asynchronous Java.perform work or the user's task. Root/Agent integration and license/source delivery are implemented. Android ART qualification remains unexecuted; each current Root service requires explicit matching native/Java self-probes with actual invocation events and independent cleanup proof. Successful compilation alone does not make Java hooking available.

Do not commit node_modules or generated native bindings/bundles. All downloads are development artifacts, not conversation/shared tools installed on the phone.
