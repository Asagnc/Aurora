#!/usr/bin/env bash
set -euo pipefail

aurora_sdk_path="${RUNNER_TEMP:?}/aurora-sdk"
aurora_archive="${RUNNER_TEMP}/aurora-commandlinetools.zip"
aurora_url="https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip"
aurora_sha256="0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8"

curl --fail --silent --show-error --location "$aurora_url" --output "$aurora_archive"
printf '%s  %s\n' "$aurora_sha256" "$aurora_archive" | sha256sum --check
mkdir -p "$aurora_sdk_path/bootstrap" "$aurora_sdk_path/cmdline-tools"
unzip -q "$aurora_archive" -d "$aurora_sdk_path/bootstrap"
mv "$aurora_sdk_path/bootstrap/cmdline-tools" "$aurora_sdk_path/cmdline-tools/latest"
aurora_manager="$aurora_sdk_path/cmdline-tools/latest/bin/sdkmanager"

set +e
yes | "$aurora_manager" --sdk_root="$aurora_sdk_path" --licenses >"${RUNNER_TEMP}/aurora-sdk-licenses.log"
aurora_license_status=${PIPESTATUS[1]}
set -e
if [[ "$aurora_license_status" -ne 0 ]]; then
    cat "${RUNNER_TEMP}/aurora-sdk-licenses.log"
    exit "$aurora_license_status"
fi
"$aurora_manager" --sdk_root="$aurora_sdk_path" --channel=0 "platforms;android-37.0" "build-tools;36.0.0"
printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$aurora_sdk_path" "$aurora_sdk_path" >>"${GITHUB_ENV:?}"
