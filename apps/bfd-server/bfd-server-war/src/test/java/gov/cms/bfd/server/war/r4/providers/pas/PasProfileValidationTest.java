package gov.cms.bfd.server.war.r4.providers.pas;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FHIR profile validation tests for PAS resources. Uses HAPI FHIR's {@link FhirValidator} with
 * {@link FhirInstanceValidator} configured with the Da Vinci PAS IG NPM package
 * ({@code hl7.fhir.us.davinci-pas#2.0.1}) to validate generated resources against PAS
 * StructureDefinitions.
 *
 * <p>This is the primary <b>Layer 1</b> audit conformance verification for CMS-0057-F. It
 * validates that mapper output conforms to PAS IG profiles — including extension cardinality,
 * binding strengths, and slicing rules — not just base FHIR R4 structural rules.
 *
 * <p>Reports are written to {@code target/pas-validation-report.txt} and {@code
 * target/pas-validation-report.json}.
 */
class PasProfileValidationTest {

  private static final Logger logger = LoggerFactory.getLogger(PasProfileValidationTest.class);

  private static FhirContext fhirContext;
  private static FhirValidator fhirValidator;
  private static boolean pasIgLoaded = false;
  private static final String REPORT_DIR = "target";
  private static final String TEXT_REPORT_FILE = REPORT_DIR + "/pas-validation-report.txt";
  private static final String JSON_REPORT_FILE = REPORT_DIR + "/pas-validation-report.json";

  @BeforeAll
  static void setUpValidator() {
    fhirContext = FhirContext.forR4();

    // Build the validation support chain with PAS IG NPM package.
    // This is the critical Layer 1 requirement for CMS-0057-F audit conformance:
    // the FhirInstanceValidator must load the PAS IG StructureDefinitions to validate
    // extension cardinality, binding strengths, and slicing rules.
    NpmPackageValidationSupport npmSupport = new NpmPackageValidationSupport(fhirContext);
    try {
      npmSupport.loadPackageFromClasspath(
          "classpath:package/hl7.fhir.us.davinci-pas-2.0.1.tgz");
      pasIgLoaded = true;
      logger.info("Successfully loaded Da Vinci PAS IG NPM package v2.0.1");
    } catch (Exception e) {
      logger.warn(
          "Could not load PAS IG NPM package from classpath. "
              + "Falling back to base R4 validation only. "
              + "To enable full PAS profile validation, add the PAS IG NPM package "
              + "(hl7.fhir.us.davinci-pas-2.0.1.tgz) to src/test/resources/package/. "
              + "Error: {}",
          e.getMessage());
    }

    // The chain order matters: NPM package support first so PAS IG definitions
    // take precedence over base R4 defaults where applicable.
    ValidationSupportChain validationSupportChain =
        new ValidationSupportChain(
            npmSupport,
            new DefaultProfileValidationSupport(fhirContext),
            new InMemoryTerminologyServerValidationSupport(fhirContext),
            new CommonCodeSystemsTerminologyService(fhirContext),
            new SnapshotGeneratingValidationSupport(fhirContext));

    // Wrap in CachingValidationSupport for performance
    CachingValidationSupport cachingSupport =
        new CachingValidationSupport(validationSupportChain);

    FhirInstanceValidator instanceValidator = new FhirInstanceValidator(cachingSupport);
    // Do NOT disable terminology checks — X12 code system bindings must be validated
    // for CMS-0057-F audit conformance. The previous setNoTerminologyChecks(true) call
    // was removed intentionally.

    fhirValidator = fhirContext.newValidator();
    fhirValidator.registerValidatorModule(instanceValidator);

    // Ensure target directory exists and clear previous reports
    new File(REPORT_DIR).mkdirs();
    new File(TEXT_REPORT_FILE).delete();
    new File(JSON_REPORT_FILE).delete();
  }

  @Test
  void testValidateClaimResource() throws IOException {
    EobToPasClaimMapper claimMapper = new EobToPasClaimMapper();
    ExplanationOfBenefit eob = createTestEob();

    Claim claim = claimMapper.map(eob);
    assertNotNull(claim);

    ValidationResult result = fhirValidator.validateWithResult(claim);
    logAndReportValidation("Claim", PasConstants.PAS_CLAIM_PROFILE_URL, result);

    assertNoErrors(result, "Claim");
  }

  @Test
  void testValidateClaimResponseResource() throws IOException {
    ClaimResponseMapper responseMapper = new ClaimResponseMapper();
    Claim claim = createTestClaim();

    ClaimResponse claimResponse =
        responseMapper.buildResponse(
            claim,
            Arrays.asList(PasConstants.ReviewAction.APPROVED, PasConstants.ReviewAction.DENIED));
    assertNotNull(claimResponse);

    ValidationResult result = fhirValidator.validateWithResult(claimResponse);
    logAndReportValidation("ClaimResponse", PasConstants.PAS_CLAIM_RESPONSE_PROFILE_URL, result);

    assertNoErrors(result, "ClaimResponse");
  }

  @Test
  void testValidatePatientResource() throws IOException {
    PatientToPasBeneficiaryMapper patientMapper = new PatientToPasBeneficiaryMapper();
    Patient patient = createTestPatient();

    Patient beneficiary = patientMapper.map(patient);
    assertNotNull(beneficiary);

    ValidationResult result = fhirValidator.validateWithResult(beneficiary);
    logAndReportValidation("Patient", PasConstants.PROFILE_PAS_BENEFICIARY, result);

    assertNoErrors(result, "Patient");
  }

  @Test
  void testValidateCoverageResource() throws IOException {
    CoverageToPasCoverageMapper coverageMapper = new CoverageToPasCoverageMapper();
    Coverage coverage = createTestCoverage();

    Coverage pasCoverage = coverageMapper.map(coverage);
    assertNotNull(pasCoverage);

    ValidationResult result = fhirValidator.validateWithResult(pasCoverage);
    logAndReportValidation("Coverage", PasConstants.PROFILE_PAS_COVERAGE, result);

    assertNoErrors(result, "Coverage");
  }

  @Test
  void testValidateResponseBundle() throws IOException {
    ClaimResponseMapper responseMapper = new ClaimResponseMapper();
    Claim claim = createTestClaim();

    ClaimResponse claimResponse =
        responseMapper.buildResponse(
            claim, Collections.singletonList(PasConstants.ReviewAction.APPROVED));
    Bundle responseBundle = responseMapper.wrapInBundle(claimResponse);
    assertNotNull(responseBundle);

    ValidationResult result = fhirValidator.validateWithResult(responseBundle);
    logAndReportValidation(
        "Bundle (Response)", PasConstants.PAS_RESPONSE_BUNDLE_PROFILE_URL, result);

    assertNoErrors(result, "Bundle (Response)");
  }

  /**
   * Explicitly verifies that the PAS IG NPM package was loaded. If this test fails, the other
   * validation tests are only validating against base FHIR R4 profiles, which is NOT sufficient
   * for CMS-0057-F audit conformance.
   */
  @Test
  void testPasIgPackageLoaded() {
    assertTrue(
        pasIgLoaded,
        "Da Vinci PAS IG NPM package (hl7.fhir.us.davinci-pas-2.0.1.tgz) must be loaded "
            + "for CMS-0057-F audit conformance. Add the package to "
            + "src/test/resources/package/hl7.fhir.us.davinci-pas-2.0.1.tgz");
  }

  /**
   * Asserts that a validation result contains no ERROR-level issues. Warnings and info-level issues
   * are logged but do not cause test failure.
   */
  private void assertNoErrors(ValidationResult result, String resourceType) {
    List<SingleValidationMessage> errors = new ArrayList<>();
    for (SingleValidationMessage msg : result.getMessages()) {
      if (msg.getSeverity() == ResultSeverityEnum.ERROR
          || msg.getSeverity() == ResultSeverityEnum.FATAL) {
        errors.add(msg);
      }
    }

    if (!errors.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append(resourceType)
          .append(" validation produced ")
          .append(errors.size())
          .append(" error(s):\n");
      for (SingleValidationMessage error : errors) {
        sb.append("  [")
            .append(error.getSeverity())
            .append("] ")
            .append(error.getLocationString())
            .append(": ")
            .append(error.getMessage())
            .append("\n");
      }
      assertTrue(errors.isEmpty(), sb.toString());
    }
  }

  private void logAndReportValidation(
      String resourceType, String profileUrl, ValidationResult result) throws IOException {
    boolean passed = result.isSuccessful();
    String status = passed ? "PASS" : "FAIL";

    // Log to text report
    try (PrintWriter writer = new PrintWriter(new FileWriter(TEXT_REPORT_FILE, true))) {
      writer.println("=== " + resourceType + " Validation ===");
      writer.println("Profile: " + profileUrl);
      writer.println("PAS IG Loaded: " + pasIgLoaded);
      writer.println("Status: " + status);
      writer.println("Issues: " + result.getMessages().size());

      result
          .getMessages()
          .forEach(
              msg -> {
                writer.println(
                    "  ["
                        + msg.getSeverity()
                        + "] "
                        + msg.getLocationString()
                        + ": "
                        + msg.getMessage());
                logger.info(
                    "[{}] {} at {}: {}",
                    resourceType,
                    msg.getSeverity(),
                    msg.getLocationString(),
                    msg.getMessage());
              });

      writer.println();
    }

    // Log to JSON report
    ObjectMapper objectMapper = new ObjectMapper();
    List<ObjectNode> existingEntries = new ArrayList<>();

    File jsonFile = new File(JSON_REPORT_FILE);
    if (jsonFile.exists() && jsonFile.length() > 0) {
      try {
        ArrayNode existingArray = (ArrayNode) objectMapper.readTree(jsonFile);
        existingArray.forEach(node -> existingEntries.add((ObjectNode) node));
      } catch (Exception e) {
        // Start fresh if file is malformed
      }
    }

    ObjectNode entry = objectMapper.createObjectNode();
    entry.put("resourceType", resourceType);
    entry.put("profileUrl", profileUrl);
    entry.put("pasIgLoaded", pasIgLoaded);
    entry.put("status", status);

    ArrayNode issuesArray = objectMapper.createArrayNode();
    result
        .getMessages()
        .forEach(
            msg -> {
              ObjectNode issue = objectMapper.createObjectNode();
              issue.put("severity", msg.getSeverity().name());
              issue.put("location", msg.getLocationString());
              issue.put("message", msg.getMessage());
              issuesArray.add(issue);
            });
    entry.set("issues", issuesArray);

    existingEntries.add(entry);
    ArrayNode outputArray = objectMapper.createArrayNode();
    existingEntries.forEach(outputArray::add);
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, outputArray);

    logger.info(
        "{} validation {} (PAS IG loaded: {}): {} issues",
        resourceType,
        status,
        pasIgLoaded,
        result.getMessages().size());
  }

  private ExplanationOfBenefit createTestEob() {
    ExplanationOfBenefit eob = new ExplanationOfBenefit();
    eob.setType(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/claim-type",
                "professional",
                "Professional")));
    eob.setPatient(new Reference("Patient/test-patient"));
    eob.setProvider(new Reference("Practitioner/test-provider"));

    ExplanationOfBenefit.ItemComponent item = new ExplanationOfBenefit.ItemComponent();
    item.setSequence(1);
    item.setProductOrService(
        new CodeableConcept(new Coding("http://www.ama-assn.org/go/cpt", "99213", "Office visit")));
    eob.addItem(item);

    ExplanationOfBenefit.InsuranceComponent ins = new ExplanationOfBenefit.InsuranceComponent();
    ins.setFocal(true);
    ins.setCoverage(new Reference("Coverage/test-coverage"));
    eob.addInsurance(ins);

    return eob;
  }

  private Claim createTestClaim() {
    Claim claim = new Claim();
    claim.setUse(Claim.Use.PREAUTHORIZATION);
    claim.setType(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/claim-type",
                "professional",
                "Professional")));
    claim.setPatient(new Reference("Patient/test-patient"));
    claim.setProvider(new Reference("Practitioner/test-provider"));

    for (int i = 1; i <= 2; i++) {
      Claim.ItemComponent item = new Claim.ItemComponent();
      item.setSequence(i);
      item.setProductOrService(
          new CodeableConcept(
              new Coding("http://www.ama-assn.org/go/cpt", "9921" + i, "Service " + i)));
      claim.addItem(item);
    }

    return claim;
  }

  private Patient createTestPatient() {
    Patient patient = new Patient();
    patient.setId("test-patient");
    patient.addName(new HumanName().setFamily("Doe").addGiven("John"));
    patient.setGender(Enumerations.AdministrativeGender.MALE);
    patient.setBirthDate(new Date());
    patient.addIdentifier(
        new Identifier().setSystem(PasConstants.MBI_IDENTIFIER_SYSTEM).setValue("1234567890A"));
    return patient;
  }

  private Coverage createTestCoverage() {
    Coverage coverage = new Coverage();
    coverage.setId("test-coverage");
    coverage.setStatus(Coverage.CoverageStatus.ACTIVE);
    coverage.setSubscriberId("1234567890A");
    coverage.addPayor(new Reference("Organization/test-org"));
    return coverage;
  }
}
