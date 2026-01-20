package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

/**
 * Converts FHIR MedicationRequest to HL7 RXE (Pharmacy/Treatment Encoded Order)
 * segment.
 */
@Component
public class MedicationRequestToRxeConverter implements FhirToHl7Converter<MedicationRequest> {

    private int rxeIndex = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof MedicationRequest;
    }

    @Override
    public void convert(MedicationRequest medRequest, Message message, Terser terser) throws HL7Exception {
        String rxePath = "/.RXE(" + rxeIndex + ")";

        // RXE-1 Quantity/Timing (deprecated, but set for compatibility)
        terser.set(rxePath + "-1", "1");

        // RXE-2 Give Code (Medication)
        if (medRequest.hasMedicationCodeableConcept()) {
            CodeableConcept medication = medRequest.getMedicationCodeableConcept();
            if (medication.hasCoding()) {
                Coding coding = medication.getCodingFirstRep();
                terser.set(rxePath + "-2-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(rxePath + "-2-2", coding.getDisplay());
                }
                if (coding.hasSystem()) {
                    terser.set(rxePath + "-2-3", coding.getSystem());
                }
            } else if (medication.hasText()) {
                terser.set(rxePath + "-2-2", medication.getText());
            }
        }

        // RXE-3/4/5 Dosage Information
        if (medRequest.hasDosageInstruction()) {
            Dosage dosage = medRequest.getDosageInstructionFirstRep();

            // Dose Quantity
            if (dosage.hasDoseAndRate() && dosage.getDoseAndRateFirstRep().hasDoseQuantity()) {
                Quantity doseQty = dosage.getDoseAndRateFirstRep().getDoseQuantity();
                if (doseQty.hasValue()) {
                    terser.set(rxePath + "-3", doseQty.getValue().toPlainString());
                }
                if (doseQty.hasUnit()) {
                    terser.set(rxePath + "-5-1", doseQty.getUnit());
                }
            }

            // RXE-7 Provider's Administration Instructions
            if (dosage.hasText()) {
                terser.set(rxePath + "-7-2", dosage.getText());
            }

            // RXE-21/22 Give Rate (if present)
            if (dosage.hasDoseAndRate() && dosage.getDoseAndRateFirstRep().hasRateQuantity()) {
                Quantity rateQty = dosage.getDoseAndRateFirstRep().getRateQuantity();
                if (rateQty.hasValue()) {
                    terser.set(rxePath + "-21", rateQty.getValue().toPlainString());
                }
                if (rateQty.hasUnit()) {
                    terser.set(rxePath + "-22-1", rateQty.getUnit());
                }
            }
        }

        // RXE-10/11/12 Dispense Information
        if (medRequest.hasDispenseRequest()) {
            MedicationRequest.MedicationRequestDispenseRequestComponent dispense = medRequest.getDispenseRequest();

            if (dispense.hasQuantity()) {
                Quantity qty = dispense.getQuantity();
                if (qty.hasValue()) {
                    terser.set(rxePath + "-10", qty.getValue().toPlainString());
                }
                if (qty.hasUnit()) {
                    terser.set(rxePath + "-11-1", qty.getUnit());
                }
            }

            if (dispense.hasNumberOfRepeatsAllowed()) {
                terser.set(rxePath + "-12", String.valueOf(dispense.getNumberOfRepeatsAllowed()));
            }
        }

        // RXE-13 Ordering Provider
        if (medRequest.hasRequester()) {
            Reference requester = medRequest.getRequester();
            if (requester.hasReference()) {
                String ref = requester.getReference();
                if (ref.contains("/")) {
                    terser.set(rxePath + "-13-1", ref.substring(ref.lastIndexOf("/") + 1));
                } else {
                    terser.set(rxePath + "-13-1", ref);
                }
            }
            if (requester.hasDisplay()) {
                terser.set(rxePath + "-13-2", requester.getDisplay());
            }
        }

        // RXE-15 Prescription Number (from identifier)
        if (medRequest.hasIdentifier()) {
            for (Identifier id : medRequest.getIdentifier()) {
                if (id.hasValue()) {
                    terser.set(rxePath + "-15", id.getValue());
                    break;
                }
            }
        }

        // RXE-25 Give Strength (from extension or medication coding)
        // RXE-31 Pharmacy Order Type
        if (medRequest.hasIntent()) {
            String intentCode = medRequest.getIntent().toCode();
            if ("order".equals(intentCode)) {
                terser.set(rxePath + "-31", "O");
            } else if ("reflex-order".equals(intentCode)) {
                terser.set(rxePath + "-31", "R");
            }
        }

        // Map Route (RXR segment)
        if (medRequest.hasDosageInstruction()) {
            Dosage dosage = medRequest.getDosageInstructionFirstRep();
            if (dosage.hasRoute()) {
                String rxrPath = "/.RXR(" + rxeIndex + ")";
                CodeableConcept route = dosage.getRoute();
                if (route.hasCoding()) {
                    Coding routeCoding = route.getCodingFirstRep();
                    terser.set(rxrPath + "-1-1", routeCoding.getCode());
                    if (routeCoding.hasDisplay()) {
                        terser.set(rxrPath + "-1-2", routeCoding.getDisplay());
                    }
                }

                // Site
                if (dosage.hasSite()) {
                    CodeableConcept site = dosage.getSite();
                    if (site.hasCoding()) {
                        terser.set(rxrPath + "-2-1", site.getCodingFirstRep().getCode());
                    }
                }
            }
        }

        rxeIndex++;
    }
}
