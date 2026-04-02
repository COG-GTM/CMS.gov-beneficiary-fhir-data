package gov.cms.bfd.server.war.r4.providers.pas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

/**
 * Validates a PAS $submit Bundle. Checks that the Bundle structure is valid, the first entry is a
 * Claim with use=PREAUTHORIZATION, references resolve within the Bundle, and
 * supportingInfo.sequence values are unique.
 */
@Component
public class PasClaimValidator {

  /**
   * Validates a PAS $submit request Bundle.
   *
   * @param bundle the Bundle to validate
   * @return an OperationOutcome with any validation errors/warnings
   */
  public OperationOutcome validate(Bundle bundle) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    // Check bundle is not null and has entries
    if (bundle == null) {
      errors.add("Bundle must not be null");
      return buildOutcome(errors, warnings);
    }

    if (!bundle.hasEntry() || bundle.getEntry().isEmpty()) {
      errors.add("Bundle must have at least one entry");
      return buildOutcome(errors, warnings);
    }

    // Check first entry is a Claim resource
    Resource firstResource = bundle.getEntry().get(0).getResource();
    if (!(firstResource instanceof Claim)) {
      errors.add(
          "First entry in Bundle must be a Claim resource, found: "
              + (firstResource != null ? firstResource.fhirType() : "null"));
      return buildOutcome(errors, warnings);
    }

    Claim claim = (Claim) firstResource;

    // Check Claim.use == PREAUTHORIZATION
    if (claim.getUse() != Claim.Use.PREAUTHORIZATION) {
      errors.add(
          "Claim.use must be 'preauthorization', found: '"
              + (claim.hasUse() ? claim.getUse().toCode() : "null")
              + "'");
    }

    // Check required PAS elements
    if (!claim.hasPatient()) {
      errors.add("Claim must have a patient reference");
    }
    if (!claim.hasProvider()) {
      errors.add("Claim must have a provider reference");
    }
    if (!claim.hasPriority()) {
      errors.add("Claim must have a priority");
    }
    if (!claim.hasType()) {
      errors.add("Claim must have a type");
    }
    if (!claim.hasInsurance()) {
      errors.add("Claim must have at least one insurance");
    }

    // Validate reference integrity within Bundle
    if (!validateReferences(claim, bundle)) {
      errors.add("Not all references in the Claim resolve to resources in the Bundle");
    }

    // Validate unique supportingInfo.sequence
    if (!validateSupportingInfoSequence(claim)) {
      errors.add("Claim.supportingInfo.sequence values must be unique");
    }

    return buildOutcome(errors, warnings);
  }

  /**
   * Checks if the validation result indicates success (no errors).
   *
   * @param outcome the OperationOutcome to check
   * @return true if there are no error-level issues
   */
  public boolean isValid(OperationOutcome outcome) {
    if (outcome == null || !outcome.hasIssue()) {
      return true;
    }
    return outcome.getIssue().stream()
        .noneMatch(
            issue ->
                issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR
                    || issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL);
  }

  /**
   * Validates that all references in the Claim resolve to resources in the Bundle. Modeled after
   * ClaimEndpoint.validateReferences() in the PAS RI.
   *
   * @param claim the Claim to validate
   * @param bundle the Bundle containing referenced resources
   * @return true if all references resolve
   */
  private boolean validateReferences(Claim claim, Bundle bundle) {
    // Collect all resource fullUrls and IDs from the bundle
    Set<String> bundleResourceIds = new HashSet<>();
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      Resource resource = entry.getResource();
      if (resource != null) {
        if (entry.hasFullUrl()) {
          bundleResourceIds.add(entry.getFullUrl());
        }
        if (resource.hasId()) {
          bundleResourceIds.add(resource.fhirType() + "/" + resource.getIdElement().getIdPart());
          bundleResourceIds.add(resource.getIdElement().getIdPart());
        }
      }
    }

    // Collect references from the Claim
    List<String> referenceStrings = new ArrayList<>();
    if (claim.hasPatient() && claim.getPatient().hasReference()) {
      referenceStrings.add(claim.getPatient().getReference());
    }
    if (claim.hasProvider() && claim.getProvider().hasReference()) {
      referenceStrings.add(claim.getProvider().getReference());
    }
    if (claim.hasInsurance()) {
      for (Claim.InsuranceComponent ins : claim.getInsurance()) {
        if (ins.hasCoverage() && ins.getCoverage().hasReference()) {
          referenceStrings.add(ins.getCoverage().getReference());
        }
      }
    }

    // Check that all references resolve
    for (String ref : referenceStrings) {
      if (!bundleResourceIds.contains(ref)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Validates that supportingInfo.sequence values are unique within the Claim. Modeled after
   * ClaimEndpoint.validateSupportingInfoSequence() in the PAS RI.
   *
   * @param claim the Claim to validate
   * @return true if all sequence values are unique
   */
  private boolean validateSupportingInfoSequence(Claim claim) {
    if (!claim.hasSupportingInfo()) {
      return true;
    }

    Set<Integer> sequenceSet = new HashSet<>();
    for (Claim.SupportingInformationComponent info : claim.getSupportingInfo()) {
      if (!sequenceSet.add(info.getSequence())) {
        return false;
      }
    }

    return true;
  }

  /**
   * Builds an OperationOutcome from lists of errors and warnings.
   *
   * @param errors the error messages
   * @param warnings the warning messages
   * @return an OperationOutcome with the issues
   */
  private OperationOutcome buildOutcome(List<String> errors, List<String> warnings) {
    OperationOutcome outcome = new OperationOutcome();

    for (String error : errors) {
      outcome
          .addIssue()
          .setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setCode(OperationOutcome.IssueType.INVALID)
          .setDiagnostics(error);
    }

    for (String warning : warnings) {
      outcome
          .addIssue()
          .setSeverity(OperationOutcome.IssueSeverity.WARNING)
          .setCode(OperationOutcome.IssueType.INFORMATIONAL)
          .setDiagnostics(warning);
    }

    return outcome;
  }
}
