package gov.cms.bfd.server.war.r4.providers.pas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link PasClaimValidator}. */
class PasClaimValidatorTest {

  private PasClaimValidator validator;

  @BeforeEach
  void setUp() {
    validator = new PasClaimValidator();
  }

  @Test
  void testValidBundlePassesValidation() {
    Bundle bundle = createValidBundle();

    OperationOutcome outcome = validator.validate(bundle);

    assertTrue(validator.isValid(outcome));
  }

  @Test
  void testNullBundleFailsValidation() {
    OperationOutcome outcome = validator.validate(null);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream()
            .anyMatch(i -> i.getDiagnostics().contains("Bundle must not be null")));
  }

  @Test
  void testEmptyBundleFailsValidation() {
    Bundle bundle = new Bundle();

    OperationOutcome outcome = validator.validate(bundle);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream()
            .anyMatch(i -> i.getDiagnostics().contains("must have at least one entry")));
  }

  @Test
  void testBundleWithoutClaimAsFirstEntryFails() {
    Bundle bundle = new Bundle();
    bundle.addEntry().setResource(new Patient());

    OperationOutcome outcome = validator.validate(bundle);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream()
            .anyMatch(i -> i.getDiagnostics().contains("First entry in Bundle must be a Claim")));
  }

  @Test
  void testClaimWithWrongUseFails() {
    Bundle bundle = new Bundle();
    Claim claim = createBasicClaim();
    claim.setUse(Claim.Use.CLAIM); // Wrong use value
    bundle.addEntry().setResource(claim);
    addSupportingResources(bundle);

    OperationOutcome outcome = validator.validate(bundle);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream()
            .anyMatch(i -> i.getDiagnostics().contains("must be 'preauthorization'")));
  }

  @Test
  void testClaimMissingPatientFails() {
    Bundle bundle = new Bundle();
    Claim claim = createBasicClaim();
    claim.setPatient(null);
    bundle.addEntry().setResource(claim);

    OperationOutcome outcome = validator.validate(bundle);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream()
            .anyMatch(i -> i.getDiagnostics().contains("must have a patient reference")));
  }

  @Test
  void testClaimMissingProviderFails() {
    Bundle bundle = new Bundle();
    Claim claim = createBasicClaim();
    claim.setProvider(null);
    bundle.addEntry().setResource(claim);

    OperationOutcome outcome = validator.validate(bundle);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream()
            .anyMatch(i -> i.getDiagnostics().contains("must have a provider reference")));
  }

  @Test
  void testClaimMissingPriorityFails() {
    Bundle bundle = new Bundle();
    Claim claim = createBasicClaim();
    claim.setPriority(null);
    bundle.addEntry().setResource(claim);

    OperationOutcome outcome = validator.validate(bundle);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream()
            .anyMatch(i -> i.getDiagnostics().contains("must have a priority")));
  }

  @Test
  void testClaimMissingTypeFails() {
    Bundle bundle = new Bundle();
    Claim claim = createBasicClaim();
    claim.setType(null);
    bundle.addEntry().setResource(claim);

    OperationOutcome outcome = validator.validate(bundle);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream().anyMatch(i -> i.getDiagnostics().contains("must have a type")));
  }

  @Test
  void testClaimMissingInsuranceFails() {
    Bundle bundle = new Bundle();
    Claim claim = createBasicClaim();
    claim.setInsurance(null);
    bundle.addEntry().setResource(claim);

    OperationOutcome outcome = validator.validate(bundle);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream()
            .anyMatch(i -> i.getDiagnostics().contains("must have at least one insurance")));
  }

  @Test
  void testDuplicateSupportingInfoSequenceFails() {
    Bundle bundle = createValidBundle();
    Claim claim = (Claim) bundle.getEntry().get(0).getResource();

    // Add two supportingInfo entries with the same sequence
    Claim.SupportingInformationComponent info1 = new Claim.SupportingInformationComponent();
    info1.setSequence(1);
    info1.setCategory(new CodeableConcept(new Coding("http://example.org", "info", "Info")));
    claim.addSupportingInfo(info1);

    Claim.SupportingInformationComponent info2 = new Claim.SupportingInformationComponent();
    info2.setSequence(1); // Duplicate sequence
    info2.setCategory(new CodeableConcept(new Coding("http://example.org", "info2", "Info2")));
    claim.addSupportingInfo(info2);

    OperationOutcome outcome = validator.validate(bundle);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream()
            .anyMatch(i -> i.getDiagnostics().contains("sequence values must be unique")));
  }

  @Test
  void testUniqueSupportingInfoSequencePasses() {
    Bundle bundle = createValidBundle();
    Claim claim = (Claim) bundle.getEntry().get(0).getResource();

    Claim.SupportingInformationComponent info1 = new Claim.SupportingInformationComponent();
    info1.setSequence(1);
    info1.setCategory(new CodeableConcept(new Coding("http://example.org", "info", "Info")));
    claim.addSupportingInfo(info1);

    Claim.SupportingInformationComponent info2 = new Claim.SupportingInformationComponent();
    info2.setSequence(2); // Different sequence
    info2.setCategory(new CodeableConcept(new Coding("http://example.org", "info2", "Info2")));
    claim.addSupportingInfo(info2);

    OperationOutcome outcome = validator.validate(bundle);

    assertTrue(validator.isValid(outcome));
  }

  @Test
  void testUnresolvedReferencesFails() {
    Bundle bundle = new Bundle();
    Claim claim = createBasicClaim();
    // References point to resources that don't exist in the bundle
    claim.setPatient(new Reference("Patient/nonexistent"));
    bundle.addEntry().setResource(claim);

    OperationOutcome outcome = validator.validate(bundle);

    assertFalse(validator.isValid(outcome));
    assertTrue(
        outcome.getIssue().stream()
            .anyMatch(i -> i.getDiagnostics().contains("references in the Claim resolve")));
  }

  @Test
  void testIsValidWithNoIssues() {
    OperationOutcome outcome = new OperationOutcome();
    assertTrue(validator.isValid(outcome));
  }

  @Test
  void testIsValidWithNullOutcome() {
    assertTrue(validator.isValid(null));
  }

  @Test
  void testIsValidWithWarningsOnly() {
    OperationOutcome outcome = new OperationOutcome();
    outcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.WARNING)
        .setDiagnostics("A warning");
    assertTrue(validator.isValid(outcome));
  }

  private Bundle createValidBundle() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);

    // Create patient
    Patient patient = new Patient();
    patient.setId("test-patient");

    // Create practitioner
    Practitioner practitioner = new Practitioner();
    practitioner.setId("test-provider");

    // Create coverage
    Coverage coverage = new Coverage();
    coverage.setId("test-coverage");

    // Create organization
    Organization organization = new Organization();
    organization.setId("test-org");

    // Create claim with resolved references
    Claim claim = new Claim();
    claim.setUse(Claim.Use.PREAUTHORIZATION);
    claim.setStatus(Claim.ClaimStatus.ACTIVE);
    claim.setType(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/claim-type",
                "professional",
                "Professional")));
    claim.setPatient(new Reference("Patient/test-patient"));
    claim.setProvider(new Reference("Practitioner/test-provider"));
    claim.setPriority(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/processpriority", "normal", "Normal")));

    Claim.InsuranceComponent insurance = new Claim.InsuranceComponent();
    insurance.setSequence(1);
    insurance.setFocal(true);
    insurance.setCoverage(new Reference("Coverage/test-coverage"));
    claim.addInsurance(insurance);

    bundle.addEntry().setResource(claim);
    bundle.addEntry().setResource(patient);
    bundle.addEntry().setResource(practitioner);
    bundle.addEntry().setResource(coverage);
    bundle.addEntry().setResource(organization);

    return bundle;
  }

  private Claim createBasicClaim() {
    Claim claim = new Claim();
    claim.setUse(Claim.Use.PREAUTHORIZATION);
    claim.setStatus(Claim.ClaimStatus.ACTIVE);
    claim.setType(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/claim-type",
                "professional",
                "Professional")));
    claim.setPatient(new Reference("Patient/test-patient"));
    claim.setProvider(new Reference("Practitioner/test-provider"));
    claim.setPriority(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/processpriority", "normal", "Normal")));

    Claim.InsuranceComponent insurance = new Claim.InsuranceComponent();
    insurance.setSequence(1);
    insurance.setFocal(true);
    insurance.setCoverage(new Reference("Coverage/test-coverage"));
    claim.addInsurance(insurance);

    return claim;
  }

  private void addSupportingResources(Bundle bundle) {
    Patient patient = new Patient();
    patient.setId("test-patient");
    bundle.addEntry().setResource(patient);

    Practitioner practitioner = new Practitioner();
    practitioner.setId("test-provider");
    bundle.addEntry().setResource(practitioner);

    Coverage coverage = new Coverage();
    coverage.setId("test-coverage");
    bundle.addEntry().setResource(coverage);
  }
}
