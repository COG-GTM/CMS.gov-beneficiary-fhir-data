package gov.cms.bfd.server.war.r4.providers.pas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PasBundleBuilder}. */
class PasBundleBuilderTest {

  /** Verifies that buildPasBundle creates a complete PAS Request Bundle. */
  @Test
  void testBuildPasBundle_completeness() {
    ExplanationOfBenefit eob = createSampleEob();
    Patient patient = createSamplePatient();
    Coverage coverage = createSampleCoverage();

    Bundle bundle = PasBundleBuilder.buildPasBundle(eob, patient, coverage);

    assertNotNull(bundle);
    assertNotNull(bundle.getId());
    assertEquals(Bundle.BundleType.COLLECTION, bundle.getType());
    assertNotNull(bundle.getTimestamp());

    // Check profile
    assertTrue(
        bundle.getMeta().getProfile().stream()
            .anyMatch(p -> PasConstants.PROFILE_PAS_REQUEST_BUNDLE.equals(p.getValue())));

    // Should have 5 entries: Claim, Patient, Insurer, Requestor, Coverage
    assertEquals(5, bundle.getEntry().size());

    // Verify resource types
    boolean hasClaim = false;
    boolean hasPatient = false;
    boolean hasCoverage = false;
    int orgCount = 0;

    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      assertNotNull(entry.getFullUrl());
      assertTrue(entry.getFullUrl().startsWith("urn:uuid:"));
      // Validate that the UUID portion is a valid RFC 4122 UUID
      String uuidPart = entry.getFullUrl().substring("urn:uuid:".length());
      UUID.fromString(uuidPart); // throws IllegalArgumentException if not valid UUID
      Resource resource = entry.getResource();
      assertNotNull(resource);

      if (resource instanceof Claim) {
        hasClaim = true;
      } else if (resource instanceof Patient) {
        hasPatient = true;
      } else if (resource instanceof Coverage) {
        hasCoverage = true;
      } else if (resource instanceof Organization) {
        orgCount++;
      }
    }

    assertTrue(hasClaim, "Bundle must contain a Claim");
    assertTrue(hasPatient, "Bundle must contain a Patient");
    assertTrue(hasCoverage, "Bundle must contain a Coverage");
    assertEquals(2, orgCount, "Bundle must contain 2 Organizations (insurer + requestor)");
  }

  /** Verifies that the PAS Beneficiary is correctly built from a BFD Patient. */
  @Test
  void testBuildPasBeneficiary() {
    Patient source = createSamplePatient();

    Patient pasBeneficiary = PasBundleBuilder.buildPasBeneficiary(source);

    assertNotNull(pasBeneficiary);
    assertNotNull(pasBeneficiary.getId());
    assertTrue(
        pasBeneficiary.getMeta().getProfile().stream()
            .anyMatch(p -> PasConstants.PROFILE_PAS_BENEFICIARY.equals(p.getValue())));

    // Check demographics copied
    assertEquals("Smith", pasBeneficiary.getNameFirstRep().getFamily());
    assertEquals(Enumerations.AdministrativeGender.MALE, pasBeneficiary.getGender());
    assertNotNull(pasBeneficiary.getBirthDate());
  }

  /** Verifies that the CMS Insurer Organization is correctly built. */
  @Test
  void testBuildCmsInsurer() {
    Organization insurer = PasBundleBuilder.buildCmsInsurer();

    assertNotNull(insurer);
    assertEquals(PasConstants.INSURER_RESOURCE_ID, insurer.getId());
    assertEquals(PasConstants.CMS_ORG_NAME, insurer.getName());
    assertTrue(insurer.getActive());
    assertTrue(
        insurer.getMeta().getProfile().stream()
            .anyMatch(p -> PasConstants.PROFILE_PAS_INSURER.equals(p.getValue())));

    // Check NPI
    assertFalse(insurer.getIdentifier().isEmpty());
    assertEquals(PasConstants.CODE_SYSTEM_NPI, insurer.getIdentifierFirstRep().getSystem());
    assertEquals(PasConstants.CMS_ORG_NPI, insurer.getIdentifierFirstRep().getValue());
  }

  /** Verifies that the Requestor Organization is correctly built from EOB provider data. */
  @Test
  void testBuildRequestorOrganization() {
    ExplanationOfBenefit eob = createSampleEob();
    eob.setProvider(
        new Reference()
            .setIdentifier(
                new Identifier().setSystem(PasConstants.CODE_SYSTEM_NPI).setValue("1234567890")));

    Organization requestor = PasBundleBuilder.buildRequestorOrganization(eob);

    assertNotNull(requestor);
    assertNotNull(requestor.getId());
    assertTrue(requestor.getActive());
    assertTrue(
        requestor.getMeta().getProfile().stream()
            .anyMatch(p -> PasConstants.PROFILE_PAS_REQUESTOR.equals(p.getValue())));

    assertEquals(PasConstants.CODE_SYSTEM_NPI, requestor.getIdentifierFirstRep().getSystem());
    assertEquals("1234567890", requestor.getIdentifierFirstRep().getValue());
  }

  /** Verifies that PAS Coverage is correctly built. */
  @Test
  void testBuildPasCoverage() {
    Coverage source = createSampleCoverage();

    Coverage pasCoverage =
        PasBundleBuilder.buildPasCoverage(source, "urn:uuid:patient-1", "urn:uuid:insurer-1");

    assertNotNull(pasCoverage);
    assertNotNull(pasCoverage.getId());
    assertEquals(Coverage.CoverageStatus.ACTIVE, pasCoverage.getStatus());
    assertTrue(
        pasCoverage.getMeta().getProfile().stream()
            .anyMatch(p -> PasConstants.PROFILE_PAS_COVERAGE.equals(p.getValue())));

    assertEquals("urn:uuid:patient-1", pasCoverage.getBeneficiary().getReference());
    assertFalse(pasCoverage.getPayor().isEmpty());
    assertEquals("urn:uuid:insurer-1", pasCoverage.getPayorFirstRep().getReference());
  }

  /**
   * Creates a sample ExplanationOfBenefit.
   *
   * @return a sample EOB
   */
  private ExplanationOfBenefit createSampleEob() {
    ExplanationOfBenefit eob = new ExplanationOfBenefit();
    eob.setType(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem("https://bluebutton.cms.gov/resources/codesystem/eob-type")
                    .setCode("40")
                    .setDisplay("Inpatient")));
    eob.setCreated(new Date());
    return eob;
  }

  /**
   * Creates a sample Patient.
   *
   * @return a sample Patient
   */
  private Patient createSamplePatient() {
    Patient patient = new Patient();
    patient.setId("sample-patient");
    patient.addIdentifier(
        new Identifier().setSystem(PasConstants.IDENTIFIER_SYSTEM_MBI).setValue("1S00E00AA00"));
    patient.addName(new HumanName().setFamily("Smith").addGiven("John"));
    patient.setGender(Enumerations.AdministrativeGender.MALE);
    patient.setBirthDate(new Date());
    return patient;
  }

  /**
   * Creates a sample Coverage.
   *
   * @return a sample Coverage
   */
  private Coverage createSampleCoverage() {
    Coverage coverage = new Coverage();
    coverage.setId("sample-coverage");
    coverage.setStatus(Coverage.CoverageStatus.ACTIVE);
    coverage.setBeneficiary(new Reference("Patient/sample-patient"));
    coverage.setSubscriberId("1S00E00AA00");
    return coverage;
  }
}
