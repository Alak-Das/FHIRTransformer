package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Converts FHIR MedicationAdministration to HL7 RXA (Pharmacy/Treatment
 * Administration) segment.
 */
@Component
public class MedicationAdministrationToRxaConverter implements FhirToHl7Converter<MedicationAdministration> {

    private int rxaIndex = 0;
    private final SimpleDateFormat hl7DateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof MedicationAdministration;
    }

    @Override
    public void convert(MedicationAdministration medAdmin, Message message, Terser terser) throws HL7Exception {
        String rxaPath = "/.RXA(" + rxaIndex + ")";

        // RXA-1 Give Sub-ID Counter
        terser.set(rxaPath + "-1", String.valueOf(rxaIndex));

        // RXA-2 Administration Sub-ID Counter
        terser.set(rxaPath + "-2", "1");

        // RXA-3 Date/Time Start of Administration
        if (medAdmin.hasEffectiveDateTimeType()) {
            terser.set(rxaPath + "-3", hl7DateTimeFormat.format(medAdmin.getEffectiveDateTimeType().getValue()));
        } else if (medAdmin.hasEffectivePeriod()) {
            Period period = medAdmin.getEffectivePeriod();
            if (period.hasStart()) {
                terser.set(rxaPath + "-3", hl7DateTimeFormat.format(period.getStart()));
            }
            // RXA-4 Date/Time End of Administration
            if (period.hasEnd()) {
                terser.set(rxaPath + "-4", hl7DateTimeFormat.format(period.getEnd()));
            }
        }

        // RXA-5 Administered Code
        if (medAdmin.hasMedicationCodeableConcept()) {
            CodeableConcept med = medAdmin.getMedicationCodeableConcept();
            if (med.hasCoding()) {
                Coding coding = med.getCodingFirstRep();
                terser.set(rxaPath + "-5-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(rxaPath + "-5-2", coding.getDisplay());
                }
                if (coding.hasSystem()) {
                    terser.set(rxaPath + "-5-3", coding.getSystem());
                }
            } else if (med.hasText()) {
                terser.set(rxaPath + "-5-2", med.getText());
            }
        } else if (medAdmin.hasMedicationReference()) {
            Reference medRef = medAdmin.getMedicationReference();
            if (medRef.hasDisplay()) {
                terser.set(rxaPath + "-5-2", medRef.getDisplay());
            }
            if (medRef.hasReference()) {
                String ref = medRef.getReference();
                if (ref.contains("/")) {
                    terser.set(rxaPath + "-5-1", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
        }

        // RXA-6 Administered Amount
        // RXA-7 Administered Units
        if (medAdmin.hasDosage()) {
            MedicationAdministration.MedicationAdministrationDosageComponent dosage = medAdmin.getDosage();
            if (dosage.hasDose()) {
                Quantity dose = dosage.getDose();
                if (dose.hasValue()) {
                    terser.set(rxaPath + "-6", dose.getValue().toPlainString());
                }
                if (dose.hasUnit()) {
                    terser.set(rxaPath + "-7-1", dose.getUnit());
                }
                if (dose.hasCode()) {
                    terser.set(rxaPath + "-7-1", dose.getCode());
                }
            }

            // RXA-8 Administered Dosage Form (not directly in FHIR)

            // RXA-9 Administration Notes
            if (dosage.hasText()) {
                terser.set(rxaPath + "-9-1", dosage.getText());
            }

            // RXA-11 Administered-at Location (from site)
            if (dosage.hasSite()) {
                CodeableConcept site = dosage.getSite();
                if (site.hasCoding()) {
                    terser.set(rxaPath + "-11-1", site.getCodingFirstRep().getCode());
                }
            }

            // RXA-12 Administered Per (Time Unit) - from rate
            if (dosage.hasRateQuantity()) {
                Quantity rate = dosage.getRateQuantity();
                if (rate.hasUnit()) {
                    terser.set(rxaPath + "-12", rate.getUnit());
                }
            }
        }

        // RXA-10 Administering Provider
        if (medAdmin.hasPerformer()) {
            for (MedicationAdministration.MedicationAdministrationPerformerComponent performer : medAdmin
                    .getPerformer()) {
                if (performer.hasActor()) {
                    Reference actor = performer.getActor();
                    if (actor.hasDisplay()) {
                        terser.set(rxaPath + "-10-2", actor.getDisplay());
                    }
                    if (actor.hasReference()) {
                        String ref = actor.getReference();
                        if (ref.contains("/")) {
                            terser.set(rxaPath + "-10-1", ref.substring(ref.lastIndexOf("/") + 1));
                        }
                    }
                }
                break; // Only use first performer
            }
        }

        // RXA-15 Substance Lot Number
        // RXA-16 Substance Expiration Date
        // RXA-17 Substance Manufacturer Name
        // (These would come from contained Medication resource if available)

        // RXA-18 Substance/Treatment Refusal Reason
        if (medAdmin.hasStatusReason()) {
            for (CodeableConcept reason : medAdmin.getStatusReason()) {
                if (reason.hasCoding()) {
                    terser.set(rxaPath + "-18-1", reason.getCodingFirstRep().getCode());
                    if (reason.getCodingFirstRep().hasDisplay()) {
                        terser.set(rxaPath + "-18-2", reason.getCodingFirstRep().getDisplay());
                    }
                }
                break;
            }
        }

        // RXA-20 Completion Status
        if (medAdmin.hasStatus()) {
            String status = medAdmin.getStatus().toCode();
            switch (status) {
                case "completed":
                    terser.set(rxaPath + "-20", "CP"); // Complete
                    break;
                case "in-progress":
                    terser.set(rxaPath + "-20", "IP"); // In Progress
                    break;
                case "not-done":
                    terser.set(rxaPath + "-20", "NA"); // Not Administered
                    break;
                case "on-hold":
                    terser.set(rxaPath + "-20", "PA"); // Partially Administered
                    break;
                case "stopped":
                    terser.set(rxaPath + "-20", "CP"); // Complete (stopped is essentially done)
                    break;
                default:
                    terser.set(rxaPath + "-20", "RE"); // Refused
            }
        }

        // RXA-21 Action Code - NDC (from identifiers)
        if (medAdmin.hasIdentifier()) {
            for (Identifier id : medAdmin.getIdentifier()) {
                if (id.hasSystem() && id.getSystem().contains("ndc")) {
                    terser.set(rxaPath + "-21", id.getValue());
                    break;
                }
            }
        }

        rxaIndex++;
    }
}
