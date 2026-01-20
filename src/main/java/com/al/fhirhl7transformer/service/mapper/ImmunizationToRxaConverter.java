package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Converts FHIR Immunization to HL7 RXA (Pharmacy/Treatment Administration)
 * segment.
 */
@Component
public class ImmunizationToRxaConverter implements FhirToHl7Converter<Immunization> {

    private static final SimpleDateFormat HL7_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");
    private int rxaIndex = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Immunization;
    }

    @Override
    public void convert(Immunization immunization, Message message, Terser terser) throws HL7Exception {
        String rxaPath = "/.RXA(" + rxaIndex + ")";

        // RXA-1 Give Sub-ID Counter
        terser.set(rxaPath + "-1", "0");

        // RXA-2 Administration Sub-ID Counter
        terser.set(rxaPath + "-2", String.valueOf(rxaIndex + 1));

        // RXA-3 Date/Time Start of Administration
        if (immunization.hasOccurrenceDateTimeType()) {
            terser.set(rxaPath + "-3", HL7_DATE_FORMAT.format(immunization.getOccurrenceDateTimeType().getValue()));
        }

        // RXA-4 Date/Time End of Administration (same as start for vaccines)
        if (immunization.hasOccurrenceDateTimeType()) {
            terser.set(rxaPath + "-4", HL7_DATE_FORMAT.format(immunization.getOccurrenceDateTimeType().getValue()));
        }

        // RXA-5 Administered Code (Vaccine)
        if (immunization.hasVaccineCode()) {
            CodeableConcept vaccineCode = immunization.getVaccineCode();
            if (vaccineCode.hasCoding()) {
                Coding coding = vaccineCode.getCodingFirstRep();
                terser.set(rxaPath + "-5-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(rxaPath + "-5-2", coding.getDisplay());
                }
                if (coding.hasSystem()) {
                    // Map to HL7 coding system
                    String system = coding.getSystem();
                    if (system.contains("cvx")) {
                        terser.set(rxaPath + "-5-3", "CVX");
                    } else {
                        terser.set(rxaPath + "-5-3", system);
                    }
                }
            }
        }

        // RXA-6 Administered Amount
        if (immunization.hasDoseQuantity()) {
            Quantity dose = immunization.getDoseQuantity();
            if (dose.hasValue()) {
                terser.set(rxaPath + "-6", dose.getValue().toPlainString());
            }
            // RXA-7 Administered Units
            if (dose.hasUnit()) {
                terser.set(rxaPath + "-7-1", dose.getUnit());
            }
        } else {
            // Default to 1 dose for vaccines
            terser.set(rxaPath + "-6", "1");
        }

        // RXA-9 Administration Notes
        if (immunization.hasNote()) {
            StringBuilder notes = new StringBuilder();
            for (Annotation note : immunization.getNote()) {
                if (note.hasText()) {
                    if (notes.length() > 0)
                        notes.append("; ");
                    notes.append(note.getText());
                }
            }
            if (notes.length() > 0) {
                terser.set(rxaPath + "-9-2", notes.toString());
            }
        }

        // RXA-10 Administering Provider
        if (immunization.hasPerformer()) {
            for (Immunization.ImmunizationPerformerComponent performer : immunization.getPerformer()) {
                if (performer.hasActor()) {
                    Reference actor = performer.getActor();
                    if (actor.hasReference()) {
                        String ref = actor.getReference();
                        if (ref.contains("/")) {
                            terser.set(rxaPath + "-10-1", ref.substring(ref.lastIndexOf("/") + 1));
                        }
                    }
                    if (actor.hasDisplay()) {
                        terser.set(rxaPath + "-10-2", actor.getDisplay());
                    }
                    break; // Only use first performer
                }
            }
        }

        // RXA-11 Administered-at Location
        if (immunization.hasLocation()) {
            Reference location = immunization.getLocation();
            if (location.hasReference()) {
                String ref = location.getReference();
                if (ref.contains("/")) {
                    terser.set(rxaPath + "-11-4-1", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
            if (location.hasDisplay()) {
                terser.set(rxaPath + "-11-4-2", location.getDisplay());
            }
        }

        // RXA-15 Substance Lot Number
        if (immunization.hasLotNumber()) {
            terser.set(rxaPath + "-15", immunization.getLotNumber());
        }

        // RXA-16 Substance Expiration Date
        if (immunization.hasExpirationDate()) {
            terser.set(rxaPath + "-16", new SimpleDateFormat("yyyyMMdd").format(immunization.getExpirationDate()));
        }

        // RXA-17 Substance Manufacturer Name
        if (immunization.hasManufacturer()) {
            Reference manufacturer = immunization.getManufacturer();
            if (manufacturer.hasDisplay()) {
                terser.set(rxaPath + "-17-1", manufacturer.getDisplay());
            } else if (manufacturer.hasReference()) {
                String ref = manufacturer.getReference();
                if (ref.contains("/")) {
                    terser.set(rxaPath + "-17-1", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
        }

        // RXA-18 Substance/Treatment Refusal Reason
        if (immunization.hasStatusReason()) {
            CodeableConcept statusReason = immunization.getStatusReason();
            if (statusReason.hasCoding()) {
                terser.set(rxaPath + "-18-1", statusReason.getCodingFirstRep().getCode());
                if (statusReason.getCodingFirstRep().hasDisplay()) {
                    terser.set(rxaPath + "-18-2", statusReason.getCodingFirstRep().getDisplay());
                }
            }
        }

        // RXA-20 Completion Status
        if (immunization.hasStatus()) {
            String status = immunization.getStatus().toCode();
            if ("completed".equals(status)) {
                terser.set(rxaPath + "-20", "CP");
            } else if ("not-done".equals(status)) {
                terser.set(rxaPath + "-20", "NA");
            } else if ("entered-in-error".equals(status)) {
                terser.set(rxaPath + "-20", "PA");
            }
        }

        // RXA-21 Action Code
        if (immunization.hasPrimarySource()) {
            terser.set(rxaPath + "-21", immunization.getPrimarySource() ? "A" : "U");
        }

        rxaIndex++;
    }
}
