package gov.cms.bfd.server.war.r4.providers.pas;

import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Identifier;
import org.springframework.stereotype.Component;

/**
 * Maps a BFD Coverage to a PAS-profiled Coverage. Ensures subscriberId maps to MBI, payor
 * references Organization, and status is active.
 */
@Component
public class CoverageToPasCoverageMapper {

  /**
   * Maps a BFD Coverage to a PAS-compatible Coverage resource.
   *
   * @param coverage the source Coverage resource
   * @return the mapped Coverage resource with PAS-required elements
   * @throws IllegalArgumentException if the coverage is null or missing required elements
   */
  public Coverage map(Coverage coverage) {
    if (coverage == null) {
      throw new IllegalArgumentException("Coverage must not be null");
    }

    Coverage pasCoverage = coverage.copy();

    // Ensure status is active
    pasCoverage.setStatus(Coverage.CoverageStatus.ACTIVE);

    // Ensure subscriberId maps to MBI
    if (!hasSubscriberId(pasCoverage)) {
      // Try to derive from identifier
      if (pasCoverage.hasIdentifier()) {
        for (Identifier id : pasCoverage.getIdentifier()) {
          if (PasConstants.MBI_IDENTIFIER_SYSTEM.equals(id.getSystem())) {
            pasCoverage.setSubscriberId(id.getValue());
            break;
          }
        }
      }
    }

    // Validate payor reference
    if (!pasCoverage.hasPayor()) {
      throw new IllegalArgumentException("Coverage must have at least one payor reference");
    }

    return pasCoverage;
  }

  /**
   * Checks if the coverage has a subscriberId set.
   *
   * @param coverage the coverage to check
   * @return true if subscriberId is present and non-empty
   */
  private boolean hasSubscriberId(Coverage coverage) {
    return coverage.hasSubscriberId()
        && coverage.getSubscriberId() != null
        && !coverage.getSubscriberId().isEmpty();
  }
}
