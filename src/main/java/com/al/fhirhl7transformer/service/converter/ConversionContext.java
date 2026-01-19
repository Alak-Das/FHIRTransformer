package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.model.Message;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversionContext {
    private String patientId;
    private String encounterId;
    private String transactionId;
    private Message hapiMessage;

    @Builder.Default
    private java.util.Map<String, org.hl7.fhir.r4.model.ServiceRequest> serviceRequests = new java.util.HashMap<>();

    @Builder.Default
    private java.util.Map<String, org.hl7.fhir.r4.model.MedicationRequest> medicationRequests = new java.util.HashMap<>();

    @Builder.Default
    private java.util.Map<Integer, java.util.List<org.hl7.fhir.r4.model.Observation>> observationsByObr = new java.util.HashMap<>();

    private String triggerEvent;
}
