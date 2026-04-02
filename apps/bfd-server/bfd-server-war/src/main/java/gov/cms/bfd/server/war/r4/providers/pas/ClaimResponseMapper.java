package gov.cms.bfd.server.war.r4.providers.pas;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RemittanceOutcome;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

/**
 * Builds a PAS-profiled {@link ClaimResponse} from a submitted {@link Claim}. Includes
 * reviewAction extensions, authorizationNumber, and disposition aggregation logic modeled after the
 * PAS RI's ClaimResponseFactory.determineDisposition().
 */
@Component
public class ClaimResponseMapper {

  /**
   * Builds a PAS-profiled ClaimResponse from the given Claim and review actions.
   *
   * @param claim the submitted Claim
   * @param itemReviewActions the review action for each item (parallel to claim.item)
   * @return a PAS-profiled ClaimResponse
   */
  public ClaimResponse buildResponse(
      Claim claim, List<PasConstants.ReviewAction> itemReviewActions) {
    if (claim == null) {
      throw new IllegalArgumentException("Claim must not be null");
    }

    ClaimResponse response = new ClaimResponse();

    // Set PAS ClaimResponse profile
    Meta meta = new Meta();
    meta.addProfile(PasConstants.PAS_CLAIM_RESPONSE_PROFILE_URL);
    response.setMeta(meta);

    response.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
    response.setType(claim.getType());
    response.setUse(ClaimResponse.Use.PREAUTHORIZATION);
    response.setCreated(new Date());

    // Set patient reference
    if (claim.hasPatient()) {
      response.setPatient(claim.getPatient());
    }

    // Set insurer
    response.setInsurer(new Reference().setDisplay("CMS"));

    // Map items with reviewAction extensions
    List<ClaimResponse.ItemComponent> responseItems = new ArrayList<>();
    if (claim.hasItem()) {
      for (int i = 0; i < claim.getItem().size(); i++) {
        Claim.ItemComponent claimItem = claim.getItem().get(i);
        PasConstants.ReviewAction reviewAction =
            (itemReviewActions != null && i < itemReviewActions.size())
                ? itemReviewActions.get(i)
                : PasConstants.ReviewAction.PENDED;

        ClaimResponse.ItemComponent responseItem = new ClaimResponse.ItemComponent();
        responseItem.setItemSequence(claimItem.getSequence());

        // Add reviewAction extension
        Extension reviewActionExt = new Extension(PasConstants.REVIEW_ACTION_EXTENSION_URL);
        reviewActionExt.addExtension(
            new Extension(
                PasConstants.REVIEW_ACTION_CODE_EXTENSION_URL,
                reviewAction.toCodeableConcept()));
        responseItem.addExtension(reviewActionExt);

        // Add authorizationNumber extension
        responseItem.addExtension(
            new Extension(
                PasConstants.AUTHORIZATION_NUMBER_EXTENSION_URL,
                new StringType(UUID.randomUUID().toString())));

        // Add itemPreAuthPeriod extension
        Period preAuthPeriod = new Period();
        preAuthPeriod.setStart(new Date());
        // Set end to 90 days from now
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, 90);
        preAuthPeriod.setEnd(cal.getTime());
        responseItem.addExtension(
            new Extension(PasConstants.ITEM_PREAUTH_PERIOD_EXTENSION_URL, preAuthPeriod));

        // Add itemPreAuthIssueDate extension
        responseItem.addExtension(
            new Extension(
                PasConstants.ITEM_PREAUTH_ISSUE_DATE_EXTENSION_URL,
                new org.hl7.fhir.r4.model.DateType(new Date())));

        // Add empty adjudication to satisfy HAPI requirements
        ClaimResponse.AdjudicationComponent adjudication =
            new ClaimResponse.AdjudicationComponent();
        adjudication.setCategory(
            new CodeableConcept()
                .addCoding(
                    new org.hl7.fhir.r4.model.Coding(
                        "http://terminology.hl7.org/CodeSystem/adjudication",
                        "submitted",
                        "Submitted Amount")));
        responseItem.addAdjudication(adjudication);

        responseItems.add(responseItem);
      }
    }
    response.setItem(responseItems);

    // Determine disposition
    PasConstants.Disposition disposition = determineDisposition(itemReviewActions);
    response.setDisposition(disposition.value());

    // Map outcome from disposition
    response.setOutcome(mapOutcome(disposition));

    return response;
  }

  /**
   * Builds a default PAS-profiled ClaimResponse with all items pended.
   *
   * @param claim the submitted Claim
   * @return a PAS-profiled ClaimResponse with pended items
   */
  public ClaimResponse buildDefaultResponse(Claim claim) {
    if (claim == null || !claim.hasItem()) {
      return buildResponse(claim, null);
    }
    List<PasConstants.ReviewAction> actions = new ArrayList<>();
    for (int i = 0; i < claim.getItem().size(); i++) {
      actions.add(PasConstants.ReviewAction.PENDED);
    }
    return buildResponse(claim, actions);
  }

  /**
   * Wraps a ClaimResponse in a response Bundle.
   *
   * @param claimResponse the ClaimResponse to wrap
   * @return a Bundle containing the ClaimResponse
   */
  public Bundle wrapInBundle(ClaimResponse claimResponse) {
    Bundle bundle = new Bundle();
    Meta meta = new Meta();
    meta.addProfile(PasConstants.PAS_RESPONSE_BUNDLE_PROFILE_URL);
    bundle.setMeta(meta);
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.addEntry().setResource(claimResponse);
    return bundle;
  }

  /**
   * Determines the overall disposition from individual item review actions. Modeled after
   * ClaimResponseFactory.determineDisposition() in the PAS RI.
   *
   * @param itemReviewActions the review actions for each item
   * @return the aggregated Disposition
   */
  public static PasConstants.Disposition determineDisposition(
      List<PasConstants.ReviewAction> itemReviewActions) {
    if (itemReviewActions == null || itemReviewActions.isEmpty()) {
      return PasConstants.Disposition.PENDING;
    }

    boolean atLeastOneGranted = false;
    boolean atLeastOneDenied = false;
    boolean atLeastOnePended = false;

    for (PasConstants.ReviewAction action : itemReviewActions) {
      if (action == PasConstants.ReviewAction.APPROVED
          || action == PasConstants.ReviewAction.PARTIAL) {
        atLeastOneGranted = true;
      } else if (action == PasConstants.ReviewAction.DENIED) {
        atLeastOneDenied = true;
      } else if (action == PasConstants.ReviewAction.PENDED
          || action == PasConstants.ReviewAction.PENDEDFOLLOWUP) {
        atLeastOnePended = true;
      } else if (action == PasConstants.ReviewAction.CANCELLED) {
        return PasConstants.Disposition.CANCELLED;
      }
    }

    if (atLeastOnePended) {
      return PasConstants.Disposition.PENDING;
    } else if (atLeastOneGranted && atLeastOneDenied) {
      return PasConstants.Disposition.PARTIAL;
    } else if (atLeastOneGranted && !atLeastOneDenied) {
      return PasConstants.Disposition.GRANTED;
    } else if (atLeastOneDenied && !atLeastOneGranted) {
      return PasConstants.Disposition.DENIED;
    }

    return PasConstants.Disposition.UNKNOWN;
  }

  /**
   * Maps a Disposition to a FHIR RemittanceOutcome.
   *
   * @param disposition the disposition
   * @return the corresponding RemittanceOutcome
   */
  public static RemittanceOutcome mapOutcome(PasConstants.Disposition disposition) {
    if (disposition == null) {
      return RemittanceOutcome.QUEUED;
    }
    switch (disposition) {
      case GRANTED:
      case DENIED:
      case CANCELLED:
        return RemittanceOutcome.COMPLETE;
      case PARTIAL:
        return RemittanceOutcome.PARTIAL;
      case PENDING:
      case UNKNOWN:
      default:
        return RemittanceOutcome.QUEUED;
    }
  }
}
