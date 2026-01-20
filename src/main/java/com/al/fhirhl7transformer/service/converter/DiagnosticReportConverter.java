package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Group;
import com.al.fhirhl7transformer.util.DateTimeUtil;
import com.al.fhirhl7transformer.util.MappingConstants;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class DiagnosticReportConverter implements SegmentConverter<DiagnosticReport> {

    @Override
    public List<DiagnosticReport> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<DiagnosticReport> reports = new ArrayList<>();
        log.info("ProcessDiagnosticReports called");
        int obrIndex = 0;
        while (obrIndex < 50) { // Safety limit
            try {
                String obrPath = "/.OBR(" + obrIndex + ")";
                String mainPathToUse = obrPath;
                boolean found = false;

                // Strategy to find OBR: Root -> ORDER -> ORDER_OBSERVATION ->
                // PATIENT_RESULT/ORDER_OBSERVATION
                String[] pathsToCheck = {
                        obrPath,
                        "/.ORDER(" + obrIndex + ")/OBR",
                        "/.ORDER_OBSERVATION(" + obrIndex + ")/OBR",
                        "/.PATIENT_RESULT/ORDER_OBSERVATION(" + obrIndex + ")/OBR",
                        "/.RESPONSE/ORDER(" + obrIndex + ")/OBR"
                };

                for (String path : pathsToCheck) {
                    try {
                        if (terser.getSegment(path) != null) {
                            mainPathToUse = path;
                            found = true;
                            break;
                        }
                    } catch (Exception ignored) {
                        // Continue to next path
                    }
                }

                if (!found)
                    break;

                log.info("Processing DiagnosticReport: {}", mainPathToUse);
                String code = terser.get(mainPathToUse + "-4-1");
                String display = terser.get(mainPathToUse + "-4-2");
                if (code == null) {
                    log.debug("Skipping DiagnosticReport for OBR at {} due to missing code", mainPathToUse);
                    // If we found a segment but it has no code, and it's > 0, it might be a phantom
                    // or empty segment.
                    // To avoid infinite loops if terser keeps returning it, we should probably
                    // break if it's not the first one.
                    if (obrIndex > 0)
                        break;
                    obrIndex++;
                    continue;
                }

                DiagnosticReport report = new DiagnosticReport();
                report.setId(UUID.randomUUID().toString());

                if (context.getPatientId() != null) {
                    report.setSubject(new Reference("Patient/" + context.getPatientId()));
                }
                if (context.getEncounterId() != null) {
                    report.setEncounter(new Reference("Encounter/" + context.getEncounterId()));
                }

                // OBR-2/3 Identifiers (Extracted early for linking)
                // Use component 1 for robust matching
                String placerId = terser.get(mainPathToUse + "-2-1");
                String fillerId = terser.get(mainPathToUse + "-3-1");
                log.info("DiagnosticReport OBR-{}, Placer={}, Filler={}", obrIndex, placerId, fillerId);

                // LINKING: Connect to ServiceRequest (Order) from Context
                // 1. Try Lookup by Placer ID
                ServiceRequest linkedOrder = null;
                if (placerId != null) {
                    linkedOrder = context.getServiceRequests().get("PLACER:" + placerId);
                }
                // 2. Try Lookup by Filler ID
                if (linkedOrder == null && fillerId != null) {
                    linkedOrder = context.getServiceRequests().get("FILLER:" + fillerId);
                }
                // 3. Fallback to Index
                if (linkedOrder == null) {
                    linkedOrder = context.getServiceRequests().get(String.valueOf(obrIndex));
                }

                if (linkedOrder != null) {
                    report.addBasedOn(new Reference("ServiceRequest/" + linkedOrder.getId()));
                    log.debug("Linked DiagnosticReport {} to ServiceRequest {}", report.getId(), linkedOrder.getId());
                } else {
                    log.debug("No linked ServiceRequest found for OBR index {}", obrIndex);
                }

                // OBR-4 Universal Service ID -> Code
                if (code != null) {
                    CodeableConcept cc = new CodeableConcept();
                    cc.addCoding().setSystem(MappingConstants.SYSTEM_LOINC).setCode(code).setDisplay(display);
                    report.setCode(cc);
                }

                // OBR-7 Observation Date/Time -> EffectiveDateTime
                String obsDate = terser.get(mainPathToUse + "-7");
                if (obsDate != null && !obsDate.isEmpty()) {
                    try {
                        report.setEffective(DateTimeUtil.hl7DateTimeToFhir(obsDate));
                    } catch (Exception e) {
                        log.warn("Failed to parse OBR-7 date: {}", obsDate);
                    }
                }

                // OBR-22 Status Change Date/Time -> Issued
                String issuedDate = terser.get(mainPathToUse + "-22");
                if (issuedDate != null && !issuedDate.isEmpty()) {
                    try {
                        DateTimeType dt = DateTimeUtil.hl7DateTimeToFhir(issuedDate);
                        if (dt != null)
                            report.setIssued(dt.getValue());
                    } catch (Exception e) {
                        log.warn("Failed to parse OBR-22 date: {}", issuedDate);
                    }
                }

                // OBR-25 Result Status -> Status
                String status = terser.get(mainPathToUse + "-25");
                if ("F".equals(status))
                    report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
                else if ("C".equals(status))
                    report.setStatus(DiagnosticReport.DiagnosticReportStatus.CORRECTED);
                else if ("X".equals(status))
                    report.setStatus(DiagnosticReport.DiagnosticReportStatus.CANCELLED);
                else if ("P".equals(status))
                    report.setStatus(DiagnosticReport.DiagnosticReportStatus.PRELIMINARY);
                else
                    report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);

                // Add identifiers to Report
                if (placerId != null) {
                    report.addIdentifier().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setValue(placerId)
                            .getType().addCoding().setCode("PLAC").setDisplay("Placer Identifier");
                }
                if (fillerId != null) {
                    report.addIdentifier().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setValue(fillerId)
                            .getType().addCoding().setCode("FILL").setDisplay("Filler Identifier");
                }

                // LINKING: Connect to Observations (Results) from Context
                List<Observation> results = null;
                if (placerId != null) {
                    results = context.getObservationsByObr().get(placerId.hashCode());
                }
                if (results == null && fillerId != null) {
                    results = context.getObservationsByObr().get(fillerId.hashCode());
                }
                if (results == null) {
                    results = context.getObservationsByObr().get(obrIndex);
                }

                if (results != null) {
                    for (Observation obs : results) {
                        report.addResult(new Reference("Observation/" + obs.getId()));
                    }
                    log.debug("Linked {} Observations to DiagnosticReport {}", results.size(), report.getId());
                }

                // OBR-32/34/35 Principal Result Interpreter / Technician / Transcriptionist ->
                // Performer
                mapPerformer(terser, obrPath + "-32", report);
                mapPerformer(terser, obrPath + "-34", report);
                mapPerformer(terser, obrPath + "-35", report);

                // Check for NTE segments (Notes) associated with this OBR for Conclusion
                try {
                    String obrIdCheck = terser.get(obrPath + "-1");
                    if (obrIdCheck != null && !obrIdCheck.isEmpty()) {
                        Segment obrSegment = terser.getSegment(obrPath);
                        if (obrSegment != null) {
                            Structure parent = obrSegment.getParent();
                            if (parent instanceof Group) {
                                Group group = (Group) parent;
                                String[] names = group.getNames();
                                boolean hasNTE = false;
                                for (String name : names) {
                                    if ("NTE".equals(name)) {
                                        hasNTE = true;
                                        break;
                                    }
                                }

                                if (hasNTE) {
                                    Structure[] ntes = group.getAll("NTE");
                                    StringBuilder conclusionBuilder = new StringBuilder();

                                    for (Structure nteStruct : ntes) {
                                        if (nteStruct instanceof Segment) {
                                            Segment nte = (Segment) nteStruct;
                                            // NTE-3: Comment
                                            ca.uhn.hl7v2.model.Type[] comments = nte.getField(3);
                                            for (ca.uhn.hl7v2.model.Type c : comments) {
                                                if (conclusionBuilder.length() > 0)
                                                    conclusionBuilder.append("\n");
                                                conclusionBuilder.append(c.toString());
                                            }
                                        }
                                    }

                                    if (conclusionBuilder.length() > 0) {
                                        report.setConclusion(conclusionBuilder.toString());
                                        log.debug("Mapped NTE segments to DiagnosticReport conclusion");
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing OBR NTEs", e);
                }

                reports.add(report);
                obrIndex++;
            } catch (Exception e) {
                log.error("Error processing OBR segment", e);
                break;
            }
        }
        return reports;
    }

    private void mapPerformer(Terser terser, String path, DiagnosticReport report) {
        try {
            String id = terser.get(path + "-1");
            String family = terser.get(path + "-2");
            if (id != null || family != null) {
                Reference performer = new Reference();
                if (id != null)
                    performer.setReference("Practitioner/" + id);
                if (family != null) {
                    String given = terser.get(path + "-3");
                    StringBuilder name = new StringBuilder(family);
                    if (given != null)
                        name.append(", ").append(given);
                    performer.setDisplay(name.toString());
                }
                report.addPerformer(performer);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
