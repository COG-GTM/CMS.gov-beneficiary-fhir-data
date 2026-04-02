# BFD-to-PAS FHIR Resource Mapping

> **Document**: `02-bfd-to-pas-mapping.md`
> **Branch**: `feature/cms-0057f-pas-integration`
> **Last Updated**: 2026-04-02
> **Status**: Draft

## 1. Purpose

This document defines the field-level transformation requirements for converting existing BFD (Beneficiary FHIR Data) resources into resources conformant with the HL7 Da Vinci Prior Authorization Support (PAS) Implementation Guide. It identifies every gap between the current BFD output and PAS profile requirements, assigns a priority to each gap, and catalogs entirely new resources and operations that BFD must support.

### Priority Definitions

| Priority | Label | Meaning |
|----------|-------|---------|
| **P0** | Blocking | Must be resolved before any PAS transaction can succeed. Without this change the resource will be rejected by a PAS-conformant payer. |
| **P1** | Required | Required by the PAS IG for a complete implementation. Omission will cause validation warnings or limit functionality. |
| **P2** | Recommended | Enhances interoperability or enables optional PAS features. Can be deferred to a later phase. |

---

## 2. Source Material References

### BFD (COG-GTM/CMS.gov-beneficiary-fhir-data)

| File | Path | Key Lines |
|------|------|-----------|
| FissClaimTransformerV2.java | `apps/bfd-server/bfd-server-war/src/main/java/gov/cms/bfd/server/war/r4/providers/pac/FissClaimTransformerV2.java` | 128-170 (transformClaim), 144-145 (type/use), 160-166 (meta) |
| FissClaimResponseTransformerV2.java | Same package | 122-149 (transformClaim), 131-132 (type/use), 56-68 (STATUS_TO_OUTCOME), 139-145 (meta) |
| R4ClaimResourceProvider.java | Same package | 25-87 (provider registration, FISS/MCS type map) |
| R4ClaimResponseResourceProvider.java | Same package | 25-87 (provider registration, FISS/MCS type map) |
| SystemUrls.java | `apps/bfd-server-ng/src/main/java/gov/cms/bfd/server/ng/util/SystemUrls.java` | 17-18 (C4BB Patient profile), 32-33 (C4BB Coverage profile) |

### PAS RI (COG-GTM/FHIR-prior-auth)

| File | Path | Key Lines |
|------|------|-----------|
| FhirUtils.java | `src/main/java/org/hl7/davinci/priorauth/FhirUtils.java` | 35-56 (extension URLs), 58-61 (code systems), 66-89 (Disposition enum), 97-156 (ReviewAction enum) |
| ClaimResponseFactory.java | `src/main/java/org/hl7/davinci/priorauth/ClaimResponseFactory.java` | 307-348 (disposition logic), 363-421 (ClaimResponse creation), 422-462 (CommunicationRequest) |
| ClaimEndpoint.java | `src/main/java/org/hl7/davinci/priorauth/endpoint/ClaimEndpoint.java` | 115-194 ($submit operation flow) |
| bundle-prior-auth.json | `src/test/resources/bundle-prior-auth.json` | 1-20 (sample PAS Bundle with `use: preauthorization`) |

---

## 3. Mapping Tables

### 3.1 Claim Resource (R4ClaimResourceProvider / FissClaimTransformerV2 &rarr; PAS Claim Profile)

| # | BFD Resource | BFD Field/Element | Current Value | PAS Target Profile | PAS Required Value | Gap | Priority |
|---|---|---|---|---|---|---|---|
| C-01 | Claim | `Claim.use` | `Claim.Use.CLAIM` (FissClaimTransformerV2.java:149) | PAS Claim | `Claim.Use.PREAUTHORIZATION` | Value mismatch: BFD hardcodes `CLAIM`; PAS requires `PREAUTHORIZATION` | **P0** |
| C-02 | Claim | `Claim.meta.profile` | Not set (FissClaimTransformerV2.java:160-166 sets only `lastUpdated` and security tags) | PAS Claim | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claim` | Missing profile declaration. PAS-conformant servers MUST declare the profile in `meta.profile`. | **P0** |
| C-03 | Claim | `Claim.type` | `ClaimType.INSTITUTIONAL` (FissClaimTransformerV2.java:145) | PAS Claim | Same code system is acceptable, but PAS also requires an X12 service type code slice when applicable | Partial gap: base coding is compatible, but PAS may require an additional X12 278 service type coding in the same CodeableConcept | **P1** |
| C-04 | Claim | `Claim.item.extension` (`extension-itemRequestedServiceDate`) | Not present | PAS Claim | Extension URL: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemRequestedServiceDate` (FhirUtils.java:45). Value: `Period` with requested service date range. | Missing extension. PAS requires the requested service date on each Claim.item. | **P0** |
| C-05 | Claim | `Claim.item.extension` (`extension-itemTraceNumber`) | Not present | PAS Claim | Extension URL: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemTraceNumber` (FhirUtils.java:44). Value: `Identifier` for item-level trace. | Missing extension. Enables payer-to-provider item correlation. | **P1** |
| C-06 | Claim | `Claim.supportingInfo` | Present (FissClaimTransformerV2.java:146) with BFD-specific categories (type-of-bill, DRG, admission type) | PAS Claim | PAS defines its own supportingInfo slices (e.g., `additionalInformation` categories) | Review needed: existing supportingInfo categories may not align with PAS-defined slices. Mapping of BFD categories to PAS categories is required. | **P1** |
| C-07 | Claim | `Claim.insurance.coverage` | Reference to contained payer (FissClaimTransformerV2.java:413-440) using payer name | PAS Claim | Reference to a PAS-conformant Coverage resource | Gap: BFD constructs insurance from FISS payer data; PAS expects a reference to a standalone Coverage resource with `subscriberId` = MBI | **P1** |
| C-08 | Claim | `Claim.provider` | `Reference("#provider-org")` — contained Organization (FissClaimTransformerV2.java:152) | PAS Claim | Reference to a PAS-conformant Organization or Practitioner | Review needed: PAS may require the provider to be a Bundle entry rather than a contained resource | **P2** |
| C-09 | Claim | `Claim.diagnosis` | Present with ICD-9/ICD-10 codes (FissClaimTransformerV2.java:155, 297-321) | PAS Claim | Same ICD coding systems accepted | Compatible; no change needed for base diagnosis coding | **N/A** |
| C-10 | Claim | `Claim.item` | Present with revenue line items (FissClaimTransformerV2.java:158, 448-588) | PAS Claim | Item must carry PAS extensions (C-04, C-05 above) | Items exist but lack required PAS extensions | **P0** |
| C-11 | Claim | `Claim.priority` | `ProcessPriority.NORMAL` (FissClaimTransformerV2.java:150) | PAS Claim | Same code system accepted | Compatible | **N/A** |
| C-12 | Claim | `Claim.patient` | `Reference("#patient")` — contained patient (FissClaimTransformerV2.java:153) | PAS Claim | Reference to a PAS Beneficiary (US Core Patient) as a Bundle entry | Gap: PAS expects patient as a separate Bundle entry, not contained | **P1** |

### 3.2 ClaimResponse Resource (R4ClaimResponseResourceProvider / FissClaimResponseTransformerV2 &rarr; PAS ClaimResponse Profile)

| # | BFD Resource | BFD Field/Element | Current Value | PAS Target Profile | PAS Required Value | Gap | Priority |
|---|---|---|---|---|---|---|---|
| CR-01 | ClaimResponse | `ClaimResponse.use` | `ClaimResponse.Use.CLAIM` (FissClaimResponseTransformerV2.java:132) | PAS ClaimResponse | `ClaimResponse.Use.PREAUTHORIZATION` | Value mismatch: must be `PREAUTHORIZATION` for PAS | **P0** |
| CR-02 | ClaimResponse | `ClaimResponse.meta.profile` | Not set (FissClaimResponseTransformerV2.java:139-145 sets only `lastUpdated` and security tags) | PAS ClaimResponse | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse` (ClaimResponseFactory.java:414-415) | Missing profile declaration | **P0** |
| CR-03 | ClaimResponse | `ClaimResponse.outcome` | Mapped from FISS status codes via `STATUS_TO_OUTCOME` map (FissClaimResponseTransformerV2.java:56-68): `' '`/`'a'`&rarr;QUEUED, `'d'`/`'p'`/`'r'`/`'u'`&rarr;COMPLETE, `'f'`/`'i'`/`'m'`/`'s'`/`'t'`&rarr;PARTIAL | PAS ClaimResponse | `COMPLETE` (for granted/denied), `PARTIAL`, `QUEUED` (for pending) per ClaimResponseFactory.java:375-381 | Partial compatibility: BFD already maps to the same enum values but uses FISS-specific status codes. Mapping logic must be reviewed to ensure PAS semantic alignment (e.g., BFD `QUEUED` maps to PAS `PENDING`). | **P1** |
| CR-04 | ClaimResponse | `ClaimResponse.disposition` | Not set (FissClaimResponseTransformerV2.java:122-149 — no `setDisposition()` call) | PAS ClaimResponse | String value from Disposition enum: `Granted` / `Denied` / `Partial` / `Pending` / `Cancelled` (FhirUtils.java:66-68, ClaimResponseFactory.java:385) | Missing field. PAS requires a human-readable disposition string. | **P0** |
| CR-05 | ClaimResponse | `ClaimResponse.item.extension` (`extension-reviewAction`) | Not present | PAS ClaimResponse | Extension URL: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction` (FhirUtils.java:46). Nested extensions for `reviewActionCode` and optionally `reviewReason`. | Missing extension. Core PAS requirement for conveying the payer's decision per item. | **P0** |
| CR-06 | ClaimResponse | `ClaimResponse.item.extension` (`extension-reviewActionCode`) | Not present | PAS ClaimResponse | Extension URL: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewActionCode` (FhirUtils.java:47). System: `https://valueset.x12.org/x217/005010/response/2000F/HCR/1/01/00/306` (FhirUtils.java:59). Codes: `A1` (Approved), `A2` (Partial), `A3` (Denied), `A4` (Pended), `A6` (Cancelled), `86` (Pended-Followup) per ReviewAction enum (FhirUtils.java:97-156). | Missing extension with X12 HCR01 code. Must map FISS status codes to X12 ReviewAction codes. | **P0** |
| CR-07 | ClaimResponse | `ClaimResponse.extension` (`extension-authorizationNumber`) | Not present | PAS ClaimResponse | Extension URL: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-authorizationNumber` (FhirUtils.java:36). Value: `string` — the prior authorization reference number. | Missing extension. PAS uses `preAuthRef` (ClaimResponseFactory.java:386) alongside this extension. | **P1** |
| CR-08 | ClaimResponse | `ClaimResponse.item.extension` (`extension-itemPreAuthPeriod`) | Not present | PAS ClaimResponse | Extension URL: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemPreAuthPeriod` (FhirUtils.java:42). Value: `Period` — authorized service date range. | Missing extension | **P1** |
| CR-09 | ClaimResponse | `ClaimResponse.item.extension` (`extension-itemPreAuthIssueDate`) | Not present | PAS ClaimResponse | Extension URL: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemPreAuthIssueDate` (FhirUtils.java:41). Value: `date` — when authorization was issued. | Missing extension | **P1** |
| CR-10 | ClaimResponse | `ClaimResponse.item.extension` (`extension-itemAuthorizedProvider`) | Not present | PAS ClaimResponse | Extension URL: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemAuthorizedProvider` (FhirUtils.java:38). Value: `Reference(Practitioner|Organization)`. | Missing extension | **P1** |
| CR-11 | ClaimResponse | `ClaimResponse.item.extension` (`extension-itemAuthorizedDate`) | Not present | PAS ClaimResponse | Extension URL: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemAuthorizedDate` (FhirUtils.java:37). Value: `Period` — authorized date range. | Missing extension | **P2** |
| CR-12 | ClaimResponse | `ClaimResponse.item.extension` (`extension-administrationReferenceNumber`) | Not present | PAS ClaimResponse | Extension URL: `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-administrationReferenceNumber` (FhirUtils.java:35). Value: `string`. | Missing extension | **P2** |
| CR-13 | ClaimResponse | `ClaimResponse.preAuthRef` | Not set in BFD | PAS ClaimResponse | String — unique pre-authorization reference number (ClaimResponseFactory.java:386) | Missing field. Required for downstream referencing of the authorization. | **P1** |
| CR-14 | ClaimResponse | `ClaimResponse.type` | `ClaimType.INSTITUTIONAL` (FissClaimResponseTransformerV2.java:131) | PAS ClaimResponse | Same code system accepted | Compatible | **N/A** |
| CR-15 | ClaimResponse | `ClaimResponse.patient` | `Reference("#patient")` — contained (FissClaimResponseTransformerV2.java:134) | PAS ClaimResponse | Reference to Bundle entry (PAS Beneficiary) | Gap: should reference a Bundle-entry patient, not contained | **P1** |
| CR-16 | ClaimResponse | `ClaimResponse.requestor` | Not set in BFD | PAS ClaimResponse | `Reference(Practitioner|Organization)` — the requesting provider (ClaimResponseFactory.java:382) | Missing field | **P1** |
| CR-17 | ClaimResponse | `ClaimResponse.request` | `Reference("Claim/f-{claimId}")` (FissClaimResponseTransformerV2.java:135) | PAS ClaimResponse | `Reference("Claim/{id}")` (ClaimResponseFactory.java:383) | Compatible format. Verify the referenced Claim is PAS-conformant. | **N/A** |

### 3.3 Patient Resource (R4PatientResourceProvider &rarr; PAS Beneficiary / US Core Patient)

| # | BFD Resource | BFD Field/Element | Current Value | PAS Target Profile | PAS Required Value | Gap | Priority |
|---|---|---|---|---|---|---|---|
| P-01 | Patient | `Patient.meta.profile` | C4BB Patient 2.1.0: `http://hl7.org/fhir/us/carin-bb/StructureDefinition/C4BB-Patient\|2.1.0` (SystemUrls.java:17-18) | PAS Beneficiary (US Core Patient) | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient` | Review needed: C4BB Patient is built on US Core Patient; the profile declaration may need to add the PAS Beneficiary profile URL or confirm US Core Patient is sufficient | **P1** |
| P-02 | Patient | `Patient.identifier` (MBI) | Uses BFD MBI identifier system (e.g., `https://bluebutton.cms.gov/resources/identifier/mbi-hash` or current MBI system) | PAS Beneficiary | MBI identifier with system `http://hl7.org/fhir/sid/us-mbi` or CMS-recognized system | Verify alignment: confirm the MBI identifier system URL matches what PAS payers expect. The system URL may differ between BFD and PAS expectations. | **P1** |
| P-03 | Patient | `Patient.name` | Present (required by C4BB/US Core) | PAS Beneficiary | Required by US Core Patient | Compatible | **N/A** |
| P-04 | Patient | `Patient.gender` | Present (required by C4BB/US Core) | PAS Beneficiary | Required by US Core Patient | Compatible | **N/A** |
| P-05 | Patient | `Patient.birthDate` | Present (required by C4BB/US Core) | PAS Beneficiary | Required by US Core Patient | Compatible | **N/A** |
| P-06 | Patient | `Patient.address` | Present | PAS Beneficiary | Required by US Core Patient | Compatible | **N/A** |

### 3.4 Coverage Resource (R4CoverageResourceProvider &rarr; PAS Coverage)

| # | BFD Resource | BFD Field/Element | Current Value | PAS Target Profile | PAS Required Value | Gap | Priority |
|---|---|---|---|---|---|---|---|
| V-01 | Coverage | `Coverage.meta.profile` | C4BB Coverage 2.1.0: `http://hl7.org/fhir/us/carin-bb/StructureDefinition/C4BB-Coverage\|2.1.0` (SystemUrls.java:32-33) | PAS Coverage | PAS Coverage profile URL or HRex Coverage | Verify: may need to add PAS Coverage profile declaration | **P1** |
| V-02 | Coverage | `Coverage.subscriberId` | Set from beneficiary data | PAS Coverage | Must contain the Medicare Beneficiary Identifier (MBI) | Verify: confirm `subscriberId` is populated with MBI value and uses the correct system | **P1** |
| V-03 | Coverage | `Coverage.payor` | Reference to CMS Organization | PAS Coverage | `Reference(Organization)` — the payer/insurer | Compatible if the referenced Organization conforms to PAS expectations | **P2** |
| V-04 | Coverage | `Coverage.beneficiary` | Reference to Patient | PAS Coverage | `Reference(Patient)` — must point to a PAS Beneficiary | Compatible | **N/A** |
| V-05 | Coverage | `Coverage.status` | `active` | PAS Coverage | `active` | Compatible | **N/A** |
| V-06 | Coverage | `Coverage.relationship` | `self` (subscriber is the beneficiary) | PAS Coverage | `self` for Medicare beneficiaries | Compatible | **N/A** |

---

## 4. FISS Status Code to PAS Disposition/ReviewAction Mapping

BFD maps FISS single-character status codes to `RemittanceOutcome` values. PAS requires a richer mapping that includes both a `Disposition` string and an X12 `ReviewAction` code. The following table proposes the complete mapping:

| FISS Status Code | BFD Current Outcome (FissClaimResponseTransformerV2.java:56-68) | PAS Disposition (FhirUtils.java:66-68) | PAS ReviewAction X12 Code (FhirUtils.java:97-156) | X12 Code System |
|---|---|---|---|---|
| `' '` (space) | `QUEUED` | `Pending` | `A4` (Pended) | `https://codesystem.x12.org/005010/306` |
| `'a'` | `QUEUED` | `Pending` | `A4` (Pended) | `https://codesystem.x12.org/005010/306` |
| `'d'` | `COMPLETE` | `Denied` | `A3` (Denied) | `https://codesystem.x12.org/005010/306` |
| `'f'` | `PARTIAL` | `Partial` | `A2` (Partial Approval) | `https://codesystem.x12.org/005010/306` |
| `'i'` | `PARTIAL` | `Partial` | `A2` (Partial Approval) | `https://codesystem.x12.org/005010/306` |
| `'m'` | `PARTIAL` | `Partial` | `A2` (Partial Approval) | `https://codesystem.x12.org/005010/306` |
| `'p'` | `COMPLETE` | `Granted` | `A1` (Approved) | `https://codesystem.x12.org/005010/306` |
| `'r'` | `COMPLETE` | `Granted` | `A1` (Approved) | `https://codesystem.x12.org/005010/306` |
| `'s'` | `PARTIAL` | `Partial` | `A2` (Partial Approval) | `https://codesystem.x12.org/005010/306` |
| `'t'` | `PARTIAL` | `Partial` | `A2` (Partial Approval) | `https://codesystem.x12.org/005010/306` |
| `'u'` | `COMPLETE` | `Granted` | `A1` (Approved) | `https://codesystem.x12.org/005010/306` |
| *(cancelled)* | *(not mapped)* | `Cancelled` | `A6` (Cancelled) | `https://codesystem.x12.org/005010/306` |
| *(pended w/ followup)* | *(not mapped)* | `Pending` | `86` (Pended — Follow-up Action Required) | `https://codesystem.x12.org/005010/306` |

> **Note**: The FISS-to-PAS status mapping above is a proposed starting point. The exact mapping MUST be validated with CMS business rules and the PAS IG conformance requirements. Some FISS statuses (e.g., `'i'`, `'m'`, `'s'`, `'t'`) may have nuanced meanings that require different PAS mappings.

---

## 5. PAS Extension URL Reference

All PAS extensions that BFD must support, sourced from `FhirUtils.java` lines 35-47:

| Extension Name | URL | FHIR Type | Used On | BFD Status |
|---|---|---|---|---|
| `extension-administrationReferenceNumber` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-administrationReferenceNumber` | `string` | ClaimResponse.item | Not present |
| `extension-authorizationNumber` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-authorizationNumber` | `string` | ClaimResponse | Not present |
| `extension-itemAuthorizedDate` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemAuthorizedDate` | `Period` | ClaimResponse.item | Not present |
| `extension-itemAuthorizedProvider` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemAuthorizedProvider` | `Reference` | ClaimResponse.item | Not present |
| `extension-itemCancelled` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemCancelled` | `boolean` | ClaimResponse.item | Not present |
| `extension-infoChanged` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-infoChanged` | `CodeableConcept` | ClaimResponse.item | Not present |
| `extension-itemPreAuthIssueDate` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemPreAuthIssueDate` | `date` | ClaimResponse.item | Not present |
| `extension-itemPreAuthPeriod` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemPreAuthPeriod` | `Period` | ClaimResponse.item | Not present |
| `extension-itemReference` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemReference` | `Reference` | Claim.item | Not present |
| `extension-itemTraceNumber` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemTraceNumber` | `Identifier` | Claim.item | Not present |
| `extension-itemRequestedServiceDate` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemRequestedServiceDate` | `Period` | Claim.item | Not present |
| `extension-reviewAction` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction` | `complex` | ClaimResponse.item | Not present |
| `extension-reviewActionCode` | `http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewActionCode` | `CodeableConcept` | Within reviewAction | Not present |

### PAS Code Systems (FhirUtils.java:58-61)

| Code System | URL | Used For |
|---|---|---|
| ReviewAction Code (X12 306) | `https://valueset.x12.org/x217/005010/response/2000F/HCR/1/01/00/306` | `extension-reviewActionCode` values |
| Review Reason Code (X12 886) | `https://codesystem.x12.org/external/886` | Review reason within `extension-reviewAction` |

---

## 6. Gap Summary by Priority

### P0 — Blocking (Must Fix Before PAS Transactions)

| ID | Resource | Gap Description |
|----|----------|-----------------|
| C-01 | Claim | `Claim.use` must change from `CLAIM` to `PREAUTHORIZATION` |
| C-02 | Claim | `Claim.meta.profile` must declare PAS Claim StructureDefinition |
| C-04 | Claim | `Claim.item` missing `extension-itemRequestedServiceDate` |
| C-10 | Claim | `Claim.item` elements exist but lack required PAS extensions |
| CR-01 | ClaimResponse | `ClaimResponse.use` must change from `CLAIM` to `PREAUTHORIZATION` |
| CR-02 | ClaimResponse | `ClaimResponse.meta.profile` must declare PAS ClaimResponse StructureDefinition |
| CR-04 | ClaimResponse | `ClaimResponse.disposition` field is missing entirely |
| CR-05 | ClaimResponse | `ClaimResponse.item` missing `extension-reviewAction` |
| CR-06 | ClaimResponse | `ClaimResponse.item` missing `extension-reviewActionCode` with X12 HCR01 codes |

**Total P0 gaps: 9**

### P1 — Required (Needed for Complete PAS Implementation)

| ID | Resource | Gap Description |
|----|----------|-----------------|
| C-03 | Claim | `Claim.type` may need additional X12 service type coding |
| C-05 | Claim | `Claim.item` missing `extension-itemTraceNumber` |
| C-06 | Claim | `Claim.supportingInfo` categories may not align with PAS slices |
| C-07 | Claim | `Claim.insurance.coverage` must reference PAS-conformant Coverage |
| C-12 | Claim | `Claim.patient` should be a Bundle entry reference, not contained |
| CR-03 | ClaimResponse | `ClaimResponse.outcome` mapping needs semantic alignment with PAS |
| CR-07 | ClaimResponse | Missing `extension-authorizationNumber` |
| CR-08 | ClaimResponse | Missing `extension-itemPreAuthPeriod` |
| CR-09 | ClaimResponse | Missing `extension-itemPreAuthIssueDate` |
| CR-10 | ClaimResponse | Missing `extension-itemAuthorizedProvider` |
| CR-13 | ClaimResponse | `ClaimResponse.preAuthRef` not set |
| CR-15 | ClaimResponse | `ClaimResponse.patient` should be Bundle entry, not contained |
| CR-16 | ClaimResponse | `ClaimResponse.requestor` not set |
| P-01 | Patient | Profile declaration may need PAS Beneficiary / US Core Patient URL |
| P-02 | Patient | MBI identifier system URL alignment needs verification |
| V-01 | Coverage | Profile declaration may need PAS Coverage URL |
| V-02 | Coverage | `subscriberId` MBI mapping needs verification |

**Total P1 gaps: 17**

### P2 — Recommended (Enhances Interoperability)

| ID | Resource | Gap Description |
|----|----------|-----------------|
| C-08 | Claim | Provider reference format (contained vs. Bundle entry) |
| CR-11 | ClaimResponse | Missing `extension-itemAuthorizedDate` |
| CR-12 | ClaimResponse | Missing `extension-administrationReferenceNumber` |
| V-03 | Coverage | Payor reference Organization conformance verification |

**Total P2 gaps: 4**

---

## 7. New Resources and Operations Required

BFD is currently a **read-only** system. The PAC (Pre-Adjudication Claims) providers (`R4ClaimResourceProvider`, `R4ClaimResponseResourceProvider`) expose only search/read operations via `AbstractR4ResourceProvider`. PAS requires both read and write operations. The following new capabilities must be built:

### 7.1 New Operations

| Operation | FHIR Path | HTTP Method | Priority | Description | PAS RI Reference |
|---|---|---|---|---|---|
| `Claim/$submit` | `POST /Claim/$submit` | POST | **P0** | Accepts a PAS-conformant Bundle containing a Claim and supporting resources. Processes the prior authorization request and returns a response Bundle containing a ClaimResponse. This is the primary PAS interaction. | ClaimEndpoint.java:115-194 |
| `Claim/$inquire` | `POST /Claim/$inquire` | POST | **P1** | Accepts a Bundle and returns the current status of a previously submitted prior authorization. Used for polling. | ClaimEndpoint.java (inquiry endpoint) |

### 7.2 New Resources

| Resource | FHIR Type | Priority | Description | PAS RI Reference |
|---|---|---|---|---|
| CommunicationRequest | `CommunicationRequest` | **P1** | Generated when a prior authorization is pended and the payer requires additional information from the provider. Contains payload with service line references, content modifiers, and communicated diagnoses. | ClaimResponseFactory.java:422-462 |
| Subscription | `Subscription` | **P2** | Enables asynchronous notifications for pended claims. When a pended claim reaches a final decision, the payer notifies the provider via rest-hook or WebSocket. | PAS RI SubscriptionEndpoint |

### 7.3 New Bundle Structure

PAS transactions use a **FHIR Bundle** as the unit of exchange (not individual resources). BFD must support creating and consuming the following Bundle structure:

```
PAS Request Bundle (type: collection)
├── Claim (use: preauthorization, with PAS extensions)
├── Patient (US Core Patient / PAS Beneficiary)
├── Coverage (PAS Coverage)
├── Organization (Insurer)
└── Practitioner / Organization (Provider)

PAS Response Bundle (type: collection)
├── ClaimResponse (with PAS extensions, disposition, reviewAction)
├── Patient (echo back)
├── CommunicationRequest (if pended, optional)
└── Organization (Insurer)
```

**Reference**: `bundle-prior-auth.json` in the PAS RI shows the expected structure with `"use": "preauthorization"` on the Claim resource (lines 1-20).

### 7.4 Architectural Impact

| Area | Current BFD State | PAS Requirement | Impact |
|---|---|---|---|
| **Data Flow** | Read-only (GET). BFD reads from RDA/RIF data stores and transforms to FHIR. | Write path (POST) for `$submit` and `$inquire`. | Entirely new write path. Must accept incoming Bundles, validate, process, persist authorization state, and return response Bundles. |
| **State Management** | Stateless reads — each request queries the database independently. | Stateful authorization lifecycle: submitted &rarr; pended &rarr; granted/denied. Pended items require follow-up tracking. | New persistence layer for authorization state. Must track authorization status, link to CommunicationRequests, and support status transitions. |
| **Authentication** | Mutual TLS with client certificates. | PAS RI uses Bearer token auth (ClaimEndpoint.java:119-121). BFD's mTLS may be sufficient but must be validated against PAS IG requirements. | Review authentication model for PAS-specific requirements. |
| **Async Processing** | Not applicable — all responses are synchronous. | Pended claims may require async notification via Subscription/rest-hook when a final decision is made. | New async processing capability using Subscription resources or equivalent mechanism. |

---

## 8. Implementation Recommendations

### Phase 1 — P0 Gaps (Minimum Viable PAS)

1. **Create a PAS-specific transformer layer** that wraps the existing FISS transformers. This avoids modifying the existing BFD read path while adding PAS-conformant output.
2. **Implement `Claim.use = PREAUTHORIZATION`** and `ClaimResponse.use = PREAUTHORIZATION` as conditional logic based on the request context (PAS vs. standard BFD).
3. **Add `meta.profile` declarations** for both Claim and ClaimResponse pointing to PAS StructureDefinitions.
4. **Implement the `extension-reviewAction`** complex extension on ClaimResponse items, using the FISS-to-X12 status mapping defined in Section 4.
5. **Add `ClaimResponse.disposition`** using the mapped Disposition enum value.
6. **Add `extension-itemRequestedServiceDate`** on Claim items, sourcing dates from the billable period or service dates.
7. **Implement `Claim/$submit`** as a new HAPI FHIR operation provider.

### Phase 2 — P1 Gaps (Complete PAS)

1. Add remaining ClaimResponse extensions (authorization number, preauth period/issue date, authorized provider).
2. Implement `Claim/$inquire` for status polling.
3. Build CommunicationRequest generation for pended items.
4. Refactor patient/provider references from contained to Bundle entries.
5. Verify and align MBI identifier system URLs across Patient and Coverage.

### Phase 3 — P2 Gaps (Enhanced PAS)

1. Add optional extensions (itemAuthorizedDate, administrationReferenceNumber).
2. Implement Subscription resource for async notifications.
3. Validate Coverage.payor Organization conformance.

---

## Appendix A: File Cross-Reference Index

| Concept | BFD File | BFD Line(s) | PAS RI File | PAS RI Line(s) |
|---|---|---|---|---|
| Claim.use value | FissClaimTransformerV2.java | 149 | bundle-prior-auth.json | 20 |
| ClaimResponse.use value | FissClaimResponseTransformerV2.java | 132 | ClaimResponseFactory.java | 367 |
| Claim.type coding | FissClaimTransformerV2.java | 145 | bundle-prior-auth.json | 11-18 |
| ClaimResponse.outcome mapping | FissClaimResponseTransformerV2.java | 56-68, 174-177 | ClaimResponseFactory.java | 375-381 |
| ClaimResponse.disposition | *(not implemented)* | — | ClaimResponseFactory.java | 385 |
| ReviewAction X12 codes | *(not implemented)* | — | FhirUtils.java | 97-156 |
| Disposition enum | *(not implemented)* | — | FhirUtils.java | 66-89 |
| PAS extension URLs | *(not implemented)* | — | FhirUtils.java | 35-56 |
| PAS code systems | *(not implemented)* | — | FhirUtils.java | 58-61 |
| $submit operation | *(not implemented)* | — | ClaimEndpoint.java | 115-194 |
| CommunicationRequest | *(not implemented)* | — | ClaimResponseFactory.java | 422-462 |
| Patient profile (C4BB) | SystemUrls.java | 17-18 | *(US Core Patient)* | — |
| Coverage profile (C4BB) | SystemUrls.java | 32-33 | *(PAS Coverage)* | — |
| meta.profile on Claim | FissClaimTransformerV2.java | 160-166 | ClaimResponseFactory.java | 414-415 |
| meta.profile on ClaimResponse | FissClaimResponseTransformerV2.java | 139-145 | ClaimResponseFactory.java | 414-415 |
