package gov.cms.bfd.server.war.r4.providers.pas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.codesystems.ProcessPriority;
import org.springframework.stereotype.Component;

/**
 * Maps an {@link ExplanationOfBenefit} to a PAS-profiled {@link Claim} with {@code use:
 * preauthorization}.
 */
@Component
public class EobToPasClaimMapper {

  /**
   * Transforms an ExplanationOfBenefit into a PAS-profiled Claim.
   *
   * @param eob the source ExplanationOfBenefit
   * @return a PAS-profiled Claim resource
   */
  public Claim map(ExplanationOfBenefit eob) {
    if (eob == null) {
      throw new IllegalArgumentException("ExplanationOfBenefit must not be null");
    }

    Claim claim = new Claim();

    // Set PAS profile
    Meta meta = new Meta();
    meta.addProfile(PasConstants.PAS_CLAIM_PROFILE_URL);
    claim.setMeta(meta);

    // PAS requires use = PREAUTHORIZATION (not CLAIM like PAC)
    claim.setUse(Claim.Use.PREAUTHORIZATION);
    claim.setStatus(Claim.ClaimStatus.ACTIVE);

    // Map type from EOB
    if (eob.hasType()) {
      claim.setType(eob.getType());
    }

    // Map patient reference
    if (eob.hasPatient()) {
      claim.setPatient(eob.getPatient());
    }

    // Map provider reference
    if (eob.hasProvider()) {
      claim.setProvider(eob.getProvider());
    }

    // Set priority to normal
    claim.setPriority(
        new CodeableConcept(
            new Coding(
                ProcessPriority.NORMAL.getSystem(),
                ProcessPriority.NORMAL.toCode(),
                ProcessPriority.NORMAL.getDisplay())));

    // Map diagnosis
    if (eob.hasDiagnosis()) {
      List<Claim.DiagnosisComponent> diagnoses = new ArrayList<>();
      for (ExplanationOfBenefit.DiagnosisComponent eobDiag : eob.getDiagnosis()) {
        Claim.DiagnosisComponent claimDiag = new Claim.DiagnosisComponent();
        claimDiag.setSequence(eobDiag.getSequence());
        claimDiag.setDiagnosis(eobDiag.getDiagnosis());
        if (eobDiag.hasType()) {
          claimDiag.setType(eobDiag.getType());
        }
        diagnoses.add(claimDiag);
      }
      claim.setDiagnosis(diagnoses);
    }

    // Map items with PAS extensions
    if (eob.hasItem()) {
      List<Claim.ItemComponent> items = new ArrayList<>();
      for (ExplanationOfBenefit.ItemComponent eobItem : eob.getItem()) {
        Claim.ItemComponent claimItem = new Claim.ItemComponent();
        claimItem.setSequence(eobItem.getSequence());
        claimItem.setProductOrService(eobItem.getProductOrService());

        // Add PAS extension: itemRequestedServiceDate from EOB service date
        if (eobItem.hasServicedDateType()) {
          claimItem.addExtension(
              new Extension(
                  PasConstants.ITEM_REQUESTED_SERVICE_DATE,
                  new DateTimeType(eobItem.getServicedDateType().getValueAsString())));
        } else if (eobItem.hasServicedPeriod()) {
          claimItem.addExtension(
              new Extension(PasConstants.ITEM_REQUESTED_SERVICE_DATE, eobItem.getServicedPeriod()));
        }

        // Add PAS extension: itemTraceNumber
        Identifier traceNumber = new Identifier();
        traceNumber.setSystem("http://example.org/ITEM_TRACE_NUMBER");
        traceNumber.setValue(UUID.randomUUID().toString());
        claimItem.addExtension(
            new Extension(PasConstants.ITEM_TRACE_NUMBER_EXTENSION_URL, traceNumber));

        items.add(claimItem);
      }
      claim.setItem(items);
    }

    // Map insurance
    if (eob.hasInsurance()) {
      List<Claim.InsuranceComponent> insurances = new ArrayList<>();
      for (ExplanationOfBenefit.InsuranceComponent eobIns : eob.getInsurance()) {
        Claim.InsuranceComponent claimIns = new Claim.InsuranceComponent();
        claimIns.setFocal(eobIns.getFocal());
        claimIns.setCoverage(eobIns.getCoverage());
        insurances.add(claimIns);
      }
      claim.setInsurance(insurances);
    }

    // Map supportingInfo
    if (eob.hasSupportingInfo()) {
      List<Claim.SupportingInformationComponent> supportingInfos = new ArrayList<>();
      for (ExplanationOfBenefit.SupportingInformationComponent eobInfo : eob.getSupportingInfo()) {
        Claim.SupportingInformationComponent claimInfo = new Claim.SupportingInformationComponent();
        claimInfo.setSequence(eobInfo.getSequence());
        claimInfo.setCategory(eobInfo.getCategory());
        if (eobInfo.hasCode()) {
          claimInfo.setCode(eobInfo.getCode());
        }
        supportingInfos.add(claimInfo);
      }
      claim.setSupportingInfo(supportingInfos);
    }

    return claim;
  }
}
