#!/usr/bin/env sh
set -eu

OUT="${TMPDIR:-/tmp}/property-upload-screening-classes"
mkdir -p "$OUT"
javac -d "$OUT" $(find src/main/java -name '*.java')
java -cp "$OUT" dev.learning.property.PropertyUploadServer
