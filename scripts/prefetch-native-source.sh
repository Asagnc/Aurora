#!/usr/bin/env bash
set -euo pipefail

# Avoid the observed Samba HTTP/2 REFUSED_STREAM failure; never change TLS or SHA.
aurora_native_dir="${RUNNER_TEMP:?}/aurora-native-downloads"
aurora_talloc_archive="$aurora_native_dir/talloc-2.5.0.tar.gz"
mkdir -p "$aurora_native_dir"
trap 'rm -f "$aurora_talloc_archive.partial"' EXIT
aurora_downloaded=false
for aurora_url in https://download.samba.org/pub/talloc/talloc-2.5.0.tar.gz \
  https://www.samba.org/ftp/talloc/talloc-2.5.0.tar.gz; do
  if curl --http1.1 --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
    --connect-timeout 20 --max-time 60 --retry 1 --retry-all-errors --retry-max-time 90 \
    "$aurora_url" --output "$aurora_talloc_archive.partial"; then
    aurora_downloaded=true
    break
  fi
done
if [[ "$aurora_downloaded" != true ]]; then exit 1; fi
printf '%s  %s\n' 912afa237510ae542a7733998eb18a12bcda35ab6729c8e2ddb43e8d0ebab007 \
  "$aurora_talloc_archive.partial" | sha256sum --check
mv "$aurora_talloc_archive.partial" "$aurora_talloc_archive"
printf 'AURORA_TALLOC_ARCHIVE=%s\n' "$aurora_talloc_archive" >>"${GITHUB_ENV:?}"
