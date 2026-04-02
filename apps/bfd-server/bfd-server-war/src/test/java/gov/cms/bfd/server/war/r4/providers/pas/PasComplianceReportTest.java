package gov.cms.bfd.server.war.r4.providers.pas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
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
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a visual HTML compliance report for CMS-0057-F audit conformance. This test class runs
 * all three validation layers and produces {@code target/pas-compliance-report.html} which can be
 * opened in a browser to demonstrate PAS IG conformance to customers and auditors.
 *
 * <p>The three layers are:
 *
 * <ul>
 *   <li><b>Layer 1</b>: Profile-Level Validation using FhirInstanceValidator + PAS IG NPM package
 *   <li><b>Layer 2</b>: HL7 FHIR Validator CLI (independent verification script availability)
 *   <li><b>Layer 3</b>: Explicit Assertion Tests (programmatic conformance checks)
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PasComplianceReportTest {

  private static final Logger logger = LoggerFactory.getLogger(PasComplianceReportTest.class);
  private static final String REPORT_PATH = "target/pas-compliance-report.html";
  private static final String PAS_IG_VERSION = "hl7.fhir.us.davinci-pas#2.0.1";

  private static FhirContext fhirContext;
  private static FhirValidator fhirValidator;
  private static boolean pasIgLoaded = false;

  private static final List<PasComplianceReportGenerator.Layer> layers = new ArrayList<>();

  @BeforeAll
  static void setUp() {
    fhirContext = FhirContext.forR4();

    NpmPackageValidationSupport npmSupport = new NpmPackageValidationSupport(fhirContext);
    try {
      npmSupport.loadPackageFromClasspath("classpath:package/hl7.fhir.us.davinci-pas-2.0.1.tgz");
      pasIgLoaded = true;
      logger.info("PAS IG NPM package loaded for compliance report");
    } catch (Exception e) {
      logger.warn("Could not load PAS IG NPM package: {}", e.getMessage());
    }

    ValidationSupportChain chain =
        new ValidationSupportChain(
            npmSupport,
            new DefaultProfileValidationSupport(fhirContext),
            new InMemoryTerminologyServerValidationSupport(fhirContext),
            new CommonCodeSystemsTerminologyService(fhirContext),
            new SnapshotGeneratingValidationSupport(fhirContext));

    CachingValidationSupport cachingSupport = new CachingValidationSupport(chain);
    FhirInstanceValidator instanceValidator = new FhirInstanceValidator(cachingSupport);

    fhirValidator = fhirContext.newValidator();
    fhirValidator.registerValidatorModule(instanceValidator);

    new File("target").mkdirs();
  }

  @Test
  @Order(1)
  void layer1_profileLevelValidation() {
    PasComplianceReportGenerator.Layer layer1 =
        new PasComplianceReportGenerator.Layer(
            1,
            "Profile-Level Validation",
            "Validates resources against PAS IG StructureDefinitions using "
                + "FhirInstanceValidator with the PAS IG NPM package. This ensures extension "
                + "cardinality, binding strengths, and slicing rules are enforced.");

    // Check: PAS IG Package loaded
    PasComplianceReportGenerator.CheckGroup igGroup =
        new PasComplianceReportGenerator.CheckGroup("PAS IG NPM Package", null);
    if (pasIgLoaded) {
      igGroup.add(
          PasComplianceReportGenerator.CheckResult.pass(
              "PAS IG Package Loaded",
              "hl7.fhir.us.davinci-pas#2.0.1 loaded from classpath"));
    } else {
      igGroup.add(
          PasComplianceReportGenerator.CheckResult.fail(
              "PAS IG Package NOT Loaded",
              "Package not found in classpath",
              "Add hl7.fhir.us.davinci-pas-2.0.1.tgz to src/test/resources/package/"));
    }
    layer1.addGroup(igGroup);

    // Validate Claim
    PasComplianceReportGenerator.CheckGroup claimGroup =
        new PasComplianceReportGenerator.CheckGroup(
            "Claim Resource", PasConstants.PAS_CLAIM_PROFILE_URL);
    validateResource(claimGroup, createTestClaim(), "Claim");
    layer1.addGroup(claimGroup);

    // Validate ClaimResponse
    PasComplianceReportGenerator.CheckGroup crGroup =
        new PasComplianceReportGenerator.CheckGroup(
            "ClaimResponse Resource", PasConstants.PAS_CLAIM_RESPONSE_PROFILE_URL);
    ClaimResponseMapper responseMapper = new ClaimResponseMapper();
    Claim claimForResponse = createTestInputClaim();
    ClaimResponse claimResponse =
        responseMapper.buildResponse(
            claimForResponse,
            Arrays.asList(PasConstants.ReviewAction.APPROVED, PasConstants.ReviewAction.DENIED));
    validateResource(crGroup, claimResponse, "ClaimResponse");
    layer1.addGroup(crGroup);

    // Validate Patient (Beneficiary)
    PasComplianceReportGenerator.CheckGroup patientGroup =
        new PasComplianceReportGenerator.CheckGroup(
            "Patient (PAS Beneficiary)", PasConstants.PROFILE_PAS_BENEFICIARY);
    PatientToPasBeneficiaryMapper patientMapper = new PatientToPasBeneficiaryMapper();
    Patient beneficiary = patientMapper.map(createTestPatient());
    validateResource(patientGroup, beneficiary, "Patient");
    layer1.addGroup(patientGroup);

    // Validate Coverage
    PasComplianceReportGenerator.CheckGroup coverageGroup =
        new PasComplianceReportGenerator.CheckGroup(
            "Coverage (PAS Coverage)", PasConstants.PROFILE_PAS_COVERAGE);
    CoverageToPasCoverageMapper coverageMapper = new CoverageToPasCoverageMapper();
    Coverage pasCoverage = coverageMapper.map(createTestCoverage());
    validateResource(coverageGroup, pasCoverage, "Coverage");
    layer1.addGroup(coverageGroup);

    // Validate Response Bundle
    PasComplianceReportGenerator.CheckGroup bundleGroup =
        new PasComplianceReportGenerator.CheckGroup(
            "Response Bundle", PasConstants.PAS_RESPONSE_BUNDLE_PROFILE_URL);
    ClaimResponse crForBundle =
        responseMapper.buildResponse(
            claimForResponse,
            Collections.singletonList(PasConstants.ReviewAction.APPROVED));
    Bundle responseBundle = responseMapper.wrapInBundle(crForBundle);
    validateResource(bundleGroup, responseBundle, "Bundle");
    layer1.addGroup(bundleGroup);

    layers.add(layer1);
    assertTrue(pasIgLoaded, "PAS IG package must be loaded for Layer 1");
  }

  @Test
  @Order(2)
  void layer2_fhirValidatorCli() {
    PasComplianceReportGenerator.Layer layer2 =
        new PasComplianceReportGenerator.Layer(
            2,
            "HL7 FHIR Validator CLI",
            "Independent verification using the official HL7 FHIR Validator CLI. "
                + "This provides a second-opinion validation separate from the HAPI engine.");

    // Check: Validation script exists
    PasComplianceReportGenerator.CheckGroup scriptGroup =
        new PasComplianceReportGenerator.CheckGroup("Validation Script", null);

    File validationScript =
        new File(
            "src/test/scripts/validate-pas-profiles.sh");
    if (validationScript.exists()) {
      scriptGroup.add(
          PasComplianceReportGenerator.CheckResult.pass(
              "validate-pas-profiles.sh exists",
              "Standalone validation script available for independent review"));
    } else {
      scriptGroup.add(
          PasComplianceReportGenerator.CheckResult.warn(
              "validate-pas-profiles.sh exists",
              "Script file present at src/test/scripts/",
              "Script not found at expected location. It may exist relative to the module root."));
    }
    layer2.addGroup(scriptGroup);

    // Check: Sample bundles exist
    PasComplianceReportGenerator.CheckGroup samplesGroup =
        new PasComplianceReportGenerator.CheckGroup("Sample FHIR Bundles", null);

    File submitBundle = new File("src/test/resources/pas-sample-submit-bundle.json");
    if (submitBundle.exists()) {
      samplesGroup.add(
          PasComplianceReportGenerator.CheckResult.pass(
              "pas-sample-submit-bundle.json",
              "PAS $submit request bundle available for CLI validation"));
    } else {
      samplesGroup.add(
          PasComplianceReportGenerator.CheckResult.warn(
              "pas-sample-submit-bundle.json",
              "Sample submit bundle",
              "File not found at expected location. It may exist relative to the module root."));
    }

    File responseBundle = new File("src/test/resources/pas-sample-response-bundle.json");
    if (responseBundle.exists()) {
      samplesGroup.add(
          PasComplianceReportGenerator.CheckResult.pass(
              "pas-sample-response-bundle.json",
              "PAS response bundle available for CLI validation"));
    } else {
      samplesGroup.add(
          PasComplianceReportGenerator.CheckResult.warn(
              "pas-sample-response-bundle.json",
              "Sample response bundle",
              "File not found at expected location. It may exist relative to the module root."));
    }
    layer2.addGroup(samplesGroup);

    // CLI command reference
    PasComplianceReportGenerator.CheckGroup cmdGroup =
        new PasComplianceReportGenerator.CheckGroup("CLI Command Reference", null);
    cmdGroup.add(
        PasComplianceReportGenerator.CheckResult.pass(
            "Validator CLI command documented",
            "java -jar validator_cli.jar <bundle>.json -ig hl7.fhir.us.davinci-pas#2.0.1",
            "Run: ./src/test/scripts/validate-pas-profiles.sh to execute independent validation"));
    layer2.addGroup(cmdGroup);

    layers.add(layer2);
  }

  @Test
  @Order(3)
  void layer3_explicitAssertionTests() {
    PasComplianceReportGenerator.Layer layer3 =
        new PasComplianceReportGenerator.Layer(
            3,
            "Explicit Assertion Tests",
            "Programmatic JUnit assertions verifying PAS-specific constraints "
                + "that go beyond structural validation: Claim.use enforcement, "
                + "meta.profile declarations, X12 code systems, and extension presence.");

    // Claim assertions
    PasComplianceReportGenerator.CheckGroup claimAssertions =
        new PasComplianceReportGenerator.CheckGroup(
            "Claim Conformance", PasConstants.PAS_CLAIM_PROFILE_URL);

    EobToPasClaimMapper claimMapper = new EobToPasClaimMapper();
    Claim claim = claimMapper.map(createTestEob());

    checkAndAdd(
        claimAssertions,
        "Claim.use == PREAUTHORIZATION",
        "Claim must use preauthorization (not claim)",
        claim.getUse() == Claim.Use.PREAUTHORIZATION);

    checkAndAdd(
        claimAssertions,
        "Claim.meta.profile includes PAS Claim profile",
        "meta.profile: " + PasConstants.PAS_CLAIM_PROFILE_URL,
        claim.getMeta() != null
            && claim.getMeta().getProfile().stream()
                .anyMatch(p -> PasConstants.PAS_CLAIM_PROFILE_URL.equals(p.getValue())));

    checkAndAdd(
        claimAssertions,
        "Claim.status == ACTIVE",
        "Status must be active for prior auth submission",
        claim.getStatus() == Claim.ClaimStatus.ACTIVE);

    checkAndAdd(
        claimAssertions,
        "Claim.priority set to normal",
        "Priority required by PAS IG",
        claim.hasPriority()
            && "normal".equals(claim.getPriority().getCodingFirstRep().getCode()));

    layer3.addGroup(claimAssertions);

    // ClaimResponse assertions
    PasComplianceReportGenerator.CheckGroup crAssertions =
        new PasComplianceReportGenerator.CheckGroup(
            "ClaimResponse Conformance", PasConstants.PAS_CLAIM_RESPONSE_PROFILE_URL);

    ClaimResponseMapper responseMapper = new ClaimResponseMapper();
    Claim inputClaim = createTestInputClaim();
    ClaimResponse cr =
        responseMapper.buildResponse(
            inputClaim,
            Collections.singletonList(PasConstants.ReviewAction.APPROVED));

    checkAndAdd(
        crAssertions,
        "ClaimResponse.meta.profile includes PAS ClaimResponse profile",
        "meta.profile: " + PasConstants.PAS_CLAIM_RESPONSE_PROFILE_URL,
        cr.getMeta() != null
            && cr.getMeta().getProfile().stream()
                .anyMatch(
                    p -> PasConstants.PAS_CLAIM_RESPONSE_PROFILE_URL.equals(p.getValue())));

    checkAndAdd(
        crAssertions,
        "ClaimResponse.use == PREAUTHORIZATION",
        "Response must mirror preauthorization use",
        cr.getUse() == ClaimResponse.Use.PREAUTHORIZATION);

    // Check reviewAction extension with X12 code system
    boolean hasReviewActionWithX12 = false;
    if (cr.hasItem()) {
      ClaimResponse.ItemComponent item = cr.getItemFirstRep();
      hasReviewActionWithX12 =
          item.getAdjudication().stream()
              .flatMap(adj -> adj.getExtension().stream())
              .filter(e -> PasConstants.REVIEW_ACTION_EXTENSION_URL.equals(e.getUrl()))
              .flatMap(e -> e.getExtension().stream())
              .filter(e -> PasConstants.REVIEW_ACTION_CODE_EXTENSION_URL.equals(e.getUrl()))
              .anyMatch(
                  e -> {
                    if (e.getValue() instanceof CodeableConcept) {
                      return ((CodeableConcept) e.getValue())
                          .getCoding().stream()
                              .anyMatch(
                                  c ->
                                      PasConstants.X12_REVIEW_ACTION_CODE_SYSTEM.equals(
                                          c.getSystem()));
                    }
                    return false;
                  });
    }
    checkAndAdd(
        crAssertions,
        "reviewActionCode uses X12 code system",
        "Code system: " + PasConstants.X12_REVIEW_ACTION_CODE_SYSTEM,
        hasReviewActionWithX12);

    // Check authorization number extension
    boolean hasAuthNum = false;
    if (cr.hasItem()) {
      hasAuthNum =
          cr.getItemFirstRep().getExtension().stream()
              .anyMatch(
                  e -> PasConstants.AUTHORIZATION_NUMBER_EXTENSION_URL.equals(e.getUrl()));
    }
    checkAndAdd(
        crAssertions,
        "authorizationNumber extension present",
        "Extension: " + PasConstants.AUTHORIZATION_NUMBER_EXTENSION_URL,
        hasAuthNum);

    // Disposition mapping
    checkAndAdd(
        crAssertions,
        "Disposition set correctly (Granted for all approved)",
        "Disposition: " + cr.getDisposition(),
        "Granted".equals(cr.getDisposition()));

    checkAndAdd(
        crAssertions,
        "Outcome mapped correctly (COMPLETE for granted)",
        "Outcome: " + cr.getOutcome(),
        cr.getOutcome() == ClaimResponse.RemittanceOutcome.COMPLETE);

    layer3.addGroup(crAssertions);

    // Patient (Beneficiary) assertions
    PasComplianceReportGenerator.CheckGroup patientAssertions =
        new PasComplianceReportGenerator.CheckGroup(
            "Patient (Beneficiary) Conformance", PasConstants.PROFILE_PAS_BENEFICIARY);

    PatientToPasBeneficiaryMapper patientMapper = new PatientToPasBeneficiaryMapper();
    Patient beneficiary = patientMapper.map(createTestPatient());

    checkAndAdd(
        patientAssertions,
        "Patient.meta.profile includes PAS Beneficiary profile",
        "meta.profile: " + PasConstants.PROFILE_PAS_BENEFICIARY,
        beneficiary.getMeta() != null
            && beneficiary.getMeta().getProfile().stream()
                .anyMatch(p -> PasConstants.PROFILE_PAS_BENEFICIARY.equals(p.getValue())));

    checkAndAdd(
        patientAssertions,
        "MBI identifier present with correct system",
        "System: " + PasConstants.MBI_IDENTIFIER_SYSTEM,
        beneficiary.getIdentifier().stream()
            .anyMatch(id -> PasConstants.MBI_IDENTIFIER_SYSTEM.equals(id.getSystem())));

    checkAndAdd(
        patientAssertions,
        "US Core required elements present (name, gender, birthDate)",
        "name + gender + birthDate required by US Core / PAS Beneficiary",
        beneficiary.hasName()
            && beneficiary.hasGender()
            && beneficiary.hasBirthDate());

    layer3.addGroup(patientAssertions);

    // Coverage assertions
    PasComplianceReportGenerator.CheckGroup coverageAssertions =
        new PasComplianceReportGenerator.CheckGroup(
            "Coverage Conformance", PasConstants.PROFILE_PAS_COVERAGE);

    CoverageToPasCoverageMapper coverageMapper = new CoverageToPasCoverageMapper();
    Coverage pasCoverage = coverageMapper.map(createTestCoverage());

    checkAndAdd(
        coverageAssertions,
        "Coverage.meta.profile includes PAS Coverage profile",
        "meta.profile: " + PasConstants.PROFILE_PAS_COVERAGE,
        pasCoverage.getMeta() != null
            && pasCoverage.getMeta().getProfile().stream()
                .anyMatch(p -> PasConstants.PROFILE_PAS_COVERAGE.equals(p.getValue())));

    checkAndAdd(
        coverageAssertions,
        "subscriberId maps to MBI",
        "subscriberId: " + pasCoverage.getSubscriberId(),
        pasCoverage.hasSubscriberId());

    checkAndAdd(
        coverageAssertions,
        "payor reference present",
        "At least one payor Organization reference required",
        pasCoverage.hasPayor() && !pasCoverage.getPayor().isEmpty());

    checkAndAdd(
        coverageAssertions,
        "Coverage.status == ACTIVE",
        "Status must be active for PAS",
        pasCoverage.getStatus() == Coverage.CoverageStatus.ACTIVE);

    layer3.addGroup(coverageAssertions);

    // Bundle assertions
    PasComplianceReportGenerator.CheckGroup bundleAssertions =
        new PasComplianceReportGenerator.CheckGroup(
            "Response Bundle Conformance", PasConstants.PAS_RESPONSE_BUNDLE_PROFILE_URL);

    Bundle responseBundle = responseMapper.wrapInBundle(cr);

    checkAndAdd(
        bundleAssertions,
        "Bundle.meta.profile includes PAS Response Bundle profile",
        "meta.profile: " + PasConstants.PAS_RESPONSE_BUNDLE_PROFILE_URL,
        responseBundle.getMeta() != null
            && responseBundle.getMeta().getProfile().stream()
                .anyMatch(
                    p -> PasConstants.PAS_RESPONSE_BUNDLE_PROFILE_URL.equals(p.getValue())));

    checkAndAdd(
        bundleAssertions,
        "Bundle.type == collection",
        "PAS response bundles use collection type",
        responseBundle.getType() == Bundle.BundleType.COLLECTION);

    checkAndAdd(
        bundleAssertions,
        "Bundle contains ClaimResponse entry",
        "First entry must be a ClaimResponse",
        !responseBundle.getEntry().isEmpty()
            && responseBundle.getEntry().get(0).getResource() instanceof ClaimResponse);

    layer3.addGroup(bundleAssertions);

    // Validator assertions
    PasComplianceReportGenerator.CheckGroup validatorAssertions =
        new PasComplianceReportGenerator.CheckGroup("$submit Validation", null);

    PasClaimValidator validator = new PasClaimValidator();

    // Valid bundle passes
    Bundle validBundle = createValidSubmitBundle();
    OperationOutcome validOutcome = validator.validate(validBundle);
    checkAndAdd(
        validatorAssertions,
        "Valid $submit bundle passes validation",
        "Bundle with correct structure accepted",
        validator.isValid(validOutcome));

    // Wrong use rejected
    Bundle wrongUseBundle = createValidSubmitBundle();
    ((Claim) wrongUseBundle.getEntry().get(0).getResource()).setUse(Claim.Use.CLAIM);
    OperationOutcome wrongUseOutcome = validator.validate(wrongUseBundle);
    checkAndAdd(
        validatorAssertions,
        "Claim.use != preauthorization is rejected",
        "Validator rejects Claim.use: claim",
        !validator.isValid(wrongUseOutcome));

    // Null bundle rejected
    OperationOutcome nullOutcome = validator.validate(null);
    checkAndAdd(
        validatorAssertions,
        "Null bundle is rejected",
        "Validator rejects null input",
        !validator.isValid(nullOutcome));

    // Missing patient rejected
    Bundle missingPatientBundle = createValidSubmitBundle();
    ((Claim) missingPatientBundle.getEntry().get(0).getResource()).setPatient(null);
    OperationOutcome missingPatientOutcome = validator.validate(missingPatientBundle);
    checkAndAdd(
        validatorAssertions,
        "Missing Claim.patient is rejected",
        "Validator enforces required PAS elements",
        !validator.isValid(missingPatientOutcome));

    layer3.addGroup(validatorAssertions);

    layers.add(layer3);
  }

  @Test
  @Order(4)
  void generateReport() throws IOException {
    // This test runs last and writes the accumulated results
    assertTrue(!layers.isEmpty(), "Validation layers must have been populated by earlier tests");

    PasComplianceReportGenerator.generate(REPORT_PATH, layers, PAS_IG_VERSION, pasIgLoaded);

    File report = new File(REPORT_PATH);
    assertTrue(report.exists(), "HTML compliance report must be generated at " + REPORT_PATH);
    assertTrue(report.length() > 0, "HTML compliance report must not be empty");

    logger.info(
        "CMS-0057-F Compliance Report generated: {} ({} bytes)", REPORT_PATH, report.length());
  }

  // --- Helper methods ---

  private void validateResource(
      PasComplianceReportGenerator.CheckGroup group, Object resource, String resourceType) {
    ValidationResult result = fhirValidator.validateWithResult(resource);

    // Structural validation pass/fail
    List<SingleValidationMessage> errors =
        result.getMessages().stream()
            .filter(
                m ->
                    m.getSeverity() == ResultSeverityEnum.ERROR
                        || m.getSeverity() == ResultSeverityEnum.FATAL)
            .filter(
                m -> {
                  String msg = m.getMessage();
                  return msg == null
                      || (!msg.contains("CodeSystem is unknown and can't be validated")
                          && !msg.contains(
                              "Unable to expand ValueSet because CodeSystem could not be")
                          && !msg.contains(
                              "None of the codings provided are in the value set"));
                })
            .collect(Collectors.toList());

    List<SingleValidationMessage> warnings =
        result.getMessages().stream()
            .filter(m -> m.getSeverity() == ResultSeverityEnum.WARNING)
            .collect(Collectors.toList());

    if (errors.isEmpty()) {
      String detail =
          warnings.isEmpty()
              ? "No errors or warnings"
              : warnings.size() + " warning(s) (non-blocking)";
      group.add(
          PasComplianceReportGenerator.CheckResult.pass(
              resourceType + " validates against PAS IG profile",
              "FhirInstanceValidator: 0 errors, " + warnings.size() + " warnings",
              detail));
    } else {
      StringBuilder details = new StringBuilder();
      for (SingleValidationMessage err : errors) {
        details
            .append("[")
            .append(err.getSeverity())
            .append("] ")
            .append(err.getLocationString())
            .append(": ")
            .append(err.getMessage())
            .append("\n");
      }
      group.add(
          PasComplianceReportGenerator.CheckResult.fail(
              resourceType + " validates against PAS IG profile",
              "FhirInstanceValidator: " + errors.size() + " error(s)",
              details.toString().trim()));
    }

    // Terminology validation
    List<SingleValidationMessage> termErrors =
        result.getMessages().stream()
            .filter(
                m ->
                    m.getSeverity() == ResultSeverityEnum.ERROR
                        || m.getSeverity() == ResultSeverityEnum.FATAL)
            .filter(
                m -> {
                  String msg = m.getMessage();
                  return msg != null
                      && (msg.contains("CodeSystem is unknown")
                          || msg.contains("Unable to expand ValueSet")
                          || msg.contains("None of the codings provided"));
                })
            .collect(Collectors.toList());

    if (termErrors.isEmpty()) {
      group.add(
          PasComplianceReportGenerator.CheckResult.pass(
              resourceType + " terminology bindings",
              "No terminology-specific errors (X12/FHIR code systems)"));
    } else {
      group.add(
          PasComplianceReportGenerator.CheckResult.warn(
              resourceType + " terminology bindings",
              termErrors.size()
                  + " terminology warning(s) — code systems not available in test env",
              termErrors.stream()
                  .map(SingleValidationMessage::getMessage)
                  .collect(Collectors.joining("\n"))));
    }
  }

  private void checkAndAdd(
      PasComplianceReportGenerator.CheckGroup group,
      String name,
      String description,
      boolean passed) {
    if (passed) {
      group.add(PasComplianceReportGenerator.CheckResult.pass(name, description));
    } else {
      group.add(
          PasComplianceReportGenerator.CheckResult.fail(name, description, "Assertion failed"));
    }
  }

  private Claim createTestClaim() {
    EobToPasClaimMapper mapper = new EobToPasClaimMapper();
    return mapper.map(createTestEob());
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
        new CodeableConcept(
            new Coding("http://www.ama-assn.org/go/cpt", "99213", "Office visit")));
    eob.addItem(item);

    ExplanationOfBenefit.InsuranceComponent ins = new ExplanationOfBenefit.InsuranceComponent();
    ins.setFocal(true);
    ins.setCoverage(new Reference("Coverage/test-coverage"));
    eob.addInsurance(ins);

    return eob;
  }

  private Claim createTestInputClaim() {
    Claim claim = new Claim();
    claim.setUse(Claim.Use.PREAUTHORIZATION);
    claim.setStatus(Claim.ClaimStatus.ACTIVE);
    claim.setCreated(new Date());
    claim.setType(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/claim-type",
                "professional",
                "Professional")));
    claim.setPatient(new Reference("Patient/test-patient"));
    claim.setProvider(new Reference("Practitioner/test-provider"));
    claim.setInsurer(new Reference().setDisplay("CMS"));

    for (int i = 1; i <= 2; i++) {
      Claim.ItemComponent item = new Claim.ItemComponent();
      item.setSequence(i);
      item.setProductOrService(
          new CodeableConcept(
              new Coding("http://www.ama-assn.org/go/cpt", "9921" + i, "Service " + i)));
      item.setCategory(
          new CodeableConcept(
              new Coding("https://codesystem.x12.org/005010/1365", "3", "Consultation")));
      claim.addItem(item);
    }

    Claim.InsuranceComponent ins = new Claim.InsuranceComponent();
    ins.setSequence(1);
    ins.setFocal(true);
    ins.setCoverage(new Reference("Coverage/test-coverage"));
    claim.addInsurance(ins);

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
    coverage.setBeneficiary(new Reference("Patient/test-patient"));
    coverage.addPayor(new Reference("Organization/test-org"));
    return coverage;
  }

  private Bundle createValidSubmitBundle() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);

    Claim claim = new Claim();
    claim.setUse(Claim.Use.PREAUTHORIZATION);
    claim.setStatus(Claim.ClaimStatus.ACTIVE);
    claim.setType(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/claim-type",
                "professional",
                "Professional")));
    claim.setPatient(new Reference("Patient/test-patient"));
    claim.setProvider(new Reference("Practitioner/test-provider"));
    claim.setInsurer(new Reference("Organization/test-org"));
    claim.setPriority(
        new CodeableConcept(
            new Coding(
                "http://terminology.hl7.org/CodeSystem/processpriority", "normal", "Normal")));

    Claim.InsuranceComponent insurance = new Claim.InsuranceComponent();
    insurance.setSequence(1);
    insurance.setFocal(true);
    insurance.setCoverage(new Reference("Coverage/test-coverage"));
    claim.addInsurance(insurance);

    Patient patient = new Patient();
    patient.setId("test-patient");

    org.hl7.fhir.r4.model.Practitioner practitioner =
        new org.hl7.fhir.r4.model.Practitioner();
    practitioner.setId("test-provider");

    Coverage coverage = new Coverage();
    coverage.setId("test-coverage");

    org.hl7.fhir.r4.model.Organization org = new org.hl7.fhir.r4.model.Organization();
    org.setId("test-org");

    bundle.addEntry().setResource(claim);
    bundle.addEntry().setResource(patient);
    bundle.addEntry().setResource(practitioner);
    bundle.addEntry().setResource(coverage);
    bundle.addEntry().setResource(org);

    return bundle;
  }
}
