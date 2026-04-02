#!/bin/bash
# validate-pas-profiles.sh
# Standalone FHIR Validator script for PAS profile validation
# Downloads the HL7 FHIR Validator CLI jar and validates sample PAS Bundle JSON files
# against Da Vinci PAS IG profiles.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
TARGET_DIR="${PROJECT_DIR}/target"
VALIDATOR_JAR="${TARGET_DIR}/validator_cli.jar"
VALIDATOR_VERSION="6.7.10"
VALIDATOR_URL="https://github.com/hapifhir/org.hl7.fhir.core/releases/download/${VALIDATOR_VERSION}/validator_cli.jar"
OUTPUT_FILE="${TARGET_DIR}/fhir-validator-output.txt"
RESOURCES_DIR="${SCRIPT_DIR}/../resources"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== PAS Profile FHIR Validator ==="
echo ""

# Ensure target directory exists
mkdir -p "${TARGET_DIR}"

# Download validator if not present
if [ ! -f "${VALIDATOR_JAR}" ]; then
    echo "Downloading HL7 FHIR Validator CLI v${VALIDATOR_VERSION}..."
    curl -L -o "${VALIDATOR_JAR}" "${VALIDATOR_URL}"
    echo "Download complete."
else
    echo "FHIR Validator CLI already present at ${VALIDATOR_JAR}"
fi

# Initialize counters
TOTAL=0
PASSED=0
FAILED=0

# Clear output file
> "${OUTPUT_FILE}"

echo "" >> "${OUTPUT_FILE}"
echo "=== PAS Profile Validation Report ===" >> "${OUTPUT_FILE}"
echo "Date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")" >> "${OUTPUT_FILE}"
echo "Validator Version: ${VALIDATOR_VERSION}" >> "${OUTPUT_FILE}"
echo "" >> "${OUTPUT_FILE}"

# Validate each sample bundle
for BUNDLE_FILE in "${RESOURCES_DIR}"/pas-sample-*.json; do
    if [ ! -f "${BUNDLE_FILE}" ]; then
        echo -e "${YELLOW}No PAS sample bundles found in ${RESOURCES_DIR}${NC}"
        exit 0
    fi

    BUNDLE_NAME="$(basename "${BUNDLE_FILE}")"
    TOTAL=$((TOTAL + 1))

    echo "Validating ${BUNDLE_NAME}..."
    echo "--- ${BUNDLE_NAME} ---" >> "${OUTPUT_FILE}"

    # Run the FHIR Validator
    if java -jar "${VALIDATOR_JAR}" "${BUNDLE_FILE}" -ig hl7.fhir.us.davinci-pas -version 4.0.1 >> "${OUTPUT_FILE}" 2>&1; then
        echo -e "  ${GREEN}PASS${NC}: ${BUNDLE_NAME}"
        PASSED=$((PASSED + 1))
    else
        echo -e "  ${RED}FAIL${NC}: ${BUNDLE_NAME}"
        FAILED=$((FAILED + 1))
    fi

    echo "" >> "${OUTPUT_FILE}"
done

# Print summary
echo ""
echo "=== Validation Summary ==="
echo "${TOTAL} resources validated, ${PASSED} passed, ${FAILED} failed"
echo ""
echo "Summary: ${TOTAL} resources validated, ${PASSED} passed, ${FAILED} failed" >> "${OUTPUT_FILE}"

# Full output location
echo "Full output: ${OUTPUT_FILE}"

# Exit with non-zero if any failures
if [ "${FAILED}" -gt 0 ]; then
    echo -e "${RED}Validation FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}All validations PASSED${NC}"
    exit 0
fi
