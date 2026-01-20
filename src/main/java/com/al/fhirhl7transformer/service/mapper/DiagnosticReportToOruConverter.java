package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Converts FHIR DiagnosticReport to HL7 OBR (Observation Request) and OBX
 * (Observation) segments.
 * Used for ORU^R01 messages.
 */
@Component
public class DiagnosticReportToOruConverter implements FhirToHl7Converter<DiagnosticReport> {

    private static final SimpleDateFormat HL7_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");
    private int obrIndex = 0;
    private int obxIndex = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof DiagnosticReport;
    }

    @Override
    public void convert(DiagnosticReport report, Message message, Terser terser) throws HL7Exception {
        String obrPath = "/.OBR(" + obrIndex + ")";

        // OBR-1 Set ID
        terser.set(obrPath + "-1", String.valueOf(obrIndex + 1));

        // OBR-2/3 Placer/Filler Order Number
        if (report.hasIdentifier()) {
            for (Identifier id : report.getIdentifier()) {
                if (id.hasType() && id.getType().hasCoding()) {
                    String typeCode = id.getType().getCodingFirstRep().getCode();
                    if ("PLAC".equals(typeCode)) {
                        terser.set(obrPath + "-2", id.getValue());
                    } else if ("FILL".equals(typeCode)) {
                        terser.set(obrPath + "-3", id.getValue());
                    }
                } else if (id.hasValue()) {
                    if (terser.get(obrPath + "-3") == null) {
                        terser.set(obrPath + "-3", id.getValue());
                    }
                }
            }
        }

        // OBR-4 Universal Service Identifier
        if (report.hasCode()) {
            CodeableConcept code = report.getCode();
            if (code.hasCoding()) {
                Coding coding = code.getCodingFirstRep();
                terser.set(obrPath + "-4-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(obrPath + "-4-2", coding.getDisplay());
                }
                if (coding.hasSystem()) {
                    terser.set(obrPath + "-4-3", coding.getSystem());
                }
            }
        }

        // OBR-7 Observation Date/Time
        if (report.hasEffectiveDateTimeType()) {
            terser.set(obrPath + "-7", HL7_DATE_FORMAT.format(report.getEffectiveDateTimeType().getValue()));
        } else if (report.hasEffectivePeriod()) {
            Period period = report.getEffectivePeriod();
            if (period.hasStart()) {
                terser.set(obrPath + "-7", HL7_DATE_FORMAT.format(period.getStart()));
            }
            if (period.hasEnd()) {
                terser.set(obrPath + "-8", HL7_DATE_FORMAT.format(period.getEnd()));
            }
        }

        // OBR-14 Specimen Received Date/Time
        // OBR-16 Ordering Provider
        if (report.hasResultsInterpreter()) {
            Reference interpreter = report.getResultsInterpreterFirstRep();
            if (interpreter.hasReference()) {
                String ref = interpreter.getReference();
                if (ref.contains("/")) {
                    terser.set(obrPath + "-32-1", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
            if (interpreter.hasDisplay()) {
                terser.set(obrPath + "-32-2", interpreter.getDisplay());
            }
        }

        // OBR-22 Results Rpt/Status Change Date/Time
        if (report.hasIssued()) {
            terser.set(obrPath + "-22", HL7_DATE_FORMAT.format(report.getIssued()));
        }

        // OBR-24 Diagnostic Service Section ID
        if (report.hasCategory()) {
            CodeableConcept category = report.getCategoryFirstRep();
            if (category.hasCoding()) {
                terser.set(obrPath + "-24", category.getCodingFirstRep().getCode());
            }
        }

        // OBR-25 Result Status
        if (report.hasStatus()) {
            String status = report.getStatus().toCode();
            switch (status) {
                case "registered":
                    terser.set(obrPath + "-25", "O");
                    break;
                case "partial":
                    terser.set(obrPath + "-25", "P");
                    break;
                case "preliminary":
                    terser.set(obrPath + "-25", "P");
                    break;
                case "final":
                    terser.set(obrPath + "-25", "F");
                    break;
                case "amended":
                    terser.set(obrPath + "-25", "A");
                    break;
                case "corrected":
                    terser.set(obrPath + "-25", "C");
                    break;
                case "cancelled":
                case "entered-in-error":
                    terser.set(obrPath + "-25", "X");
                    break;
                default:
                    terser.set(obrPath + "-25", "F");
            }
        }

        obrIndex++;

        // Process Conclusions/Findings as OBX segments
        if (report.hasConclusion()) {
            String obxPath = "/.OBX(" + obxIndex + ")";

            terser.set(obxPath + "-1", String.valueOf(obxIndex + 1));
            terser.set(obxPath + "-2", "TX"); // Text
            terser.set(obxPath + "-3-1", "CONCLUSION");
            terser.set(obxPath + "-3-2", "Report Conclusion");
            terser.set(obxPath + "-5", report.getConclusion());
            terser.set(obxPath + "-11", "F"); // Final

            obxIndex++;
        }

        // Process Conclusion Codes as OBX segments
        if (report.hasConclusionCode()) {
            for (CodeableConcept conclusionCode : report.getConclusionCode()) {
                String obxPath = "/.OBX(" + obxIndex + ")";

                terser.set(obxPath + "-1", String.valueOf(obxIndex + 1));
                terser.set(obxPath + "-2", "CE"); // Coded Entry
                terser.set(obxPath + "-3-1", "CONCLUSION_CODE");
                terser.set(obxPath + "-3-2", "Conclusion Code");

                if (conclusionCode.hasCoding()) {
                    Coding coding = conclusionCode.getCodingFirstRep();
                    terser.set(obxPath + "-5-1", coding.getCode());
                    if (coding.hasDisplay()) {
                        terser.set(obxPath + "-5-2", coding.getDisplay());
                    }
                    if (coding.hasSystem()) {
                        terser.set(obxPath + "-5-3", coding.getSystem());
                    }
                }

                terser.set(obxPath + "-11", "F");
                obxIndex++;
            }
        }

        // Process Presented Form (attachments) as OBX
        if (report.hasPresentedForm()) {
            for (Attachment attachment : report.getPresentedForm()) {
                String obxPath = "/.OBX(" + obxIndex + ")";

                terser.set(obxPath + "-1", String.valueOf(obxIndex + 1));
                terser.set(obxPath + "-2", "ED"); // Encapsulated Data
                terser.set(obxPath + "-3-1", "REPORT_ATTACHMENT");
                terser.set(obxPath + "-3-2", attachment.hasTitle() ? attachment.getTitle() : "Report Attachment");

                // ED format: type^data_subtype^encoding^data
                StringBuilder edValue = new StringBuilder();
                if (attachment.hasContentType()) {
                    edValue.append(attachment.getContentType());
                }
                edValue.append("^");
                if (attachment.hasData()) {
                    edValue.append("Base64^A^");
                    edValue.append(new String(attachment.getData()));
                } else if (attachment.hasUrl()) {
                    edValue.append("URL^^");
                    edValue.append(attachment.getUrl());
                }

                terser.set(obxPath + "-5", edValue.toString());
                terser.set(obxPath + "-11", "F");

                obxIndex++;
            }
        }
    }
}
