## PAS IG Conformance Verification Checklist

### Claim Resource (PAS Profile)
- [ ] Claim.use = "preauthorization"
- [ ] meta.profile includes PAS Claim profile URL
- [ ] Claim.type uses correct coding system
- [ ] Claim.item has extension-itemRequestedServiceDate
- [ ] Claim.item has extension-itemTraceNumber
- [ ] Claim.patient references Patient in Bundle
- [ ] Claim.provider references Practitioner/Organization in Bundle
- [ ] Claim.insurance references Coverage in Bundle

### ClaimResponse Resource (PAS Profile)
- [ ] meta.profile includes PAS ClaimResponse profile URL
- [ ] ClaimResponse.item has extension-reviewAction with X12 HCR01 code
- [ ] ClaimResponse.item has extension-reviewActionCode
- [ ] ClaimResponse.item has extension-authorizationNumber
- [ ] ClaimResponse.disposition is set (Granted/Denied/Partial/Pending)
- [ ] ClaimResponse.outcome maps correctly (COMPLETE/PARTIAL/QUEUED)

### $submit Operation
- [ ] Accepts Bundle with Claim as first entry
- [ ] Validates Claim.use == preauthorization
- [ ] Validates reference integrity within Bundle
- [ ] Validates unique supportingInfo.sequence
- [ ] Returns ClaimResponse Bundle on success
- [ ] Returns OperationOutcome on validation failure

### Automated Validation
- [ ] All JUnit tests pass (mvn test)
- [ ] HAPI FHIR profile validation passes for all sample resources
- [ ] HL7 FHIR Validator CLI passes for sample Bundles
- [ ] Validation report attached to PR
