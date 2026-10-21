#!/usr/bin/env bash
set -euo pipefail

task_rootfs_temp=$(mktemp -d "${RUNNER_TEMP:-/tmp}/aurora-rootfs.XXXXXX")
task_rootfs_url=https://cdimage.ubuntu.com/ubuntu-base/releases/26.10/release
task_rootfs_sha=8b7ea4ed71a92873f7f4e260a3defe0d9ed85cb69c0155b12414208f79fa0f2d
task_rootfs_key=843938DF228D22F7B3742BC0D94AA3F0EFE21092
mkdir -m 700 "$task_rootfs_temp/keyring"
for task_rootfs_name in SHA256SUMS SHA256SUMS.gpg ubuntu-base-26.10-base-arm64.tar.gz; do
  curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
    --max-time 180 --retry 2 "$task_rootfs_url/$task_rootfs_name" --output "$task_rootfs_temp/$task_rootfs_name"
done
gpg --homedir "$task_rootfs_temp/keyring" --batch --import config/ubuntu-cdimage-2012.asc
gpg --homedir "$task_rootfs_temp/keyring" --batch --status-fd 1 \
  --verify "$task_rootfs_temp/SHA256SUMS.gpg" "$task_rootfs_temp/SHA256SUMS" >"$task_rootfs_temp/signature.txt"
awk -v expected="$task_rootfs_key" '$2 == "VALIDSIG" && $3 == expected {valid=1} END {exit !valid}' "$task_rootfs_temp/signature.txt"
awk -v expected="$task_rootfs_sha" '$1 == expected && $2 == "*ubuntu-base-26.10-base-arm64.tar.gz" {valid=1} END {exit !valid}' "$task_rootfs_temp/SHA256SUMS"
printf '%s  %s\n' "$task_rootfs_sha" "$task_rootfs_temp/ubuntu-base-26.10-base-arm64.tar.gz" | sha256sum --check
AURORA_ROOTFS_SAMPLE="$task_rootfs_temp/ubuntu-base-26.10-base-arm64.tar.gz" \
  ./gradlew :core:test --rerun-tasks --console=plain
