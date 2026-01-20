package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Converts FHIR ServiceRequest to HL7 OBR (Observation Request) segment.
 */
@Component
public class ServiceRequestToObrConverter implements FhirToHl7Converter<ServiceRequest> {

    private static final SimpleDateFormat HL7_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");
    private int obrIndex = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof ServiceRequest;
    }

    @Override
    public void convert(ServiceRequest serviceRequest, Message message, Terser terser) throws HL7Exception {
        String obrPath = "/.OBR(" + obrIndex + ")";

        // OBR-1 Set ID
        terser.set(obrPath + "-1", String.valueOf(obrIndex + 1));

        // OBR-2 Placer Order Number
        // OBR-3 Filler Order Number
        if (serviceRequest.hasIdentifier()) {
            for (Identifier id : serviceRequest.getIdentifier()) {
                if (id.hasType() && id.getType().hasCoding()) {
                    String typeCode = id.getType().getCodingFirstRep().getCode();
                    if ("PLAC".equals(typeCode)) {
                        terser.set(obrPath + "-2", id.getValue());
                    } else if ("FILL".equals(typeCode)) {
                        terser.set(obrPath + "-3", id.getValue());
                    }
                } else if (id.hasValue()) {
                    // Default first identifier to placer
                    if (terser.get(obrPath + "-2") == null) {
                        terser.set(obrPath + "-2", id.getValue());
                    }
                }
            }
        }

        // OBR-4 Universal Service Identifier (Procedure Code)
        if (serviceRequest.hasCode()) {
            CodeableConcept code = serviceRequest.getCode();
            if (code.hasCoding()) {
                Coding coding = code.getCodingFirstRep();
                terser.set(obrPath + "-4-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(obrPath + "-4-2", coding.getDisplay());
                }
                if (coding.hasSystem()) {
                    terser.set(obrPath + "-4-3", coding.getSystem());
                }
            } else if (code.hasText()) {
                terser.set(obrPath + "-4-2", code.getText());
            }
        }

        // OBR-5 Priority
        if (serviceRequest.hasPriority()) {
            String priority = serviceRequest.getPriority().toCode();
            switch (priority) {
                case "stat":
                    terser.set(obrPath + "-5", "S");
                    break;
                case "asap":
                    terser.set(obrPath + "-5", "A");
                    break;
                case "urgent":
                    terser.set(obrPath + "-5", "U");
                    break;
                default:
                    terser.set(obrPath + "-5", "R"); // Routine
            }
        }

        // OBR-6 Requested Date/Time
        if (serviceRequest.hasAuthoredOn()) {
            terser.set(obrPath + "-6", HL7_DATE_FORMAT.format(serviceRequest.getAuthoredOn()));
        }

        // OBR-7 Observation Date/Time (scheduled time)
        if (serviceRequest.hasOccurrenceDateTimeType()) {
            terser.set(obrPath + "-7", HL7_DATE_FORMAT.format(serviceRequest.getOccurrenceDateTimeType().getValue()));
        } else if (serviceRequest.hasOccurrencePeriod()) {
            Period period = serviceRequest.getOccurrencePeriod();
            if (period.hasStart()) {
                terser.set(obrPath + "-7", HL7_DATE_FORMAT.format(period.getStart()));
            }
            if (period.hasEnd()) {
                terser.set(obrPath + "-8", HL7_DATE_FORMAT.format(period.getEnd()));
            }
        }

        // OBR-10 Collector Identifier
        // OBR-16 Ordering Provider
        if (serviceRequest.hasRequester()) {
            Reference requester = serviceRequest.getRequester();
            if (requester.hasReference()) {
                String ref = requester.getReference();
                if (ref.contains("/")) {
                    terser.set(obrPath + "-16-1", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
            if (requester.hasDisplay()) {
                terser.set(obrPath + "-16-2", requester.getDisplay());
            }
        }

        // OBR-13 Relevant Clinical Information
        if (serviceRequest.hasReasonCode()) {
            StringBuilder reasons = new StringBuilder();
            for (CodeableConcept reason : serviceRequest.getReasonCode()) {
                if (reason.hasText()) {
                    if (reasons.length() > 0)
                        reasons.append("; ");
                    reasons.append(reason.getText());
                } else if (reason.hasCoding() && reason.getCodingFirstRep().hasDisplay()) {
                    if (reasons.length() > 0)
                        reasons.append("; ");
                    reasons.append(reason.getCodingFirstRep().getDisplay());
                }
            }
            if (reasons.length() > 0) {
                terser.set(obrPath + "-13", reasons.toString());
            }
        }

        // OBR-15 Specimen Source
        if (serviceRequest.hasSpecimen()) {
            Reference specimen = serviceRequest.getSpecimenFirstRep();
            if (specimen.hasReference()) {
                String ref = specimen.getReference();
                if (ref.contains("/")) {
                    terser.set(obrPath + "-15", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
        }

        // OBR-18 Placer Field 1 (Patient Location)
        // OBR-20 Filler Field 1

        // OBR-24 Diagnostic Service Section ID
        if (serviceRequest.hasCategory()) {
            CodeableConcept category = serviceRequest.getCategoryFirstRep();
            if (category.hasCoding()) {
                terser.set(obrPath + "-24", category.getCodingFirstRep().getCode());
            }
        }

        // OBR-25 Result Status
        if (serviceRequest.hasStatus()) {
            String status = serviceRequest.getStatus().toCode();
            switch (status) {
                case "draft":
                    terser.set(obrPath + "-25", "O"); // Order received
                    break;
                case "active":
                    terser.set(obrPath + "-25", "I"); // In-progress
                    break;
                case "completed":
                    terser.set(obrPath + "-25", "F"); // Final
                    break;
                case "revoked":
                case "entered-in-error":
                    terser.set(obrPath + "-25", "X"); // Cancelled
                    break;
                default:
                    terser.set(obrPath + "-25", "O");
            }
        }

        // OBR-27 Quantity/Timing (from occurrence timing)

        // OBR-31 Reason for Study
        if (serviceRequest.hasReasonCode()) {
            CodeableConcept reason = serviceRequest.getReasonCodeFirstRep();
            if (reason.hasCoding()) {
                terser.set(obrPath + "-31-1", reason.getCodingFirstRep().getCode());
                if (reason.getCodingFirstRep().hasDisplay()) {
                    terser.set(obrPath + "-31-2", reason.getCodingFirstRep().getDisplay());
                }
            }
        }

        // OBR-44 Procedure Code
        if (serviceRequest.hasOrderDetail()) {
            CodeableConcept detail = serviceRequest.getOrderDetailFirstRep();
            if (detail.hasCoding()) {
                terser.set(obrPath + "-44-1", detail.getCodingFirstRep().getCode());
            }
        }

        obrIndex++;
    }
}
