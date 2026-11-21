package gov.cms.bfd.server.ng.claim.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for the code-to-enum lookups used when mapping claim rows. */
class CodeLookupTest {

  @Test
  void claimTypeCodeFromCodeReturnsEveryConstant() {
    for (var value : ClaimTypeCode.values()) {
      assertSame(value, ClaimTypeCode.fromCode(value.getCode()));
    }
    assertNull(ClaimTypeCode.fromCode(-1));
  }

  @Test
  void providerSpecialtyCodeFromCodeReturnsEveryConstant() {
    for (var value : ProviderSpecialtyCode.values()) {
      assertEquals(Optional.of(value), ProviderSpecialtyCode.fromCodeOptional(value.getCode()));
    }
    assertTrue(ProviderSpecialtyCode.fromCodeOptional("not-a-code").isEmpty());
    assertTrue(ProviderSpecialtyCode.fromCodeOptional(null).isEmpty());
  }

  @Test
  void providerIdQualifierCodeFromCodeReturnsEveryConstant() {
    for (var value : ProviderIdQualifierCode.values()) {
      assertEquals(Optional.of(value), ProviderIdQualifierCode.fromCodeOptional(value.getCode()));
    }
    assertTrue(ProviderIdQualifierCode.fromCodeOptional("not-a-code").isEmpty());
    assertTrue(ProviderIdQualifierCode.fromCodeOptional(null).isEmpty());
  }
}
