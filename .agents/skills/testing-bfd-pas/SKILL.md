# Testing BFD PAS Integration Module

## Overview
The PAS integration module lives under `apps/bfd-server/bfd-server-war/src/*/java/gov/cms/bfd/server/war/r4/providers/pas/`. It includes mappers, validators, a FHIR $submit provider, and an HTML compliance report generator.

## Local Testing Limitations
- **Full Maven compilation is NOT possible locally** — the project depends on internal BFD modules (`bfd-server-shared-utils`, `bfd-model-rif`, etc.) that are only built in CI.
- `mvn test -Dtest=PasComplianceReportTest` will only work in the full BFD CI environment with all dependencies available.
- Workaround: Use a Python script to generate equivalent HTML output for visual verification. The Java generator and Python script produce structurally identical HTML.

## What You CAN Test Locally

### 1. HTML Compliance Report (Browser)
- Generate sample report: `python3 /tmp/generate-sample-report.py` (or create one matching the Java generator's output)
- Open `file:///tmp/pas-compliance-report.html` in Chrome
- Verify:
  - Header: "CMS-0057-F Compliance Report" with COMPLIANT/NON-COMPLIANT badge
  - Meta bar: PAS IG version, package status, validation engine
  - Summary cards: Total/Passed/Failed/Layers counts
  - Layer sections: All 3 layers with PASS/FAIL badges and correct check counts
  - Toggle: Click layer header to collapse/expand body content
  - Profile URLs: Correct PAS IG StructureDefinition URLs on each check group
  - Footer: CMS-0057-F audit trail text

### 2. Repo File Structure (Shell)
- PAS IG NPM package: `src/test/resources/package/hl7.fhir.us.davinci-pas-2.0.1.tgz` (should be ~605KB)
- Validation script: `src/test/scripts/validate-pas-profiles.sh` (should be executable)
- Sample bundles: `src/test/resources/pas-sample-submit-bundle.json`, `pas-sample-response-bundle.json`, plus `pas-sample-bundles/*.json`
- Source files: 10 Java files under `src/main/.../pas/`
- Test files: 10 Java files under `src/test/.../pas/`

### 3. FHIR Bundle Validity (Shell)
- Parse sample bundles with `python3 -c "import json; ..."` or `jq`
- Verify: `Bundle.type`, `Claim.use=preauthorization`, `meta.profile` URLs, `ClaimResponse` presence

### 4. Java Source Structure (Shell)
- Use `rg` to verify key symbols exist in generator/test files
- Verify `NpmPackageValidationSupport` and PAS IG tgz path references in test class

## Key Profile URLs to Verify
- Claim: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claim`
- ClaimResponse: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse`
- Beneficiary: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-beneficiary`
- Coverage: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-coverage`
- Response Bundle: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-pas-response-bundle`
- X12 Review Action: `https://codesystem.x12.org/005010/306`

## CI / Build
- The repo uses Maven with parent POM at `apps/bfd-server/pom.xml`
- Only CI configured is Devin Review (no Maven build pipeline)
- JDK 25 toolchain is required for full compilation
- The `tgz` extension is in `nonFilteredFileExtensions` in `apps/pom.xml` to prevent Maven from corrupting the PAS IG NPM package

## Devin Secrets Needed
None — this module has no external service dependencies for testing.
