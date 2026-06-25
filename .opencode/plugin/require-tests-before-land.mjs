// OpenCode plugin: require-tests-before-land adapter.
//
// The policy lives in hooks/require-tests-before-land.clj (Babashka). This
// module is the runtime adapter OpenCode loads: it normalizes the OpenCode tool
// event into the {command, cwd, transcript} shape the Clojure policy reads,
// shells out to bb, and denies when the policy denies.
//
// Fail-safe: any error or uncertainty allows. It never blocks work and never
// throws, so a wrong field name cannot break OpenCode.
//
// Intended wiring (bootstrap-project writes this when the hook is armed):
//   - an opencode.json permission rule that turns push/merge into "ask", e.g.
//     "permission": { "bash": { "git push*": "ask", "jj git push*": "ask",
//                               "git merge*": "ask", "*": "allow" } }
//   - this module registered under "plugin".
// The permission rule triggers the ask; this hook resolves it from the policy.
//
// NOTE: OpenCode's permission.ask input/output field names are not fully
// documented at time of writing. The handler inspects several shapes and writes
// the denial on best-effort fields. Confirm against the running OpenCode
// version (see the two marked lines) and adjust if needed.

import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
// .opencode/plugin/x.mjs -> repo root -> hooks/require-tests-before-land.clj
const script = path.join(here, "..", "..", "hooks", "require-tests-before-land.clj");

function runPolicy({ command, cwd, transcript }) {
  return new Promise((resolve) => {
    const payload = JSON.stringify({
      command: command || "",
      cwd: cwd || "",
      transcript: transcript || "",
    });
    const child = execFile("bb", [script], { cwd: cwd || process.cwd() }, (err, stdout) => {
      if (err) return resolve({ allow: true });
      try {
        resolve(JSON.parse(stdout.trim()));
      } catch {
        resolve({ allow: true });
      }
    });
    child.stdin.end(payload);
  });
}

function extractCommand(evt) {
  return evt?.input?.command || evt?.tool_input?.command || evt?.command || "";
}

export default async () => ({
  "permission.ask": async (evt, output) => {
    try {
      const command = extractCommand(evt);
      if (!command) return; // not a bash command we can see; allow
      const cwd = evt?.cwd || process.cwd();
      const transcript = evt?.transcript_path || evt?.transcript || "";
      const decision = await runPolicy({ command, cwd, transcript });
      if (decision && decision.allow === false) {
        output.decision = "deny"; // <-- confirm field name against OpenCode
        output.reason = decision.reason; // <-- confirm field name against OpenCode
      }
    } catch {
      // fail safe: allow
    }
  },
});
