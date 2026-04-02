package gov.cms.bfd.server.war.r4.providers.pas;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

/**
 * Maps a BFD Patient (C4BB/US Core profile) to a PAS Beneficiary. Mostly pass-through but ensures
 * MBI identifier is present and US Core required elements (name, gender, birthDate) are present.
 */
@Component
public class PatientToPasBeneficiaryMapper {

  /**
   * Maps a BFD Patient to a PAS-compatible Beneficiary (Patient resource).
   *
   * @param patient the source Patient resource
   * @return the mapped Patient resource with PAS-required elements validated
   * @throws IllegalArgumentException if the patient is null or missing required elements
   */
  public Patient map(Patient patient) {
    if (patient == null) {
      throw new IllegalArgumentException("Patient must not be null");
    }

    validateRequiredElements(patient);

    // The patient is mostly pass-through; we ensure required elements exist
    Patient beneficiary = patient.copy();

    // Set PAS Beneficiary profile
    Meta meta = new Meta();
    meta.addProfile(PasConstants.PROFILE_PAS_BENEFICIARY);
    beneficiary.setMeta(meta);

    // Ensure MBI identifier is present
    if (!hasMbiIdentifier(beneficiary)) {
      throw new IllegalArgumentException(
          "Patient must have an MBI identifier with system " + PasConstants.MBI_IDENTIFIER_SYSTEM);
    }

    return beneficiary;
  }

  /**
   * Checks if the patient has an MBI identifier.
   *
   * @param patient the patient to check
   * @return true if an MBI identifier is present
   */
  private boolean hasMbiIdentifier(Patient patient) {
    if (!patient.hasIdentifier()) {
      return false;
    }
    for (Identifier identifier : patient.getIdentifier()) {
      if (PasConstants.MBI_IDENTIFIER_SYSTEM.equals(identifier.getSystem())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Validates that US Core required elements are present.
   *
   * @param patient the patient to validate
   * @throws IllegalArgumentException if required elements are missing
   */
  private void validateRequiredElements(Patient patient) {
    if (!patient.hasName()) {
      throw new IllegalArgumentException("Patient must have at least one name (US Core required)");
    }
    if (!patient.hasGender()) {
      throw new IllegalArgumentException("Patient must have a gender (US Core required)");
    }
    if (!patient.hasBirthDate()) {
      throw new IllegalArgumentException("Patient must have a birthDate (US Core required)");
    }
  }
}
