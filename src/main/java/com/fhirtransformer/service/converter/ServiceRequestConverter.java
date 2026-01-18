package com.fhirtransformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import com.fhirtransformer.util.DateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ServiceRequestConverter implements SegmentConverter<ServiceRequest> {

    @Override
    public List<ServiceRequest> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<ServiceRequest> requests = new ArrayList<>();
        log.debug("Processing ServiceRequest segments...");
        int index = 0;
        while (index < 50) { // Safety limit
            try {
                String obrPath = "/.OBR(" + index + ")";
                String mainPathToUse = obrPath;
                String mainOrcPath = "/.ORC(" + index + ")";
                boolean found = false;

                // Try root path first
                try {
                    if (terser.getSegment(obrPath) != null) {
                        found = true;
                    }
                } catch (Exception e) {
                    // Try ORDER group path
                    String orderPath = "/.ORDER(" + index + ")/OBR";
                    try {
                        if (terser.getSegment(orderPath) != null) {
                            mainPathToUse = orderPath;
                            mainOrcPath = "/.ORDER(" + index + ")/ORC";
                            found = true;
                        }
                    } catch (Exception ex) {
                        // Not found
                    }
                }

                if (!found)
                    break;

                log.info("Processing ServiceRequest: {}", mainPathToUse);
                String code = terser.get(mainPathToUse + "-4-1");
                if (code == null) {
                    log.warn("Missing code for ServiceRequest at {}", mainPathToUse);
                    index++;
                    continue;
                }

                ServiceRequest sr = new ServiceRequest();
                sr.setId(UUID.randomUUID().toString());
                sr.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE); // Default
                sr.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);

                // Status Mapping from ORC (if available)
                try {
                    String orc1 = terser.get(mainOrcPath + "-1");
                    String orc5 = terser.get(mainOrcPath + "-5");
                    if ("CA".equals(orc1) || "OC".equals(orc1) || "CR".equals(orc1)) {
                        sr.setStatus(ServiceRequest.ServiceRequestStatus.REVOKED);
                    } else if ("DC".equals(orc1) || "OD".equals(orc1)) {
                        sr.setStatus(ServiceRequest.ServiceRequestStatus.REVOKED);
                    } else if (orc5 != null) {
                        switch (orc5) {
                            case "CM":
                                sr.setStatus(ServiceRequest.ServiceRequestStatus.COMPLETED);
                                break;
                            case "DC":
                            case "CA":
                                sr.setStatus(ServiceRequest.ServiceRequestStatus.REVOKED);
                                break;
                            case "IP":
                                sr.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
                                break;
                            case "SC":
                                sr.setStatus(ServiceRequest.ServiceRequestStatus.ONHOLD);
                                break;
                        }
                    }
                } catch (Exception e) {
                    log.debug("No ORC status for ServiceRequest at {}", mainOrcPath);
                }

                // Priority
                String priority = terser.get(mainPathToUse + "-5");
                if ("S".equals(priority))
                    sr.setPriority(ServiceRequest.ServiceRequestPriority.STAT);
                else if ("A".equals(priority))
                    sr.setPriority(ServiceRequest.ServiceRequestPriority.URGENT);
                else
                    sr.setPriority(ServiceRequest.ServiceRequestPriority.ROUTINE);

                sr.getCode().addCoding()
                        .setCode(code)
                        .setDisplay(terser.get(mainPathToUse + "-4-2"));

                // OBR-27.4 Timing (Start Date/Time)
                String timing = terser.get(mainPathToUse + "-27-4");
                if (timing != null && !timing.isEmpty()) {
                    try {
                        sr.setOccurrence(DateTimeUtil.hl7DateTimeToFhir(timing));
                    } catch (Exception e) {
                        log.debug("Failed to parse ServiceRequest timing: {}", timing);
                    }
                }

                // OBR-31 Reason for Study
                String reason = terser.get(mainPathToUse + "-31-2");
                if (reason == null)
                    reason = terser.get(mainPathToUse + "-31-1");
                if (reason != null && !reason.isEmpty()) {
                    sr.addReasonCode().setText(reason);
                }

                // OBR-16 Ordering Provider -> Requester
                String provId = terser.get(mainPathToUse + "-16-1");
                String provName = terser.get(mainPathToUse + "-16-2");
                if (provId != null || provName != null) {
                    Reference requester = new Reference();
                    if (provId != null)
                        requester.setReference("Practitioner/" + provId);
                    if (provName != null)
                        requester.setDisplay(provName);
                    sr.setRequester(requester);
                }

                if (context.getPatientId() != null) {
                    sr.setSubject(new Reference("Patient/" + context.getPatientId()));
                }

                if (context.getEncounterId() != null) {
                    sr.setEncounter(new Reference("Encounter/" + context.getEncounterId()));
                }

                // ORC Information (Placer/Filler Order Numbers) for Linking - Optional
                try {
                    String orcId = terser.get(mainOrcPath + "-1");
                    if (orcId != null && !orcId.isEmpty()) {
                        String placerId = terser.get(mainOrcPath + "-2");
                        String fillerId = terser.get(mainOrcPath + "-3");

                        if (placerId != null) {
                            sr.addIdentifier().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                                    .setValue(placerId)
                                    .getType().addCoding().setCode("PLAC").setDisplay("Placer Identifier");
                            // Index by Placer ID
                            context.getServiceRequests().put("PLACER:" + placerId, sr);
                        }
                        if (fillerId != null) {
                            sr.addIdentifier().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                                    .setValue(fillerId)
                                    .getType().addCoding().setCode("FILL").setDisplay("Filler Identifier");
                            // Index by Filler ID
                            context.getServiceRequests().put("FILLER:" + fillerId, sr);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Optional ORC segment not found for OBR index {}", index);
                }

                // Add to list
                requests.add(sr);

                // KEY CHANGE: Add to Context for Linking (DiagnosticReport will access this)
                // Use index as key (OBR-0 -> ServiceRequest-0) - Fallback
                context.getServiceRequests().put(String.valueOf(index), sr);
                log.debug("Cached ServiceRequest for OBR index {}: {}", index, sr.getId());

                index++;
            } catch (Exception e) {
                log.warn("Error processing ServiceRequest segments (index {}): {}", index, e.getMessage());
                break;
            }
        }
        return requests;
    }
}
