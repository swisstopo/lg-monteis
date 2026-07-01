#!/bin/bash

INPUT_FILE=$1
OUTPUT_FILE=$2

# Check if the file actually contains vulnerabilities (Total is 1 or more)
if grep -q -E "Total: [1-9][0-9]*" "$INPUT_FILE"; then
  echo "### Trivy Security Scan Results" > "$OUTPUT_FILE"
  echo "> [!WARNING]" >> "$OUTPUT_FILE"
  echo "> **Found non-critical issues:** Found LOW or MEDIUM vulnerabilities." >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
  echo "<details><summary>Click to review.</summary>" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
  echo '```text' >> "$OUTPUT_FILE"

  cat "$INPUT_FILE" >> "$OUTPUT_FILE"

  # Force a newline after the file contents
  echo "" >> "$OUTPUT_FILE"
  echo '```' >> "$OUTPUT_FILE"

  echo "</details>" >> "$OUTPUT_FILE"
else
  echo "### Trivy Security Scan Results" > "$OUTPUT_FILE"
  echo "> [!TIP]" >> "$OUTPUT_FILE"
  echo "> **Perfect Scan!** No vulnerabilities found at any severity level." >> "$OUTPUT_FILE"
fi