package gov.cms.bfd.server.war.r4.providers.pas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link EobToPasClaimMapper}. */
class EobToPasClaimMapperTest {

  private EobToPasClaimMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new EobToPasClaimMapper();
  }

  @Test
  void testMapSetsUseToPreauthorization() {
    ExplanationOfBenefit eob = createBasicEob();

    Claim claim = mapper.map(eob);

    assertEquals(Claim.Use.PREAUTHORIZATION, claim.getUse());
  }

  @Test
  void testMapSetsMetaProfileToPasClaim() {
    ExplanationOfBenefit eob = createBasicEob();

    Claim claim = mapper.map(eob);

    assertNotNull(claim.getMeta());
    assertTrue(
        claim.getMeta().getProfile().stream()
            .anyMatch(p -> PasConstants.PAS_CLAIM_PROFILE_URL.equals(p.getValue())));
  }

  @Test
  void testMapSetsStatusToActive() {
    ExplanationOfBenefit eob = createBasicEob();

    Claim claim = mapper.map(eob);

    assertEquals(Claim.ClaimStatus.ACTIVE, claim.getStatus());
  }

  @Test
  void testMapCopiesType() {
    ExplanationOfBenefit eob = createBasicEob();
    CodeableConcept type =
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/claim-type",
                "professional",
                "Professional"));
    eob.setType(type);

    Claim claim = mapper.map(eob);

    assertNotNull(claim.getType());
    assertEquals("professional", claim.getType().getCodingFirstRep().getCode());
  }

  @Test
  void testMapCopiesPatientReference() {
    ExplanationOfBenefit eob = createBasicEob();
    eob.setPatient(new Reference("Patient/123"));

    Claim claim = mapper.map(eob);

    assertNotNull(claim.getPatient());
    assertEquals("Patient/123", claim.getPatient().getReference());
  }

  @Test
  void testMapCopiesProviderReference() {
    ExplanationOfBenefit eob = createBasicEob();
    eob.setProvider(new Reference("Practitioner/456"));

    Claim claim = mapper.map(eob);

    assertNotNull(claim.getProvider());
    assertEquals("Practitioner/456", claim.getProvider().getReference());
  }

  @Test
  void testMapSetsPriorityToNormal() {
    ExplanationOfBenefit eob = createBasicEob();

    Claim claim = mapper.map(eob);

    assertNotNull(claim.getPriority());
    assertEquals("normal", claim.getPriority().getCodingFirstRep().getCode());
  }

  @Test
  void testMapCopiesDiagnosis() {
    ExplanationOfBenefit eob = createBasicEob();
    ExplanationOfBenefit.DiagnosisComponent diag = new ExplanationOfBenefit.DiagnosisComponent();
    diag.setSequence(1);
    diag.setDiagnosis(
        new CodeableConcept(new Coding("http://hl7.org/fhir/sid/icd-10-cm", "J06.9", "URI")));
    eob.addDiagnosis(diag);

    Claim claim = mapper.map(eob);

    assertNotNull(claim.getDiagnosis());
    assertEquals(1, claim.getDiagnosis().size());
    assertEquals(1, claim.getDiagnosis().get(0).getSequence());
  }

  @Test
  void testMapCopiesItemsWithPasExtensions() {
    ExplanationOfBenefit eob = createBasicEob();
    ExplanationOfBenefit.ItemComponent eobItem = new ExplanationOfBenefit.ItemComponent();
    eobItem.setSequence(1);
    eobItem.setProductOrService(
        new CodeableConcept(new Coding("http://www.ama-assn.org/go/cpt", "99213", "Office visit")));
    eobItem.setServiced(new DateTimeType(new Date()));
    eob.addItem(eobItem);

    Claim claim = mapper.map(eob);

    assertNotNull(claim.getItem());
    assertEquals(1, claim.getItem().size());

    Claim.ItemComponent claimItem = claim.getItem().get(0);
    assertEquals(1, claimItem.getSequence());

    // Verify PAS extensions are present
    assertTrue(
        claimItem.getExtension().stream()
            .anyMatch(e -> PasConstants.ITEM_REQUESTED_SERVICE_DATE.equals(e.getUrl())));
    assertTrue(
        claimItem.getExtension().stream()
            .anyMatch(e -> PasConstants.ITEM_TRACE_NUMBER_EXTENSION_URL.equals(e.getUrl())));
  }

  @Test
  void testMapCopiesInsurance() {
    ExplanationOfBenefit eob = createBasicEob();
    ExplanationOfBenefit.InsuranceComponent ins = new ExplanationOfBenefit.InsuranceComponent();
    ins.setFocal(true);
    ins.setCoverage(new Reference("Coverage/789"));
    eob.addInsurance(ins);

    Claim claim = mapper.map(eob);

    assertNotNull(claim.getInsurance());
    assertEquals(1, claim.getInsurance().size());
    assertTrue(claim.getInsurance().get(0).getFocal());
    assertEquals("Coverage/789", claim.getInsurance().get(0).getCoverage().getReference());
  }

  @Test
  void testMapCopiesSupportingInfo() {
    ExplanationOfBenefit eob = createBasicEob();
    ExplanationOfBenefit.SupportingInformationComponent info =
        new ExplanationOfBenefit.SupportingInformationComponent();
    info.setSequence(1);
    info.setCategory(new CodeableConcept(new Coding("http://example.org", "info", "Info")));
    eob.addSupportingInfo(info);

    Claim claim = mapper.map(eob);

    assertNotNull(claim.getSupportingInfo());
    assertEquals(1, claim.getSupportingInfo().size());
    assertEquals(1, claim.getSupportingInfo().get(0).getSequence());
  }

  @Test
  void testMapNullEobThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> mapper.map(null));
  }

  @Test
  void testMapEmptyEobProducesValidClaim() {
    ExplanationOfBenefit eob = new ExplanationOfBenefit();

    Claim claim = mapper.map(eob);

    assertNotNull(claim);
    assertEquals(Claim.Use.PREAUTHORIZATION, claim.getUse());
    assertFalse(claim.hasItem());
    assertFalse(claim.hasDiagnosis());
  }

  private ExplanationOfBenefit createBasicEob() {
    ExplanationOfBenefit eob = new ExplanationOfBenefit();
    eob.setType(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/claim-type",
                "institutional",
                "Institutional")));
    eob.setPatient(new Reference("Patient/test-patient"));
    eob.setProvider(new Reference("Practitioner/test-provider"));
    return eob;
  }
}
