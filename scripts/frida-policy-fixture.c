/* Standalone qualification reuses the exact editor shipped in the worker.
 * No kernel policy loader is reachable from this wrapper. */
#include "policy_edit.h"
#include <string.h>

int main(int argc, char **argv) {
    if (argc == 3) {
        char *args[] = {"qualification", "policy-plan", argv[1], argv[2], "magisk", "untrusted_app"};
        return aurora_policy_command(6, args);
    }
    if (argc == 4 && !strcmp(argv[1], "verify-loaded")) {
        char *args[] = {"qualification", "policy-verify", argv[2], argv[3], "magisk", "untrusted_app"};
        return aurora_policy_command(6, args);
    }
    return 64;
}
