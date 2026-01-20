package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Converts HL7 ORC (Common Order) segment to FHIR CarePlan resource.
 * ORC segments in ORM messages represent treatment plans and orders.
 */
@Slf4j
@Component
public class CarePlanConverter implements SegmentConverter<CarePlan> {

    private final SimpleDateFormat hl7DateFormat = new SimpleDateFormat("yyyyMMdd");
    private final SimpleDateFormat hl7DateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    @Override
    public List<CarePlan> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<CarePlan> results = new ArrayList<>();

        // Only process ORC for CarePlan in order-related contexts
        int orcIndex = 0;

        while (orcIndex < 20) { // Safety limit
            try {
                String orcPath = "/.ORC(" + orcIndex + ")";
                String orderControl = terser.get(orcPath + "-1");

                if (orderControl == null || orderControl.isEmpty()) {
                    // Try ORDER group path
                    try {
                        orcPath = "/ORDER(" + orcIndex + ")/ORC";
                        orderControl = terser.get(orcPath + "-1");
                        if (orderControl == null || orderControl.isEmpty()) {
                            break;
                        }
                    } catch (Exception e) {
                        break;
                    }
                }

                CarePlan carePlan = new CarePlan();
                carePlan.setId(java.util.UUID.randomUUID().toString());

                // ORC-1 Order Control -> status/intent
                switch (orderControl) {
                    case "NW": // New Order
                        carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
                        carePlan.setIntent(CarePlan.CarePlanIntent.ORDER);
                        break;
                    case "CA": // Cancel
                    case "DC": // Discontinue
                        carePlan.setStatus(CarePlan.CarePlanStatus.REVOKED);
                        carePlan.setIntent(CarePlan.CarePlanIntent.ORDER);
                        break;
                    case "HD": // Hold
                        carePlan.setStatus(CarePlan.CarePlanStatus.ONHOLD);
                        carePlan.setIntent(CarePlan.CarePlanIntent.ORDER);
                        break;
                    case "RP": // Replace
                    case "XO": // Change
                        carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
                        carePlan.setIntent(CarePlan.CarePlanIntent.ORDER);
                        break;
                    default:
                        carePlan.setStatus(CarePlan.CarePlanStatus.DRAFT);
                        carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
                }

                // ORC-2 Placer Order Number
                String placerOrderNumber = terser.get(orcPath + "-2-1");
                if (placerOrderNumber != null && !placerOrderNumber.isEmpty()) {
                    carePlan.addIdentifier()
                            .setValue(placerOrderNumber)
                            .setSystem("urn:hl7:placer-order-number")
                            .setType(new CodeableConcept()
                                    .addCoding(new Coding()
                                            .setCode("PLAC")
                                            .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")));
                }

                // ORC-3 Filler Order Number
                String fillerOrderNumber = terser.get(orcPath + "-3-1");
                if (fillerOrderNumber != null && !fillerOrderNumber.isEmpty()) {
                    carePlan.addIdentifier()
                            .setValue(fillerOrderNumber)
                            .setSystem("urn:hl7:filler-order-number")
                            .setType(new CodeableConcept()
                                    .addCoding(new Coding()
                                            .setCode("FILL")
                                            .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")));
                }

                // ORC-7 Quantity/Timing -> Period
                String startDateTime = terser.get(orcPath + "-7-4");
                String endDateTime = terser.get(orcPath + "-7-5");
                if ((startDateTime != null && !startDateTime.isEmpty()) ||
                        (endDateTime != null && !endDateTime.isEmpty())) {
                    Period period = new Period();
                    if (startDateTime != null && !startDateTime.isEmpty()) {
                        try {
                            Date start = startDateTime.length() > 8
                                    ? hl7DateTimeFormat
                                            .parse(startDateTime.substring(0, Math.min(14, startDateTime.length())))
                                    : hl7DateFormat
                                            .parse(startDateTime.substring(0, Math.min(8, startDateTime.length())));
                            period.setStart(start);
                        } catch (ParseException e) {
                            log.debug("Could not parse start date: {}", startDateTime);
                        }
                    }
                    if (endDateTime != null && !endDateTime.isEmpty()) {
                        try {
                            Date end = endDateTime.length() > 8
                                    ? hl7DateTimeFormat
                                            .parse(endDateTime.substring(0, Math.min(14, endDateTime.length())))
                                    : hl7DateFormat.parse(endDateTime.substring(0, Math.min(8, endDateTime.length())));
                            period.setEnd(end);
                        } catch (ParseException e) {
                            log.debug("Could not parse end date: {}", endDateTime);
                        }
                    }
                    carePlan.setPeriod(period);
                }

                // ORC-9 Date/Time of Transaction (created)
                String transactionDate = terser.get(orcPath + "-9");
                if (transactionDate != null && !transactionDate.isEmpty()) {
                    try {
                        Date created = transactionDate.length() > 8
                                ? hl7DateTimeFormat
                                        .parse(transactionDate.substring(0, Math.min(14, transactionDate.length())))
                                : hl7DateFormat
                                        .parse(transactionDate.substring(0, Math.min(8, transactionDate.length())));
                        carePlan.setCreated(created);
                    } catch (ParseException e) {
                        log.debug("Could not parse transaction date: {}", transactionDate);
                    }
                }

                // ORC-10 Entered By (author)
                String enteredById = terser.get(orcPath + "-10-1");
                String enteredByFamily = terser.get(orcPath + "-10-2");
                String enteredByGiven = terser.get(orcPath + "-10-3");
                if (enteredById != null || enteredByFamily != null) {
                    String display = enteredByFamily != null ? enteredByFamily : "";
                    if (enteredByGiven != null) {
                        display = enteredByGiven + " " + display;
                    }
                    carePlan.setAuthor(new Reference()
                            .setReference("Practitioner/"
                                    + (enteredById != null ? enteredById : java.util.UUID.randomUUID().toString()))
                            .setDisplay(display.trim()));
                }

                // ORC-12 Ordering Provider -> Contributor
                String orderingProviderId = terser.get(orcPath + "-12-1");
                String orderingProviderFamily = terser.get(orcPath + "-12-2");
                String orderingProviderGiven = terser.get(orcPath + "-12-3");
                if (orderingProviderId != null || orderingProviderFamily != null) {
                    String display = orderingProviderFamily != null ? orderingProviderFamily : "";
                    if (orderingProviderGiven != null) {
                        display = orderingProviderGiven + " " + display;
                    }
                    carePlan.addContributor(new Reference()
                            .setReference("Practitioner/" + (orderingProviderId != null ? orderingProviderId
                                    : java.util.UUID.randomUUID().toString()))
                            .setDisplay(display.trim()));
                }

                // Add title/description
                carePlan.setTitle("Treatment Plan - Order "
                        + (placerOrderNumber != null ? placerOrderNumber : fillerOrderNumber));
                carePlan.setDescription("CarePlan derived from HL7 ORC segment");

                // Add category
                carePlan.addCategory(new CodeableConcept()
                        .addCoding(new Coding()
                                .setCode("assess-plan")
                                .setSystem("http://hl7.org/fhir/care-plan-category")
                                .setDisplay("Assessment and Plan")));

                // Link to patient if available
                if (context.getPatientId() != null) {
                    carePlan.setSubject(new Reference("Patient/" + context.getPatientId()));
                }

                results.add(carePlan);
                orcIndex++;
            } catch (Exception e) {
                log.debug("Error processing ORC segment at index {}: {}", orcIndex, e.getMessage());
                break;
            }
        }

        return results.isEmpty() ? Collections.emptyList() : results;
    }
}
