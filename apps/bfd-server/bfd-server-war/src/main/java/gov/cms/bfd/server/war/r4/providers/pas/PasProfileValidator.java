package gov.cms.bfd.server.war.r4.providers.pas;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

/**
 * Validates FHIR resources against Da Vinci PAS profile requirements. This validator checks
 * structural conformance rules that can be verified without external StructureDefinition resources,
 * including required fields, profile declarations, and cardinality constraints.
 */
public final class PasProfileValidator {

  /** Shared FHIR context for R4. */
  private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

  /** Private constructor to prevent instantiation. */
  private PasProfileValidator() {
    // utility class
  }

  /**
   * Validates a PAS Request Bundle for structural conformance.
   *
   * @param bundle the Bundle to validate
   * @return a list of validation issues; empty if the Bundle is valid
   */
  public static List<String> validatePasBundle(Bundle bundle) {
    List<String> issues = new ArrayList<>();

    // Bundle-level checks
    if (bundle.getType() != Bundle.BundleType.COLLECTION) {
      issues.add("Bundle.type must be 'collection', found: " + bundle.getType());
    }

    if (!hasProfile(bundle.getMeta(), PasConstants.PROFILE_PAS_REQUEST_BUNDLE)) {
      issues.add(
          "Bundle.meta.profile must include PAS Request Bundle profile: "
              + PasConstants.PROFILE_PAS_REQUEST_BUNDLE);
    }

    if (bundle.getEntry().isEmpty()) {
      issues.add("Bundle must contain at least one entry");
      return issues;
    }

    // Check for required resource types
    boolean hasClaim = false;
    boolean hasPatient = false;
    boolean hasCoverage = false;
    boolean hasInsurer = false;

    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      Resource resource = entry.getResource();
      if (resource == null) {
        issues.add("Bundle entry has null resource at fullUrl: " + entry.getFullUrl());
        continue;
      }

      if (resource instanceof Claim) {
        hasClaim = true;
        issues.addAll(validatePasClaim((Claim) resource));
      } else if (resource instanceof Patient) {
        hasPatient = true;
        issues.addAll(validatePasPatient((Patient) resource));
      } else if (resource instanceof Coverage) {
        hasCoverage = true;
        issues.addAll(validatePasCoverage((Coverage) resource));
      } else if (resource instanceof Organization) {
        hasInsurer = true;
        issues.addAll(validatePasOrganization((Organization) resource));
      }
    }

    if (!hasClaim) {
      issues.add("Bundle must contain a Claim resource");
    }
    if (!hasPatient) {
      issues.add("Bundle must contain a Patient resource");
    }
    if (!hasCoverage) {
      issues.add("Bundle must contain a Coverage resource");
    }
    if (!hasInsurer) {
      issues.add("Bundle must contain at least one Organization resource");
    }

    return issues;
  }

  /**
   * Validates a PAS Claim resource.
   *
   * @param claim the Claim to validate
   * @return a list of validation issues
   */
  public static List<String> validatePasClaim(Claim claim) {
    List<String> issues = new ArrayList<>();

    if (!hasProfile(claim.getMeta(), PasConstants.PROFILE_PAS_CLAIM)) {
      issues.add("Claim.meta.profile must include PAS Claim profile");
    }

    if (claim.getStatus() == null) {
      issues.add("Claim.status is required");
    }

    if (claim.getUse() == null) {
      issues.add("Claim.use is required");
    } else if (claim.getUse() != Claim.Use.PREAUTHORIZATION) {
      issues.add("Claim.use must be 'preauthorization' for PAS, found: " + claim.getUse());
    }

    if (claim.getType() == null || !claim.getType().hasCoding()) {
      issues.add("Claim.type is required and must have a coding");
    }

    if (claim.getPatient() == null || !claim.getPatient().hasReference()) {
      issues.add("Claim.patient reference is required");
    }

    if (claim.getInsurer() == null || !claim.getInsurer().hasReference()) {
      issues.add("Claim.insurer reference is required");
    }

    if (claim.getProvider() == null || !claim.getProvider().hasReference()) {
      issues.add("Claim.provider reference is required");
    }

    if (claim.getPriority() == null || !claim.getPriority().hasCoding()) {
      issues.add("Claim.priority is required and must have a coding");
    }

    if (claim.getInsurance().isEmpty()) {
      issues.add("Claim.insurance is required (at least one entry)");
    } else {
      Claim.InsuranceComponent ins = claim.getInsuranceFirstRep();
      if (!ins.getFocal()) {
        issues.add("Claim.insurance[0].focal must be true");
      }
      if (ins.getCoverage() == null || !ins.getCoverage().hasReference()) {
        issues.add("Claim.insurance[0].coverage reference is required");
      }
    }

    return issues;
  }

  /**
   * Validates a PAS Patient (Beneficiary) resource.
   *
   * @param patient the Patient to validate
   * @return a list of validation issues
   */
  public static List<String> validatePasPatient(Patient patient) {
    List<String> issues = new ArrayList<>();

    if (!hasProfile(patient.getMeta(), PasConstants.PROFILE_PAS_BENEFICIARY)) {
      issues.add("Patient.meta.profile must include PAS Beneficiary profile");
    }

    if (!patient.hasName()) {
      issues.add("Patient.name is required");
    }

    if (!patient.hasGender()) {
      issues.add("Patient.gender is required");
    }

    if (!patient.hasBirthDate()) {
      issues.add("Patient.birthDate is required");
    }

    return issues;
  }

  /**
   * Validates a PAS Coverage resource.
   *
   * @param coverage the Coverage to validate
   * @return a list of validation issues
   */
  public static List<String> validatePasCoverage(Coverage coverage) {
    List<String> issues = new ArrayList<>();

    if (!hasProfile(coverage.getMeta(), PasConstants.PROFILE_PAS_COVERAGE)) {
      issues.add("Coverage.meta.profile must include PAS Coverage profile");
    }

    if (!coverage.hasStatus()) {
      issues.add("Coverage.status is required");
    }

    if (!coverage.hasBeneficiary()) {
      issues.add("Coverage.beneficiary reference is required");
    }

    if (coverage.getPayor().isEmpty()) {
      issues.add("Coverage.payor is required (at least one entry)");
    }

    return issues;
  }

  /**
   * Validates a PAS Organization resource (Insurer or Requestor).
   *
   * @param org the Organization to validate
   * @return a list of validation issues
   */
  public static List<String> validatePasOrganization(Organization org) {
    List<String> issues = new ArrayList<>();

    boolean hasInsurerProfile = hasProfile(org.getMeta(), PasConstants.PROFILE_PAS_INSURER);
    boolean hasRequestorProfile = hasProfile(org.getMeta(), PasConstants.PROFILE_PAS_REQUESTOR);

    if (!hasInsurerProfile && !hasRequestorProfile) {
      issues.add("Organization.meta.profile must include either PAS Insurer or Requestor profile");
    }

    if (!org.hasActive()) {
      issues.add("Organization.active is required");
    }

    if (!org.hasName()) {
      issues.add("Organization.name is required");
    }

    if (!org.hasIdentifier()) {
      issues.add("Organization.identifier is required (NPI)");
    }

    return issues;
  }

  /**
   * Serializes a FHIR resource to JSON string.
   *
   * @param resource the resource to serialize
   * @return JSON string representation
   */
  public static String toJson(Resource resource) {
    IParser parser = FHIR_CONTEXT.newJsonParser().setPrettyPrint(true);
    return parser.encodeResourceToString(resource);
  }

  /**
   * Checks if a Meta element declares a specific profile.
   *
   * @param meta the Meta element to check
   * @param profileUrl the profile URL to look for
   * @return true if the profile is declared
   */
  private static boolean hasProfile(Meta meta, String profileUrl) {
    if (meta == null || meta.getProfile().isEmpty()) {
      return false;
    }
    return meta.getProfile().stream()
        .anyMatch(canonical -> profileUrl.equals(canonical.getValue()));
  }
}
