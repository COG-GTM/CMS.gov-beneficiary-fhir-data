package gov.cms.bfd.server.war.r4.providers.pas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link ClaimResponseMapper}. */
class ClaimResponseMapperTest {

  private ClaimResponseMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ClaimResponseMapper();
  }

  @Test
  void testBuildResponseWithGrantedDisposition() {
    Claim claim = createClaimWithItems(2);
    List<PasConstants.ReviewAction> actions =
        Arrays.asList(PasConstants.ReviewAction.APPROVED, PasConstants.ReviewAction.APPROVED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    assertNotNull(response);
    assertEquals("Granted", response.getDisposition());
    assertEquals(ClaimResponse.RemittanceOutcome.COMPLETE, response.getOutcome());
  }

  @Test
  void testBuildResponseWithDeniedDisposition() {
    Claim claim = createClaimWithItems(2);
    List<PasConstants.ReviewAction> actions =
        Arrays.asList(PasConstants.ReviewAction.DENIED, PasConstants.ReviewAction.DENIED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    assertEquals("Denied", response.getDisposition());
    assertEquals(ClaimResponse.RemittanceOutcome.COMPLETE, response.getOutcome());
  }

  @Test
  void testBuildResponseWithPartialDisposition() {
    Claim claim = createClaimWithItems(2);
    List<PasConstants.ReviewAction> actions =
        Arrays.asList(PasConstants.ReviewAction.APPROVED, PasConstants.ReviewAction.DENIED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    assertEquals("Partial", response.getDisposition());
    assertEquals(ClaimResponse.RemittanceOutcome.PARTIAL, response.getOutcome());
  }

  @Test
  void testBuildResponseWithPendingDisposition() {
    Claim claim = createClaimWithItems(2);
    List<PasConstants.ReviewAction> actions =
        Arrays.asList(PasConstants.ReviewAction.APPROVED, PasConstants.ReviewAction.PENDED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    assertEquals("Pending", response.getDisposition());
    assertEquals(ClaimResponse.RemittanceOutcome.QUEUED, response.getOutcome());
  }

  @Test
  void testBuildResponseWithCancelledDisposition() {
    Claim claim = createClaimWithItems(1);
    List<PasConstants.ReviewAction> actions =
        Collections.singletonList(PasConstants.ReviewAction.CANCELLED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    assertNotNull(response);
    assertEquals("Cancelled", response.getDisposition());
    assertEquals(ClaimResponse.RemittanceOutcome.COMPLETE, response.getOutcome());
  }

  @Test
  void testBuildResponseSetsMetaProfile() {
    Claim claim = createClaimWithItems(1);
    List<PasConstants.ReviewAction> actions =
        Collections.singletonList(PasConstants.ReviewAction.APPROVED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    assertTrue(
        response.getMeta().getProfile().stream()
            .anyMatch(p -> PasConstants.PAS_CLAIM_RESPONSE_PROFILE_URL.equals(p.getValue())));
  }

  @Test
  void testBuildResponseSetsUseToPreauthorization() {
    Claim claim = createClaimWithItems(1);
    List<PasConstants.ReviewAction> actions =
        Collections.singletonList(PasConstants.ReviewAction.APPROVED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    assertEquals(ClaimResponse.Use.PREAUTHORIZATION, response.getUse());
  }

  @Test
  void testBuildResponseHasReviewActionExtensions() {
    Claim claim = createClaimWithItems(1);
    List<PasConstants.ReviewAction> actions =
        Collections.singletonList(PasConstants.ReviewAction.APPROVED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    assertNotNull(response.getItem());
    assertEquals(1, response.getItem().size());

    ClaimResponse.ItemComponent item = response.getItem().get(0);
    // reviewAction extension is on adjudication per PAS IG
    assertTrue(
        item.getAdjudication().stream()
            .flatMap(adj -> adj.getExtension().stream())
            .anyMatch(e -> PasConstants.REVIEW_ACTION_EXTENSION_URL.equals(e.getUrl())));
  }

  @Test
  void testBuildResponseHasAuthorizationNumberExtension() {
    Claim claim = createClaimWithItems(1);
    List<PasConstants.ReviewAction> actions =
        Collections.singletonList(PasConstants.ReviewAction.APPROVED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    ClaimResponse.ItemComponent item = response.getItem().get(0);
    assertTrue(
        item.getExtension().stream()
            .anyMatch(e -> PasConstants.AUTHORIZATION_NUMBER_EXTENSION_URL.equals(e.getUrl())));
  }

  @Test
  void testBuildResponseHasPreAuthPeriodExtension() {
    Claim claim = createClaimWithItems(1);
    List<PasConstants.ReviewAction> actions =
        Collections.singletonList(PasConstants.ReviewAction.APPROVED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    ClaimResponse.ItemComponent item = response.getItem().get(0);
    assertTrue(
        item.getExtension().stream()
            .anyMatch(e -> PasConstants.ITEM_PREAUTH_PERIOD_EXTENSION_URL.equals(e.getUrl())));
  }

  @Test
  void testBuildResponseHasPreAuthIssueDateExtension() {
    Claim claim = createClaimWithItems(1);
    List<PasConstants.ReviewAction> actions =
        Collections.singletonList(PasConstants.ReviewAction.APPROVED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    ClaimResponse.ItemComponent item = response.getItem().get(0);
    assertTrue(
        item.getExtension().stream()
            .anyMatch(e -> PasConstants.ITEM_PREAUTH_ISSUE_DATE_EXTENSION_URL.equals(e.getUrl())));
  }

  @Test
  void testBuildResponseReviewActionUsesX12CodeSystem() {
    Claim claim = createClaimWithItems(1);
    List<PasConstants.ReviewAction> actions =
        Collections.singletonList(PasConstants.ReviewAction.APPROVED);

    ClaimResponse response = mapper.buildResponse(claim, actions);

    ClaimResponse.ItemComponent item = response.getItem().get(0);
    // Find the reviewAction extension on adjudication per PAS IG
    Extension reviewAction =
        item.getAdjudication().stream()
            .flatMap(adj -> adj.getExtension().stream())
            .filter(e -> PasConstants.REVIEW_ACTION_EXTENSION_URL.equals(e.getUrl()))
            .findFirst()
            .orElse(null);
    assertNotNull(reviewAction, "reviewAction extension must be present on adjudication");

    // The reviewAction extension contains a nested extension with the code
    Extension codeExt =
        reviewAction.getExtension().stream()
            .filter(e -> PasConstants.REVIEW_ACTION_CODE_EXTENSION_URL.equals(e.getUrl()))
            .findFirst()
            .orElse(null);
    assertNotNull(codeExt, "reviewActionCode extension must be present");

    // Verify the coding uses the X12 Review Action code system
    assertTrue(
        codeExt.getValue() instanceof CodeableConcept,
        "reviewActionCode value must be a CodeableConcept");
    CodeableConcept codeValue = (CodeableConcept) codeExt.getValue();
    assertTrue(
        codeValue.getCoding().stream()
            .anyMatch(c -> PasConstants.X12_REVIEW_ACTION_CODE_SYSTEM.equals(c.getSystem())),
        "reviewActionCode must use X12 code system: " + PasConstants.X12_REVIEW_ACTION_CODE_SYSTEM);
  }

  @Test
  void testBuildDefaultResponse() {
    Claim claim = createClaimWithItems(3);

    ClaimResponse response = mapper.buildDefaultResponse(claim);

    assertNotNull(response);
    assertEquals("Pending", response.getDisposition());
    assertEquals(ClaimResponse.RemittanceOutcome.QUEUED, response.getOutcome());
    assertEquals(3, response.getItem().size());
  }

  @Test
  void testWrapInBundle() {
    Claim claim = createClaimWithItems(1);
    ClaimResponse response =
        mapper.buildResponse(claim, Collections.singletonList(PasConstants.ReviewAction.APPROVED));

    Bundle bundle = mapper.wrapInBundle(response);

    assertNotNull(bundle);
    assertEquals(Bundle.BundleType.COLLECTION, bundle.getType());
    assertEquals(1, bundle.getEntry().size());
    assertTrue(bundle.getEntry().get(0).getResource() instanceof ClaimResponse);

    // Verify Bundle meta.profile includes PAS Response Bundle profile
    assertNotNull(bundle.getMeta(), "Bundle.meta must be present");
    assertTrue(
        bundle.getMeta().getProfile().stream()
            .anyMatch(p -> PasConstants.PAS_RESPONSE_BUNDLE_PROFILE_URL.equals(p.getValue())),
        "Bundle.meta.profile must include PAS Response Bundle profile URL");
  }

  @Test
  void testBuildResponseNullClaimThrowsException() {
    assertThrows(
        IllegalArgumentException.class, () -> mapper.buildResponse(null, Collections.emptyList()));
  }

  @Test
  void testDetermineDispositionEmptyList() {
    assertEquals(
        PasConstants.Disposition.PENDING,
        ClaimResponseMapper.determineDisposition(Collections.emptyList()));
  }

  @Test
  void testDetermineDispositionNull() {
    assertEquals(PasConstants.Disposition.PENDING, ClaimResponseMapper.determineDisposition(null));
  }

  @Test
  void testMapOutcomeGranted() {
    assertEquals(
        ClaimResponse.RemittanceOutcome.COMPLETE,
        ClaimResponseMapper.mapOutcome(PasConstants.Disposition.GRANTED));
  }

  @Test
  void testMapOutcomeDenied() {
    assertEquals(
        ClaimResponse.RemittanceOutcome.COMPLETE,
        ClaimResponseMapper.mapOutcome(PasConstants.Disposition.DENIED));
  }

  @Test
  void testMapOutcomePartial() {
    assertEquals(
        ClaimResponse.RemittanceOutcome.PARTIAL,
        ClaimResponseMapper.mapOutcome(PasConstants.Disposition.PARTIAL));
  }

  @Test
  void testMapOutcomePending() {
    assertEquals(
        ClaimResponse.RemittanceOutcome.QUEUED,
        ClaimResponseMapper.mapOutcome(PasConstants.Disposition.PENDING));
  }

  @Test
  void testMapOutcomeNull() {
    assertEquals(ClaimResponse.RemittanceOutcome.QUEUED, ClaimResponseMapper.mapOutcome(null));
  }

  private Claim createClaimWithItems(int itemCount) {
    Claim claim = new Claim();
    claim.setUse(Claim.Use.PREAUTHORIZATION);
    claim.setType(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/claim-type",
                "professional",
                "Professional")));
    claim.setPatient(new Reference("Patient/test-patient"));

    for (int i = 1; i <= itemCount; i++) {
      Claim.ItemComponent item = new Claim.ItemComponent();
      item.setSequence(i);
      item.setProductOrService(
          new CodeableConcept(
              new Coding("http://www.ama-assn.org/go/cpt", "9921" + i, "Service " + i)));
      claim.addItem(item);
    }

    return claim;
  }
}
