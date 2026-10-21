#!/bin/bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

version=$(./gradlew -q runtime:properties --property version | awk '/^version:/ { print $2 }')
if [[ -z "$version" ]]; then
    echo "Could not determine project version" >&2
    exit 1
fi

echo "Project version: $version"

archive="$PWD/jawawasm-$version.zip"
rm -f -- "$archive"
./gradlew clean
./gradlew publishMavenJavaPublicationToMavenRepository

for module in runtime format-core format engine; do
    (
        cd "$module/build/repo"
        zip -r "$archive" .
    )
done
