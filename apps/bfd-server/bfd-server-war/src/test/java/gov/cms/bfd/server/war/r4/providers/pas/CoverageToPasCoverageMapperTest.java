package gov.cms.bfd.server.war.r4.providers.pas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link CoverageToPasCoverageMapper}. */
class CoverageToPasCoverageMapperTest {

  private CoverageToPasCoverageMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new CoverageToPasCoverageMapper();
  }

  @Test
  void testMapPreservesSubscriberId() {
    Coverage coverage = createValidCoverage();
    coverage.setSubscriberId("1234567890A");

    Coverage pasCoverage = mapper.map(coverage);

    assertNotNull(pasCoverage);
    assertEquals("1234567890A", pasCoverage.getSubscriberId());
  }

  @Test
  void testMapDerivesSubscriberIdFromMbiIdentifier() {
    Coverage coverage = createValidCoverage();
    // No subscriberId set, but has MBI identifier
    coverage.addIdentifier(
        new Identifier().setSystem(PasConstants.MBI_IDENTIFIER_SYSTEM).setValue("9876543210B"));

    Coverage pasCoverage = mapper.map(coverage);

    assertNotNull(pasCoverage);
    assertEquals("9876543210B", pasCoverage.getSubscriberId());
  }

  @Test
  void testMapSetsStatusToActive() {
    Coverage coverage = createValidCoverage();
    coverage.setStatus(Coverage.CoverageStatus.CANCELLED);

    Coverage pasCoverage = mapper.map(coverage);

    assertEquals(Coverage.CoverageStatus.ACTIVE, pasCoverage.getStatus());
  }

  @Test
  void testMapPreservesPayorReference() {
    Coverage coverage = createValidCoverage();

    Coverage pasCoverage = mapper.map(coverage);

    assertNotNull(pasCoverage.getPayor());
    assertEquals(1, pasCoverage.getPayor().size());
    assertEquals("Organization/test-org", pasCoverage.getPayor().get(0).getReference());
  }

  @Test
  void testMapNullCoverageThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> mapper.map(null));
  }

  @Test
  void testMapCoverageWithoutPayorThrowsException() {
    Coverage coverage = new Coverage();
    coverage.setStatus(Coverage.CoverageStatus.ACTIVE);
    coverage.setSubscriberId("1234567890A");
    // No payor

    assertThrows(IllegalArgumentException.class, () -> mapper.map(coverage));
  }

  @Test
  void testMapReturnsCopy() {
    Coverage coverage = createValidCoverage();
    coverage.setSubscriberId("1234567890A");

    Coverage pasCoverage = mapper.map(coverage);

    assertNotNull(pasCoverage);
    // Verify it's a copy
    coverage.setSubscriberId("changed");
    assertEquals("1234567890A", pasCoverage.getSubscriberId());
  }

  private Coverage createValidCoverage() {
    Coverage coverage = new Coverage();
    coverage.setId("test-coverage");
    coverage.setStatus(Coverage.CoverageStatus.ACTIVE);
    coverage.setSubscriberId("1234567890A");
    coverage.addPayor(new Reference("Organization/test-org"));
    return coverage;
  }
}
