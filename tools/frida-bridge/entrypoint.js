import Java from "frida-java-bridge";

// One explicit native control message supplies the selected user script after bridge load.
globalThis.Java = Java;
send({ aurora: "java_bridge_ready", version: "7.0.13", available: Java.available });
recv("aurora:run", (message) => {
  if (typeof message.source !== "string" || message.source.length > 1048576) {
    throw new Error("invalid_aurora_script");
  }
  send({ aurora: "user_script_received" });
  Script.evaluate("/aurora/agent.js", message.source);
  send({ aurora: "user_script_evaluated" });
});
