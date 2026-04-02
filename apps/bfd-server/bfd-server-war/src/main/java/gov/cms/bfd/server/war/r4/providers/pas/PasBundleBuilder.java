package gov.cms.bfd.server.war.r4.providers.pas;

import java.util.Date;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;

/**
 * Builds a Da Vinci PAS-conformant FHIR {@link Bundle} from BFD resources. The Bundle contains a
 * Claim, Patient, Coverage, and Organization resources assembled for prior authorization request
 * submission.
 */
public final class PasBundleBuilder {

  /** Private constructor to prevent instantiation. */
  private PasBundleBuilder() {
    // utility class
  }

  /**
   * Builds a complete PAS Request Bundle from BFD source resources.
   *
   * @param eob the BFD ExplanationOfBenefit to transform
   * @param patient the BFD Patient resource
   * @param coverage the BFD Coverage resource
   * @return a PAS-conformant FHIR Bundle
   */
  public static Bundle buildPasBundle(
      ExplanationOfBenefit eob, Patient patient, Coverage coverage) {

    Bundle bundle = new Bundle();
    bundle.setId(UUID.randomUUID().toString());
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.setTimestamp(new Date());

    // Set PAS Request Bundle profile
    Meta meta = new Meta();
    meta.addProfile(PasConstants.PROFILE_PAS_REQUEST_BUNDLE);
    bundle.setMeta(meta);

    // Build PAS Patient (Beneficiary)
    Patient pasPatient = buildPasBeneficiary(patient);
    String patientFullUrl = "urn:uuid:" + pasPatient.getId();

    // Build CMS Insurer Organization
    Organization insurer = buildCmsInsurer();
    String insurerFullUrl = "urn:uuid:" + UUID.randomUUID().toString();

    // Build Requestor Organization
    Organization requestor = buildRequestorOrganization(eob);
    String requestorFullUrl = "urn:uuid:" + requestor.getId();

    // Build PAS Coverage
    Coverage pasCoverage = buildPasCoverage(coverage, patientFullUrl, insurerFullUrl);
    String coverageFullUrl = "urn:uuid:" + pasCoverage.getId();

    // Build PAS Claim
    Claim pasClaim =
        PasClaimMapper.mapEobToPasClaim(
            eob,
            new Reference(patientFullUrl),
            new Reference(insurerFullUrl),
            new Reference(requestorFullUrl),
            new Reference(coverageFullUrl));

    // Add all resources to bundle
    addEntry(bundle, "urn:uuid:" + pasClaim.getId(), pasClaim);
    addEntry(bundle, patientFullUrl, pasPatient);
    addEntry(bundle, insurerFullUrl, insurer);
    addEntry(bundle, requestorFullUrl, requestor);
    addEntry(bundle, coverageFullUrl, pasCoverage);

    return bundle;
  }

  /**
   * Builds a PAS Beneficiary (Patient) from a BFD Patient resource.
   *
   * @param source the BFD Patient resource
   * @return a PAS-conformant Patient resource
   */
  static Patient buildPasBeneficiary(Patient source) {
    Patient patient = new Patient();
    patient.setId(UUID.randomUUID().toString());

    Meta meta = new Meta();
    meta.addProfile(PasConstants.PROFILE_PAS_BENEFICIARY);
    patient.setMeta(meta);

    // Copy identifiers (MBI)
    if (source.hasIdentifier()) {
      patient.setIdentifier(source.getIdentifier());
    }

    // Copy name
    if (source.hasName()) {
      patient.setName(source.getName());
    }

    // Copy gender
    if (source.hasGender()) {
      patient.setGender(source.getGender());
    }

    // Copy birthDate
    if (source.hasBirthDate()) {
      patient.setBirthDate(source.getBirthDate());
    }

    // Copy address
    if (source.hasAddress()) {
      patient.setAddress(source.getAddress());
    }

    return patient;
  }

  /**
   * Builds the CMS Insurer Organization resource.
   *
   * @return an Organization representing CMS as the insurer
   */
  static Organization buildCmsInsurer() {
    Organization org = new Organization();
    org.setId(PasConstants.INSURER_RESOURCE_ID);

    Meta meta = new Meta();
    meta.addProfile(PasConstants.PROFILE_PAS_INSURER);
    org.setMeta(meta);

    org.setActive(true);
    org.setName(PasConstants.CMS_ORG_NAME);

    // NPI identifier
    org.addIdentifier(
        new Identifier()
            .setSystem(PasConstants.CODE_SYSTEM_NPI)
            .setValue(PasConstants.CMS_ORG_NPI));

    // Organization type
    org.addType(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/organization-type")
                    .setCode("ins")
                    .setDisplay("Insurance Company")));

    return org;
  }

  /**
   * Builds the Requestor Organization from EOB provider data.
   *
   * @param eob the source ExplanationOfBenefit
   * @return an Organization representing the requesting provider
   */
  static Organization buildRequestorOrganization(ExplanationOfBenefit eob) {
    Organization org = new Organization();
    org.setId(UUID.randomUUID().toString());

    Meta meta = new Meta();
    meta.addProfile(PasConstants.PROFILE_PAS_REQUESTOR);
    org.setMeta(meta);

    org.setActive(true);
    org.setName("Provider Organization");

    // Extract NPI from EOB provider if available
    if (eob.getProvider() != null && eob.getProvider().hasIdentifier()) {
      Identifier providerIdent = eob.getProvider().getIdentifier();
      org.addIdentifier(
          new Identifier()
              .setSystem(PasConstants.CODE_SYSTEM_NPI)
              .setValue(providerIdent.getValue()));
    } else {
      // Default NPI for sample data
      org.addIdentifier(
          new Identifier().setSystem(PasConstants.CODE_SYSTEM_NPI).setValue("9999999999"));
    }

    // Organization type
    org.addType(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/organization-type")
                    .setCode("prov")
                    .setDisplay("Healthcare Provider")));

    return org;
  }

  /**
   * Builds a PAS Coverage from a BFD Coverage resource.
   *
   * @param source the BFD Coverage resource
   * @param patientReference the full URL reference to the PAS Patient
   * @param insurerReference the full URL reference to the PAS Insurer
   * @return a PAS-conformant Coverage resource
   */
  static Coverage buildPasCoverage(
      Coverage source, String patientReference, String insurerReference) {
    Coverage coverage = new Coverage();
    coverage.setId(UUID.randomUUID().toString());

    Meta meta = new Meta();
    meta.addProfile(PasConstants.PROFILE_PAS_COVERAGE);
    coverage.setMeta(meta);

    coverage.setStatus(source.hasStatus() ? source.getStatus() : Coverage.CoverageStatus.ACTIVE);

    coverage.setBeneficiary(new Reference(patientReference));
    coverage.addPayor(new Reference(insurerReference));

    if (source.hasSubscriberId()) {
      coverage.setSubscriberId(source.getSubscriberId());
    }

    if (source.hasRelationship()) {
      coverage.setRelationship(source.getRelationship());
    } else {
      coverage.setRelationship(
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem("http://terminology.hl7.org/CodeSystem/subscriber-relationship")
                      .setCode("self")
                      .setDisplay("Self")));
    }

    return coverage;
  }

  /**
   * Adds a resource entry to the Bundle.
   *
   * @param bundle the target Bundle
   * @param fullUrl the full URL for the entry
   * @param resource the FHIR resource to add
   */
  private static void addEntry(
      Bundle bundle, String fullUrl, org.hl7.fhir.r4.model.Resource resource) {
    bundle.addEntry().setFullUrl(fullUrl).setResource(resource);
  }
}
