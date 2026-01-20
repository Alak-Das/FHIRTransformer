package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converter for creating FHIR ServiceRequest/Task resources from HL7 ORC
 * (Common Order) segment.
 * Used primarily for ORM^O01 (Order) messages.
 * 
 * ORC Segment Structure:
 * - ORC-1: Order Control (NW=New, CA=Cancel, etc.)
 * - ORC-2: Placer Order Number
 * - ORC-3: Filler Order Number
 * - ORC-4: Placer Group Number
 * - ORC-5: Order Status (A=Some, CM=Complete, etc.)
 * - ORC-7: Quantity/Timing
 * - ORC-9: Date/Time of Transaction
 * - ORC-10: Entered By
 * - ORC-12: Ordering Provider
 * - ORC-13: Enterer's Location
 * - ORC-14: Call Back Phone Number
 * - ORC-15: Order Effective Date/Time
 * - ORC-16: Order Control Code Reason
 * - ORC-17: Entering Organization
 * - ORC-21: Ordering Facility Name
 */
@Component
@Slf4j
public class OrderConverter implements SegmentConverter<DomainResource> {

    @Override
    public List<DomainResource> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<DomainResource> resources = new ArrayList<>();

        int orcIndex = 0;
        while (orcIndex < 50) { // Safety limit
            try {
                String orcPath = "/.ORC(" + orcIndex + ")";
                boolean found = false;

                try {
                    if (terser.getSegment(orcPath) != null) {
                        found = true;
                    }
                } catch (Exception e) {
                    // Try ORDER group path
                    String orderPath = "/.ORDER(" + orcIndex + ")/ORC";
                    try {
                        if (terser.getSegment(orderPath) != null) {
                            orcPath = orderPath;
                            found = true;
                        }
                    } catch (Exception ex) {
                        // Not found
                    }
                }

                if (!found)
                    break;

                // ORC-1: Order Control (required)
                String orderControl = terser.get(orcPath + "-1");
                String placerNumber = terser.get(orcPath + "-2-1");
                String fillerNumber = terser.get(orcPath + "-3-1");

                if (isEmpty(orderControl) && isEmpty(placerNumber) && isEmpty(fillerNumber)) {
                    orcIndex++;
                    continue;
                }

                log.info("Processing Order from ORC({}): Control={}, Placer={}, Filler={}",
                        orcIndex, orderControl, placerNumber, fillerNumber);

                // Create ServiceRequest for the order details
                ServiceRequest serviceRequest = new ServiceRequest();
                serviceRequest.setId(UUID.randomUUID().toString());
                serviceRequest.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
                serviceRequest.setStatus(mapOrderStatus(terser.get(orcPath + "-5")));

                // Identifiers
                if (!isEmpty(placerNumber)) {
                    serviceRequest.addIdentifier()
                            .setType(new CodeableConcept()
                                    .addCoding(new Coding()
                                            .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                                            .setCode("PLAC")
                                            .setDisplay("Placer Identifier")))
                            .setValue(placerNumber);
                }

                if (!isEmpty(fillerNumber)) {
                    serviceRequest.addIdentifier()
                            .setType(new CodeableConcept()
                                    .addCoding(new Coding()
                                            .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                                            .setCode("FILL")
                                            .setDisplay("Filler Identifier")))
                            .setValue(fillerNumber);
                }

                // ORC-9: Date/Time of Transaction -> authoredOn
                String transactionDateTime = terser.get(orcPath + "-9-1");
                if (!isEmpty(transactionDateTime)) {
                    try {
                        serviceRequest.setAuthoredOnElement(new DateTimeType(parseHl7DateTime(transactionDateTime)));
                    } catch (Exception e) {
                        log.debug("Could not parse transaction date: {}", transactionDateTime);
                    }
                }

                // ORC-12: Ordering Provider -> requester
                String orderingProviderId = terser.get(orcPath + "-12-1");
                String orderingProviderName = terser.get(orcPath + "-12-2");
                if (!isEmpty(orderingProviderId) || !isEmpty(orderingProviderName)) {
                    String display = !isEmpty(orderingProviderName) ? orderingProviderName : orderingProviderId;
                    serviceRequest.setRequester(new Reference().setDisplay(display));
                }

                // ORC-15: Order Effective Date/Time -> occurrence
                String effectiveDateTime = terser.get(orcPath + "-15-1");
                if (!isEmpty(effectiveDateTime)) {
                    try {
                        serviceRequest.setOccurrence(new DateTimeType(parseHl7DateTime(effectiveDateTime)));
                    } catch (Exception e) {
                        log.debug("Could not parse effective date: {}", effectiveDateTime);
                    }
                }

                // ORC-16: Order Control Code Reason -> reasonCode
                String reasonCode = terser.get(orcPath + "-16-1");
                if (!isEmpty(reasonCode)) {
                    String reasonDisplay = terser.get(orcPath + "-16-2");
                    serviceRequest.addReasonCode(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setCode(reasonCode)
                                    .setDisplay(reasonDisplay)));
                }

                // Try to find corresponding OBR segment for Order Code (OBR-4)
                String obrPath = orcPath.replace("ORC", "OBR");
                if (terser.getSegment(obrPath) != null) {
                    // OBR-4: Universal Service Identifier -> code
                    String code = terser.get(obrPath + "-4-1");
                    String display = terser.get(obrPath + "-4-2");
                    if (!isEmpty(code)) {
                        serviceRequest.setCode(new CodeableConcept()
                                .addCoding(new Coding()
                                        .setCode(code)
                                        .setDisplay(display)));
                    }

                    // OBR-5: Priority -> priority
                    String priority = terser.get(obrPath + "-5");
                    if (!isEmpty(priority)) {
                        serviceRequest.setPriority(mapPriority(priority));
                    }
                }

                // Link to patient
                if (context != null && context.getPatientId() != null) {
                    serviceRequest.setSubject(new Reference("Patient/" + context.getPatientId()));
                }

                // Link to encounter
                if (context != null && context.getEncounterId() != null) {
                    serviceRequest.setEncounter(new Reference("Encounter/" + context.getEncounterId()));
                }

                resources.add(serviceRequest);

                // Store in context for linking with related OBR
                if (context != null) {
                    String key = !isEmpty(placerNumber) ? placerNumber
                            : (!isEmpty(fillerNumber) ? fillerNumber : String.valueOf(orcIndex));
                    context.getServiceRequests().put(key, serviceRequest);
                }

                // Create Task if order control indicates workflow action
                if (isWorkflowAction(orderControl)) {
                    Task task = createOrderTask(terser, orcPath, orderControl, serviceRequest);
                    if (context != null && context.getPatientId() != null) {
                        task.setFor(new Reference("Patient/" + context.getPatientId()));
                    }
                    resources.add(task);
                }

                orcIndex++;

            } catch (Exception e) {
                log.error("Error processing ORC segment at index {}", orcIndex, e);
                orcIndex++;
            }
        }

        log.info("Created {} resources from ORC segments", resources.size());
        return resources;
    }

    /**
     * Create a Task resource for order workflow
     */
    private Task createOrderTask(Terser terser, String orcPath, String orderControl,
            ServiceRequest serviceRequest) throws Exception {
        Task task = new Task();
        task.setId(UUID.randomUUID().toString());
        task.setStatus(mapTaskStatus(orderControl));
        task.setIntent(Task.TaskIntent.ORDER);

        // Link to the ServiceRequest
        task.setFocus(new Reference("ServiceRequest/" + serviceRequest.getId()));

        // ORC-1: Order Control -> business status
        task.setBusinessStatus(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v2-0119")
                        .setCode(orderControl)
                        .setDisplay(mapOrderControlDisplay(orderControl))));

        // ORC-9: Transaction time -> lastModified
        String transactionDateTime = terser.get(orcPath + "-9-1");
        if (!isEmpty(transactionDateTime)) {
            try {
                task.setLastModifiedElement(new DateTimeType(parseHl7DateTime(transactionDateTime)));
            } catch (Exception e) {
                log.debug("Could not parse transaction date for task: {}", transactionDateTime);
            }
        }

        // ORC-10: Entered By -> owner
        String enteredBy = terser.get(orcPath + "-10-1");
        if (!isEmpty(enteredBy)) {
            String enteredByName = terser.get(orcPath + "-10-2");
            task.setOwner(new Reference().setDisplay(
                    !isEmpty(enteredByName) ? enteredByName : enteredBy));
        }

        return task;
    }

    /**
     * Check if order control indicates a workflow action needing a Task
     */
    private boolean isWorkflowAction(String orderControl) {
        if (orderControl == null)
            return false;
        // Actions that warrant a Task resource
        return "NW".equals(orderControl) || // New order
                "CA".equals(orderControl) || // Cancel order
                "DC".equals(orderControl) || // Discontinue
                "HD".equals(orderControl) || // Hold
                "RL".equals(orderControl) || // Release
                "UA".equals(orderControl) || // Unable to accept
                "SC".equals(orderControl); // Status changed
    }

    /**
     * Map ORC-5 Order Status to FHIR ServiceRequest status
     */
    private ServiceRequest.ServiceRequestStatus mapOrderStatus(String hl7Status) {
        if (hl7Status == null)
            return ServiceRequest.ServiceRequestStatus.ACTIVE;
        switch (hl7Status.toUpperCase()) {
            case "A": // Some, but not all, results available
            case "IP": // In process
                return ServiceRequest.ServiceRequestStatus.ACTIVE;
            case "CM": // Order completed
                return ServiceRequest.ServiceRequestStatus.COMPLETED;
            case "CA": // Canceled
            case "DC": // Discontinued
                return ServiceRequest.ServiceRequestStatus.REVOKED;
            case "HD": // On hold
                return ServiceRequest.ServiceRequestStatus.ONHOLD;
            case "ER": // Error
                return ServiceRequest.ServiceRequestStatus.ENTEREDINERROR;
            default:
                return ServiceRequest.ServiceRequestStatus.ACTIVE;
        }
    }

    /**
     * Map ORC-1 Order Control to Task status
     */
    private Task.TaskStatus mapTaskStatus(String orderControl) {
        if (orderControl == null)
            return Task.TaskStatus.REQUESTED;
        switch (orderControl.toUpperCase()) {
            case "NW": // New order
            case "OK": // Order accepted
                return Task.TaskStatus.REQUESTED;
            case "IP": // In progress
                return Task.TaskStatus.INPROGRESS;
            case "CM": // Completed
            case "CR": // Response to canceled
                return Task.TaskStatus.COMPLETED;
            case "CA": // Cancel
            case "DC": // Discontinue
                return Task.TaskStatus.CANCELLED;
            case "HD": // Hold
                return Task.TaskStatus.ONHOLD;
            case "UA": // Unable to accept
            case "ER": // Error
                return Task.TaskStatus.FAILED;
            default:
                return Task.TaskStatus.REQUESTED;
        }
    }

    /**
     * Map ORC-1 Order Control code to display text
     */
    private String mapOrderControlDisplay(String orderControl) {
        if (orderControl == null)
            return "Unknown";
        switch (orderControl.toUpperCase()) {
            case "NW":
                return "New Order";
            case "OK":
                return "Order Accepted";
            case "CA":
                return "Cancel Order";
            case "DC":
                return "Discontinue Order";
            case "HD":
                return "Hold Order";
            case "RL":
                return "Release Hold";
            case "CR":
                return "Canceled as Requested";
            case "UA":
                return "Unable to Accept";
            case "CM":
                return "Order Completed";
            case "SC":
                return "Status Changed";
            default:
                return orderControl;
        }
    }

    private String parseHl7DateTime(String hl7DateTime) {
        if (hl7DateTime == null || hl7DateTime.length() < 8)
            return null;

        StringBuilder sb = new StringBuilder();
        sb.append(hl7DateTime.substring(0, 4)).append("-");
        sb.append(hl7DateTime.substring(4, 6)).append("-");
        sb.append(hl7DateTime.substring(6, 8));

        if (hl7DateTime.length() >= 12) {
            sb.append("T");
            sb.append(hl7DateTime.substring(8, 10)).append(":");
            sb.append(hl7DateTime.substring(10, 12));
            if (hl7DateTime.length() >= 14) {
                sb.append(":").append(hl7DateTime.substring(12, 14));
            }
        }

        return sb.toString();
    }

    private ServiceRequest.ServiceRequestPriority mapPriority(String priority) {
        if (priority == null)
            return ServiceRequest.ServiceRequestPriority.ROUTINE;
        switch (priority.toUpperCase()) {
            case "S":
                return ServiceRequest.ServiceRequestPriority.STAT;
            case "A": // ASAP
            case "U": // Urgent
                return ServiceRequest.ServiceRequestPriority.URGENT;
            case "R":
            default:
                return ServiceRequest.ServiceRequestPriority.ROUTINE;
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
