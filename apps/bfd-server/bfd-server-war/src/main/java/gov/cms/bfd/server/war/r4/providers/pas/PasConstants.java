package gov.cms.bfd.server.war.r4.providers.pas;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

/** Constants for the Da Vinci PAS (Prior Authorization Support) integration. */
public final class PasConstants {

  private PasConstants() {}

  // PAS Profile URLs
  public static final String PAS_CLAIM_PROFILE_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claim";
  public static final String PAS_CLAIM_RESPONSE_PROFILE_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse";
  public static final String PAS_RESPONSE_BUNDLE_PROFILE_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-pas-response-bundle";

  // FHIR Extension URLs
  public static final String REVIEW_ACTION_EXTENSION_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction";
  public static final String REVIEW_ACTION_CODE_EXTENSION_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewActionCode";
  public static final String AUTHORIZATION_NUMBER_EXTENSION_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-authorizationNumber";
  public static final String ITEM_TRACE_NUMBER_EXTENSION_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemTraceNumber";
  public static final String ITEM_PREAUTH_PERIOD_EXTENSION_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemPreAuthPeriod";
  public static final String ITEM_PREAUTH_ISSUE_DATE_EXTENSION_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemPreAuthIssueDate";
  public static final String ITEM_AUTHORIZED_PROVIDER_EXTENSION_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemAuthorizedProvider";
  public static final String ITEM_REQUESTED_SERVICE_DATE =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemRequestedServiceDate";
  public static final String ITEM_CANCELLED_EXTENSION_URL =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemCancelled";

  // X12 Code Systems
  public static final String X12_REVIEW_ACTION_CODE_SYSTEM =
      "https://codesystem.x12.org/005010/306";
  public static final String X12_REVIEW_REASON_CODE_SYSTEM =
      "https://codesystem.x12.org/external/886";

  // PAS Request/Response Bundle Profile URLs
  public static final String PROFILE_PAS_REQUEST_BUNDLE =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-pas-request-bundle";

  // PAS Resource Profile URLs
  public static final String PROFILE_PAS_CLAIM =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claim";
  public static final String PROFILE_PAS_BENEFICIARY =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-beneficiary";
  public static final String PROFILE_PAS_COVERAGE =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-coverage";
  public static final String PROFILE_PAS_INSURER =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-insurer";
  public static final String PROFILE_PAS_REQUESTOR =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-requestor";

  // Standard FHIR Code Systems
  public static final String CODE_SYSTEM_CLAIM_TYPE =
      "http://terminology.hl7.org/CodeSystem/claim-type";
  public static final String CODE_SYSTEM_PROCESS_PRIORITY =
      "http://terminology.hl7.org/CodeSystem/processpriority";
  public static final String CODE_SYSTEM_ICD10_CM = "http://hl7.org/fhir/sid/icd-10-cm";
  public static final String CODE_SYSTEM_NPI = "http://hl7.org/fhir/sid/us-npi";
  public static final String CODE_SYSTEM_CPT = "http://www.ama-assn.org/go/cpt";

  // Identifier Systems
  public static final String IDENTIFIER_SYSTEM_MBI = "http://hl7.org/fhir/sid/us-mbi";

  // CMS Organization Constants
  public static final String INSURER_RESOURCE_ID = "cms-insurer";
  public static final String CMS_ORG_NAME = "Centers for Medicare & Medicaid Services";
  public static final String CMS_ORG_NPI = "2255500003";

  // MBI Identifier System
  public static final String MBI_IDENTIFIER_SYSTEM = "http://hl7.org/fhir/sid/us-mbi";

  /**
   * Enum for the ClaimResponse Disposition field. Values are Granted, Denied, Partial, Pending,
   * Cancelled, and Unknown.
   */
  public enum Disposition {
    GRANTED("Granted"),
    DENIED("Denied"),
    PARTIAL("Partial"),
    PENDING("Pending"),
    CANCELLED("Cancelled"),
    UNKNOWN("Unknown");

    private final String value;

    Disposition(String value) {
      this.value = value;
    }

    public String value() {
      return this.value;
    }

    /**
     * Returns the Disposition for the given string value, or null if no match.
     *
     * @param value the disposition string
     * @return the matching Disposition or null
     */
    public static Disposition fromString(String value) {
      for (Disposition disposition : Disposition.values()) {
        if (disposition.value().equals(value)) {
          return disposition;
        }
      }
      return null;
    }
  }

  /**
   * Enum for the ClaimResponse.item reviewAction extensions used for X12 HCR01 Response Code. Codes
   * taken from X12 and CMS.
   */
  public enum ReviewAction {
    APPROVED("A1", "Certified in total"),
    PARTIAL("A2", "Certified - partial"),
    DENIED("A3", "Not Certified"),
    PENDED("A4", "Pended"),
    CANCELLED("A6", "Modified"),
    PENDEDFOLLOWUP("86", "Pended for Follow Up");

    private final String code;
    private final String display;

    ReviewAction(String code, String display) {
      this.code = code;
      this.display = display;
    }

    public CodeType valueCode() {
      return new CodeType(this.code);
    }

    public String value() {
      return this.code;
    }

    public String getDisplay() {
      return this.display;
    }

    public String getCodeSystem() {
      return X12_REVIEW_ACTION_CODE_SYSTEM;
    }

    /**
     * Creates a CodeableConcept for this review action.
     *
     * @return a CodeableConcept with the X12 coding
     */
    public CodeableConcept toCodeableConcept() {
      return new CodeableConcept(new Coding(getCodeSystem(), code, display));
    }

    /**
     * Returns the ReviewAction for the given string code, or null if no match.
     *
     * @param value the review action code
     * @return the matching ReviewAction or null
     */
    public static ReviewAction fromString(String value) {
      for (ReviewAction reviewAction : ReviewAction.values()) {
        if (reviewAction.value().equals(value)) {
          return reviewAction;
        }
      }
      return null;
    }
  }

  /**
   * Converts a Disposition to the corresponding ReviewAction.
   *
   * @param disposition the disposition
   * @return the corresponding ReviewAction, or null for UNKNOWN
   */
  public static ReviewAction dispositionToReviewAction(Disposition disposition) {
    if (disposition == null) {
      return null;
    }
    switch (disposition) {
      case GRANTED:
        return ReviewAction.APPROVED;
      case DENIED:
        return ReviewAction.DENIED;
      case PARTIAL:
        return ReviewAction.PARTIAL;
      case PENDING:
        return ReviewAction.PENDED;
      case CANCELLED:
        return ReviewAction.CANCELLED;
      default:
        return null;
    }
  }
}
