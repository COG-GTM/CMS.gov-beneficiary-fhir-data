package gov.cms.bfd.server.war.r4.providers.pas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PasClaimMapper}. */
class PasClaimMapperTest {

  /** Verifies that a basic EOB maps to a valid PAS Claim with correct status, use, and type. */
  @Test
  void testMapEobToPasClaim_basicMapping() {
    ExplanationOfBenefit eob = createSampleEob("40", "Institutional");

    Claim claim =
        PasClaimMapper.mapEobToPasClaim(
            eob,
            new Reference("urn:uuid:patient-1"),
            new Reference("urn:uuid:insurer-1"),
            new Reference("urn:uuid:provider-1"),
            new Reference("urn:uuid:coverage-1"));

    assertNotNull(claim);
    assertNotNull(claim.getId());
    assertEquals(Claim.ClaimStatus.ACTIVE, claim.getStatus());
    assertEquals(Claim.Use.PREAUTHORIZATION, claim.getUse());

    // Verify profile
    assertTrue(
        claim.getMeta().getProfile().stream()
            .anyMatch(p -> PasConstants.PROFILE_PAS_CLAIM.equals(p.getValue())));

    // Verify references
    assertEquals("urn:uuid:patient-1", claim.getPatient().getReference());
    assertEquals("urn:uuid:insurer-1", claim.getInsurer().getReference());
    assertEquals("urn:uuid:provider-1", claim.getProvider().getReference());

    // Verify insurance
    assertFalse(claim.getInsurance().isEmpty());
    assertTrue(claim.getInsuranceFirstRep().getFocal());
    assertEquals("urn:uuid:coverage-1", claim.getInsuranceFirstRep().getCoverage().getReference());

    // Verify priority
    assertNotNull(claim.getPriority());
    assertEquals("normal", claim.getPriority().getCodingFirstRep().getCode());
  }

  /** Verifies that carrier/professional claim types are mapped correctly. */
  @Test
  void testMapClaimType_professional() {
    ExplanationOfBenefit eob = createSampleEob("71", "Carrier");

    CodeableConcept type = PasClaimMapper.mapClaimType(eob);
    assertEquals("professional", type.getCodingFirstRep().getCode());
    assertEquals(PasConstants.CODE_SYSTEM_CLAIM_TYPE, type.getCodingFirstRep().getSystem());
  }

  /** Verifies that PDE claim types map to pharmacy. */
  @Test
  void testMapClaimType_pharmacy() {
    ExplanationOfBenefit eob = createSampleEob("PDE", "Pharmacy");

    CodeableConcept type = PasClaimMapper.mapClaimType(eob);
    assertEquals("pharmacy", type.getCodingFirstRep().getCode());
  }

  /** Verifies that inpatient claim types map to institutional. */
  @Test
  void testMapClaimType_institutional() {
    ExplanationOfBenefit eob = createSampleEob("60", "Inpatient");

    CodeableConcept type = PasClaimMapper.mapClaimType(eob);
    assertEquals("institutional", type.getCodingFirstRep().getCode());
  }

  /** Verifies that diagnosis entries are mapped from EOB to PAS Claim. */
  @Test
  void testMapEobToPasClaim_withDiagnosis() {
    ExplanationOfBenefit eob = createSampleEob("40", "Institutional");

    ExplanationOfBenefit.DiagnosisComponent diag = eob.addDiagnosis();
    diag.setSequence(1);
    diag.setDiagnosis(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem("http://hl7.org/fhir/sid/icd-10-cm")
                    .setCode("I21.0")
                    .setDisplay("STEMI of anterior wall")));

    Claim claim =
        PasClaimMapper.mapEobToPasClaim(
            eob,
            new Reference("urn:uuid:p"),
            new Reference("urn:uuid:i"),
            new Reference("urn:uuid:pr"),
            new Reference("urn:uuid:c"));

    assertFalse(claim.getDiagnosis().isEmpty());
    assertEquals(1, claim.getDiagnosis().size());
    assertEquals(1, claim.getDiagnosis().get(0).getSequence());
  }

  /** Verifies that procedure entries are mapped from EOB to PAS Claim. */
  @Test
  void testMapEobToPasClaim_withProcedures() {
    ExplanationOfBenefit eob = createSampleEob("40", "Institutional");

    ExplanationOfBenefit.ProcedureComponent proc = eob.addProcedure();
    proc.setSequence(1);
    proc.setProcedure(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(PasConstants.CODE_SYSTEM_CPT)
                    .setCode("92928")
                    .setDisplay("Coronary stent placement")));
    proc.setDate(new Date());

    Claim claim =
        PasClaimMapper.mapEobToPasClaim(
            eob,
            new Reference("urn:uuid:p"),
            new Reference("urn:uuid:i"),
            new Reference("urn:uuid:pr"),
            new Reference("urn:uuid:c"));

    assertFalse(claim.getProcedure().isEmpty());
    assertEquals(1, claim.getProcedure().size());
    assertEquals(1, claim.getProcedure().get(0).getSequence());
  }

  /** Verifies that item/service line entries are mapped from EOB to PAS Claim. */
  @Test
  void testMapEobToPasClaim_withItems() {
    ExplanationOfBenefit eob = createSampleEob("40", "Institutional");

    ExplanationOfBenefit.ItemComponent item = eob.addItem();
    item.setSequence(1);
    item.setProductOrService(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(PasConstants.CODE_SYSTEM_CPT)
                    .setCode("99214")
                    .setDisplay("Office visit")));
    item.setServiced(new DateType("2026-01-15"));
    item.setQuantity(new SimpleQuantity().setValue(1));

    Claim claim =
        PasClaimMapper.mapEobToPasClaim(
            eob,
            new Reference("urn:uuid:p"),
            new Reference("urn:uuid:i"),
            new Reference("urn:uuid:pr"),
            new Reference("urn:uuid:c"));

    assertFalse(claim.getItem().isEmpty());
    assertEquals(1, claim.getItem().size());
    assertEquals(1, claim.getItem().get(0).getSequence());
    assertEquals(
        "99214", claim.getItem().get(0).getProductOrService().getCodingFirstRep().getCode());
  }

  /** Verifies that ICD-10 code system URLs are normalized. */
  @Test
  void testNormalizeIcd10Code() {
    CodeableConcept source = new CodeableConcept();
    source.addCoding(
        new Coding()
            .setSystem("http://www.cms.gov/Medicare/Coding/ICD10")
            .setCode("I21.0")
            .setDisplay("STEMI"));

    CodeableConcept normalized = PasClaimMapper.normalizeIcd10Code(source);

    assertEquals(PasConstants.CODE_SYSTEM_ICD10_CM, normalized.getCodingFirstRep().getSystem());
    assertEquals("I21.0", normalized.getCodingFirstRep().getCode());
  }

  /**
   * Creates a sample ExplanationOfBenefit for testing.
   *
   * @param typeCode the claim type code
   * @param typeDisplay the claim type display
   * @return a sample EOB
   */
  private ExplanationOfBenefit createSampleEob(String typeCode, String typeDisplay) {
    ExplanationOfBenefit eob = new ExplanationOfBenefit();
    eob.setType(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem("https://bluebutton.cms.gov/resources/codesystem/eob-type")
                    .setCode(typeCode)
                    .setDisplay(typeDisplay)));
    eob.setCreated(new Date());
    return eob;
  }
}
