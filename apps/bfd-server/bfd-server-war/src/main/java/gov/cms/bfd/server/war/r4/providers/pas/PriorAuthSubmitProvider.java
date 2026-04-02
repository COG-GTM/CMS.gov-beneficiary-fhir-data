package gov.cms.bfd.server.war.r4.providers.pas;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HAPI FHIR resource provider implementing the PAS $submit operation on Claim resources. Accepts a
 * Bundle parameter containing a PAS-profiled Claim, validates it, and returns a response Bundle
 * containing a ClaimResponse.
 *
 * <p>Follows the pattern from ClaimEndpoint.submitOperation() in the PAS RI.
 */
@Component
public class PriorAuthSubmitProvider implements IResourceProvider {

  private static final Logger logger = LoggerFactory.getLogger(PriorAuthSubmitProvider.class);

  private final PasClaimValidator validator;
  private final ClaimResponseMapper claimResponseMapper;

  /**
   * Creates a new PriorAuthSubmitProvider.
   *
   * @param validator the claim validator
   * @param claimResponseMapper the claim response mapper
   */
  public PriorAuthSubmitProvider(
      PasClaimValidator validator, ClaimResponseMapper claimResponseMapper) {
    this.validator = validator;
    this.claimResponseMapper = claimResponseMapper;
  }

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return Claim.class;
  }

  /**
   * Implements the PAS $submit operation.
   *
   * <p>Accepts a Bundle containing a PAS-profiled Claim as the first entry, validates it, and
   * returns a response Bundle containing a PAS-profiled ClaimResponse.
   *
   * @param bundle the request Bundle containing the Claim and supporting resources
   * @return a response Bundle containing the ClaimResponse
   * @throws InvalidRequestException if the bundle is null or empty
   * @throws UnprocessableEntityException if the bundle fails validation
   */
  @Operation(name = "$submit", resourceType = Claim.class)
  public Bundle submitOperation(
      @OperationParam(name = "resource", min = 1, max = 1) Bundle bundle) {

    logger.info("PAS $submit operation invoked");

    if (bundle == null) {
      throw new InvalidRequestException("Request Bundle must not be null");
    }

    // Validate the bundle
    OperationOutcome outcome = validator.validate(bundle);
    if (!validator.isValid(outcome)) {
      logger.warn("PAS $submit validation failed: {}", formatOutcome(outcome));
      throw new UnprocessableEntityException(
          "PAS $submit validation failed", outcome);
    }

    // Extract the Claim from the bundle
    Resource firstResource = bundle.getEntry().get(0).getResource();
    Claim claim = (Claim) firstResource;

    logger.info("Processing PAS $submit for Claim with {} items",
        claim.hasItem() ? claim.getItem().size() : 0);

    // Build the ClaimResponse (default: all items pended)
    ClaimResponse claimResponse = claimResponseMapper.buildDefaultResponse(claim);

    // Wrap in response Bundle
    Bundle responseBundle = claimResponseMapper.wrapInBundle(claimResponse);

    logger.info("PAS $submit completed successfully with disposition: {}",
        claimResponse.getDisposition());

    return responseBundle;
  }

  /**
   * Formats an OperationOutcome into a human-readable string for logging.
   *
   * @param outcome the OperationOutcome
   * @return a formatted string
   */
  private String formatOutcome(OperationOutcome outcome) {
    if (outcome == null || !outcome.hasIssue()) {
      return "No issues";
    }
    StringBuilder sb = new StringBuilder();
    for (OperationOutcome.OperationOutcomeIssueComponent issue : outcome.getIssue()) {
      sb.append("[")
          .append(issue.getSeverity())
          .append("] ")
          .append(issue.getDiagnostics())
          .append("; ");
    }
    return sb.toString();
  }
}
