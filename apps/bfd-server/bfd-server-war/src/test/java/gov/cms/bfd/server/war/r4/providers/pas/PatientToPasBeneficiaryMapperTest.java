package gov.cms.bfd.server.war.r4.providers.pas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link PatientToPasBeneficiaryMapper}. */
class PatientToPasBeneficiaryMapperTest {

  private PatientToPasBeneficiaryMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new PatientToPasBeneficiaryMapper();
  }

  @Test
  void testMapPreservesMbiIdentifier() {
    Patient patient = createValidPatient();

    Patient beneficiary = mapper.map(patient);

    assertNotNull(beneficiary);
    assertTrue(
        beneficiary.getIdentifier().stream()
            .anyMatch(id -> PasConstants.MBI_IDENTIFIER_SYSTEM.equals(id.getSystem())));
  }

  @Test
  void testMapPreservesName() {
    Patient patient = createValidPatient();

    Patient beneficiary = mapper.map(patient);

    assertTrue(beneficiary.hasName());
    assertEquals("Doe", beneficiary.getNameFirstRep().getFamily());
  }

  @Test
  void testMapPreservesGender() {
    Patient patient = createValidPatient();

    Patient beneficiary = mapper.map(patient);

    assertEquals(Enumerations.AdministrativeGender.MALE, beneficiary.getGender());
  }

  @Test
  void testMapPreservesBirthDate() {
    Patient patient = createValidPatient();

    Patient beneficiary = mapper.map(patient);

    assertNotNull(beneficiary.getBirthDate());
  }

  @Test
  void testMapNullPatientThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> mapper.map(null));
  }

  @Test
  void testMapPatientWithoutMbiThrowsException() {
    Patient patient = new Patient();
    patient.addName(new HumanName().setFamily("Doe").addGiven("John"));
    patient.setGender(Enumerations.AdministrativeGender.MALE);
    patient.setBirthDate(new Date());
    // No MBI identifier
    patient.addIdentifier(new Identifier().setSystem("http://example.org/other").setValue("12345"));

    assertThrows(IllegalArgumentException.class, () -> mapper.map(patient));
  }

  @Test
  void testMapPatientWithoutNameThrowsException() {
    Patient patient = new Patient();
    patient.setGender(Enumerations.AdministrativeGender.MALE);
    patient.setBirthDate(new Date());
    patient.addIdentifier(
        new Identifier().setSystem(PasConstants.MBI_IDENTIFIER_SYSTEM).setValue("1234567890A"));

    assertThrows(IllegalArgumentException.class, () -> mapper.map(patient));
  }

  @Test
  void testMapPatientWithoutGenderThrowsException() {
    Patient patient = new Patient();
    patient.addName(new HumanName().setFamily("Doe").addGiven("John"));
    patient.setBirthDate(new Date());
    patient.addIdentifier(
        new Identifier().setSystem(PasConstants.MBI_IDENTIFIER_SYSTEM).setValue("1234567890A"));

    assertThrows(IllegalArgumentException.class, () -> mapper.map(patient));
  }

  @Test
  void testMapPatientWithoutBirthDateThrowsException() {
    Patient patient = new Patient();
    patient.addName(new HumanName().setFamily("Doe").addGiven("John"));
    patient.setGender(Enumerations.AdministrativeGender.MALE);
    patient.addIdentifier(
        new Identifier().setSystem(PasConstants.MBI_IDENTIFIER_SYSTEM).setValue("1234567890A"));

    assertThrows(IllegalArgumentException.class, () -> mapper.map(patient));
  }

  @Test
  void testMapSetsMetaProfileToPasBeneficiary() {
    Patient patient = createValidPatient();

    Patient beneficiary = mapper.map(patient);

    assertNotNull(beneficiary.getMeta(), "Patient.meta must be present");
    assertTrue(
        beneficiary.getMeta().getProfile().stream()
            .anyMatch(p -> PasConstants.PROFILE_PAS_BENEFICIARY.equals(p.getValue())),
        "Patient.meta.profile must include PAS Beneficiary profile URL");
  }

  @Test
  void testMapReturnsCopy() {
    Patient patient = createValidPatient();

    Patient beneficiary = mapper.map(patient);

    // Verify it's a copy, not the same object
    assertNotNull(beneficiary);
    assertTrue(beneficiary != patient);
  }

  private Patient createValidPatient() {
    Patient patient = new Patient();
    patient.setId("test-patient");
    patient.addName(new HumanName().setFamily("Doe").addGiven("John"));
    patient.setGender(Enumerations.AdministrativeGender.MALE);
    patient.setBirthDate(new Date());
    patient.addIdentifier(
        new Identifier().setSystem(PasConstants.MBI_IDENTIFIER_SYSTEM).setValue("1234567890A"));
    return patient;
  }
}
