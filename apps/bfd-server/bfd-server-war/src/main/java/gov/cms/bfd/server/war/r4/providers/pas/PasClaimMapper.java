package gov.cms.bfd.server.war.r4.providers.pas;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;

/**
 * Maps BFD {@link ExplanationOfBenefit} resources into Da Vinci PAS-conformant {@link Claim}
 * resources. The resulting Claim uses {@code use: preauthorization} and is tagged with the PAS
 * Claim profile.
 */
public final class PasClaimMapper {

  /** Private constructor to prevent instantiation. */
  private PasClaimMapper() {
    // utility class
  }

  /**
   * Converts a BFD {@link ExplanationOfBenefit} into a PAS-conformant {@link Claim}.
   *
   * @param eob the source ExplanationOfBenefit from BFD
   * @param patientReference reference to the PAS Beneficiary resource
   * @param insurerReference reference to the PAS Insurer Organization
   * @param providerReference reference to the PAS Requestor Organization
   * @param coverageReference reference to the PAS Coverage resource
   * @return a PAS-conformant Claim resource
   */
  public static Claim mapEobToPasClaim(
      ExplanationOfBenefit eob,
      Reference patientReference,
      Reference insurerReference,
      Reference providerReference,
      Reference coverageReference) {

    Claim claim = new Claim();
    claim.setId(UUID.randomUUID().toString());

    // Set PAS profile
    Meta meta = new Meta();
    meta.addProfile(PasConstants.PROFILE_PAS_CLAIM);
    claim.setMeta(meta);

    // Status and use
    claim.setStatus(Claim.ClaimStatus.ACTIVE);
    claim.setUse(Claim.Use.PREAUTHORIZATION);

    // Claim type
    claim.setType(mapClaimType(eob));

    // Priority
    claim.setPriority(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(PasConstants.CODE_SYSTEM_PROCESS_PRIORITY)
                    .setCode("normal")
                    .setDisplay("Normal")));

    // References
    claim.setPatient(patientReference);
    claim.setInsurer(insurerReference);
    claim.setProvider(providerReference);
    claim.setCreated(eob.getCreated() != null ? eob.getCreated() : new Date());

    // Insurance
    Claim.InsuranceComponent insurance = claim.addInsurance();
    insurance.setSequence(1);
    insurance.setFocal(true);
    insurance.setCoverage(coverageReference);

    // Diagnosis
    mapDiagnoses(eob, claim);

    // Procedures
    mapProcedures(eob, claim);

    // Items
    mapItems(eob, claim);

    return claim;
  }

  /**
   * Maps the BFD EOB claim type to a PAS-compatible FHIR claim type.
   *
   * @param eob the source ExplanationOfBenefit
   * @return a CodeableConcept representing the FHIR claim type
   */
  static CodeableConcept mapClaimType(ExplanationOfBenefit eob) {
    CodeableConcept type = new CodeableConcept();
    String code = "institutional"; // default
    String display = "Institutional";

    if (eob.getType() != null && eob.getType().hasCoding()) {
      boolean matched = false;
      for (Coding coding : eob.getType().getCoding()) {
        String sourceCode = coding.getCode();
        if (sourceCode != null) {
          switch (sourceCode) {
            case "71":
            case "72":
            case "CARRIER":
            case "HHA":
            case "DME":
            case "professional":
              code = "professional";
              display = "Professional";
              matched = true;
              break;
            case "PDE":
            case "pharmacy":
              code = "pharmacy";
              display = "Pharmacy";
              matched = true;
              break;
            case "institutional":
              code = "institutional";
              display = "Institutional";
              matched = true;
              break;
            case "oral":
              code = "oral";
              display = "Oral";
              matched = true;
              break;
            case "vision":
              code = "vision";
              display = "Vision";
              matched = true;
              break;
            default:
              break;
          }
          if (matched) {
            break;
          }
        }
      }
    }

    type.addCoding(
        new Coding()
            .setSystem(PasConstants.CODE_SYSTEM_CLAIM_TYPE)
            .setCode(code)
            .setDisplay(display));
    return type;
  }

  /**
   * Maps diagnosis entries from EOB to PAS Claim.
   *
   * @param eob the source ExplanationOfBenefit
   * @param claim the target PAS Claim
   */
  private static void mapDiagnoses(ExplanationOfBenefit eob, Claim claim) {
    if (eob.getDiagnosis() == null || eob.getDiagnosis().isEmpty()) {
      return;
    }

    List<Claim.DiagnosisComponent> diagnoses = new ArrayList<>();
    for (ExplanationOfBenefit.DiagnosisComponent eobDiag : eob.getDiagnosis()) {
      Claim.DiagnosisComponent claimDiag = new Claim.DiagnosisComponent();
      claimDiag.setSequence(eobDiag.getSequence());

      if (eobDiag.getDiagnosisCodeableConcept() != null) {
        claimDiag.setDiagnosis(normalizeIcd10Code(eobDiag.getDiagnosisCodeableConcept()));
      }

      if (eobDiag.getType() != null && !eobDiag.getType().isEmpty()) {
        claimDiag.setType(eobDiag.getType());
      }

      diagnoses.add(claimDiag);
    }
    claim.setDiagnosis(diagnoses);
  }

  /**
   * Maps procedure entries from EOB to PAS Claim.
   *
   * @param eob the source ExplanationOfBenefit
   * @param claim the target PAS Claim
   */
  private static void mapProcedures(ExplanationOfBenefit eob, Claim claim) {
    if (eob.getProcedure() == null || eob.getProcedure().isEmpty()) {
      return;
    }

    List<Claim.ProcedureComponent> procedures = new ArrayList<>();
    for (ExplanationOfBenefit.ProcedureComponent eobProc : eob.getProcedure()) {
      Claim.ProcedureComponent claimProc = new Claim.ProcedureComponent();
      claimProc.setSequence(eobProc.getSequence());

      if (eobProc.getProcedureCodeableConcept() != null) {
        claimProc.setProcedure(eobProc.getProcedureCodeableConcept());
      }
      if (eobProc.getDate() != null) {
        claimProc.setDate(eobProc.getDate());
      }

      procedures.add(claimProc);
    }
    claim.setProcedure(procedures);
  }

  /**
   * Maps item/service line entries from EOB to PAS Claim.
   *
   * @param eob the source ExplanationOfBenefit
   * @param claim the target PAS Claim
   */
  private static void mapItems(ExplanationOfBenefit eob, Claim claim) {
    if (eob.getItem() == null || eob.getItem().isEmpty()) {
      return;
    }

    List<Claim.ItemComponent> items = new ArrayList<>();
    for (ExplanationOfBenefit.ItemComponent eobItem : eob.getItem()) {
      Claim.ItemComponent claimItem = new Claim.ItemComponent();
      claimItem.setSequence(eobItem.getSequence());

      if (eobItem.getProductOrService() != null) {
        claimItem.setProductOrService(eobItem.getProductOrService());
      }

      if (eobItem.getServiced() != null) {
        claimItem.setServiced(eobItem.getServiced());
      }

      if (eobItem.getQuantity() != null) {
        claimItem.setQuantity(eobItem.getQuantity());
      }

      if (eobItem.getLocation() != null) {
        claimItem.setLocation(eobItem.getLocation());
      }

      items.add(claimItem);
    }
    claim.setItem(items);
  }

  /**
   * Normalizes ICD-10 code system URLs to the standard FHIR ICD-10-CM system.
   *
   * @param source the original CodeableConcept
   * @return a new CodeableConcept with normalized system URL
   */
  static CodeableConcept normalizeIcd10Code(CodeableConcept source) {
    CodeableConcept normalized = new CodeableConcept();
    for (Coding coding : source.getCoding()) {
      String system = coding.getSystem();
      // Normalize known BFD/CMS ICD-10 URLs to the standard FHIR ICD-10-CM URL.
      // Use explicit equals() checks to avoid incorrectly rewriting non-CM variants
      // like ICD-10-PCS (http://hl7.org/fhir/sid/icd-10-pcs).
      if (system != null
          && (system.equals("http://hl7.org/fhir/sid/icd-10")
              || system.equals("http://www.cms.gov/Medicare/Coding/ICD10"))) {
        system = PasConstants.CODE_SYSTEM_ICD10_CM;
      }
      normalized.addCoding(
          new Coding()
              .setSystem(system)
              .setCode(coding.getCode())
              .setDisplay(coding.getDisplay())
              .setVersion(coding.getVersion()));
    }
    if (source.hasText()) {
      normalized.setText(source.getText());
    }
    return normalized;
  }
}
