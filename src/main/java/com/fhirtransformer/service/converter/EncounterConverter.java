package com.fhirtransformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import com.fhirtransformer.util.DateTimeUtil;
import com.fhirtransformer.util.MappingConstants;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class EncounterConverter implements SegmentConverter<Encounter> {

    @Override
    public List<Encounter> convert(Terser terser, Bundle bundle, ConversionContext context) {
        try {
            String mainPathToUse = "/.PV1";
            boolean found = false;

            // Try root path first
            try {
                if (terser.getSegment(mainPathToUse) != null) {
                    found = true;
                }
            } catch (Exception e) {
                // Try VISIT group path (often used in OML/ORU)
                String visitPath = "/.VISIT/PV1";
                try {
                    if (terser.getSegment(visitPath) != null) {
                        mainPathToUse = visitPath;
                        found = true;
                    }
                } catch (Exception ex) {
                    // Not found
                }
            }

            if (!found) {
                return Collections.emptyList();
            }

            String checkPv1 = terser.get(mainPathToUse + "-1");
            log.info("Processing Encounter from PV1 segment at {}... PV1-1='{}'", mainPathToUse, checkPv1);

            // Strict check: if PV1-2 (Class) and PV1-19 (Visit Number) are both missing,
            // ignore this segment
            String patientClass = terser.get(mainPathToUse + "-2");
            String visitNum = terser.get(mainPathToUse + "-19");

            if ((patientClass == null || patientClass.isEmpty()) && (visitNum == null || visitNum.isEmpty())) {
                log.info(
                        "PV1 segment found but missing critical fields (PV1-2 Class and PV1-19 VisitNum). Skipping Encounter creation.");
                return Collections.emptyList();
            }

            Encounter encounter = new Encounter();
            encounter.setId(UUID.randomUUID().toString());
            context.setEncounterId(encounter.getId());

            // 1. identifier
            if (visitNum != null) {
                encounter.addIdentifier().setValue(visitNum);
            }

            // 2. status
            String trigger = context.getTriggerEvent();
            if ("A03".equals(trigger)) {
                encounter.setStatus(Encounter.EncounterStatus.FINISHED);
            } else if ("A01".equals(trigger) || "A04".equals(trigger) || "A05".equals(trigger)) {
                encounter.setStatus(Encounter.EncounterStatus.INPROGRESS);
            } else if ("A08".equals(trigger)) {
                encounter.setStatus(Encounter.EncounterStatus.INPROGRESS);
            } else {
                encounter.setStatus(Encounter.EncounterStatus.INPROGRESS); // Default
            }

            // 3. class
            // patientClass is already retrieved above
            if (patientClass == null || patientClass.isEmpty()) {
                patientClass = "O"; // Default to Outpatient
            }
            String fhirActCode = "AMB";
            if ("I".equalsIgnoreCase(patientClass))
                fhirActCode = "IMP";
            else if ("E".equalsIgnoreCase(patientClass))
                fhirActCode = "EMER";
            else if ("O".equalsIgnoreCase(patientClass))
                fhirActCode = "AMB";

            encounter.setClass_(new Coding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                    .setCode(fhirActCode));

            // 4. type (MUST BE BEFORE subject)
            String admType = terser.get(mainPathToUse + "-4");
            if (admType != null) {
                encounter.addType().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0007")
                        .setCode(admType);
            }
            // 5. serviceType (MUST BE BEFORE subject)
            String hospServ = terser.get(mainPathToUse + "-10");
            if (hospServ != null) {
                CodeableConcept sc = new CodeableConcept();
                sc.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0069").setCode(hospServ);
                encounter.setServiceType(sc);
            }

            // 6. subject
            if (context.getPatientId() != null) {
                encounter.setSubject(new Reference("Patient/" + context.getPatientId()));
            }

            // 7. participant
            processParticipants(terser, mainPathToUse, encounter);

            // 8. period
            String admitDateStr = terser.get(mainPathToUse + "-44");
            if (admitDateStr == null || admitDateStr.isEmpty()) {
                try {
                    admitDateStr = terser.get("/.EVN-2");
                } catch (Exception ex) {
                }
            }
            Date admitDate = null;
            if (admitDateStr != null && !admitDateStr.isEmpty()) {
                try {
                    admitDate = Date.from(DateTimeUtil.parseHl7DateTime(admitDateStr).toInstant());
                } catch (Exception e) {
                }
            }
            String dischargeDateStr = terser.get(mainPathToUse + "-45");
            Date dischargeDate = null;
            if (dischargeDateStr != null && !dischargeDateStr.isEmpty()) {
                try {
                    dischargeDate = Date.from(DateTimeUtil.parseHl7DateTime(dischargeDateStr).toInstant());
                } catch (Exception e) {
                }
            }
            if (admitDate != null || dischargeDate != null) {
                Period period = new Period();
                if (admitDate != null)
                    period.setStart(admitDate);
                if (dischargeDate != null)
                    period.setEnd(dischargeDate);
                encounter.setPeriod(period);
            }

            // 9. reasonCode (MUST BE BEFORE hospitalization)
            try {
                String pv2Path = "/.PV2";
                try {
                    if (terser.getSegment(pv2Path) == null && mainPathToUse.contains("VISIT")) {
                        pv2Path = "/.VISIT/PV2";
                    }
                } catch (Exception e) {
                }

                String reason = terser.get(pv2Path + "-3-2");
                if (reason == null || reason.isEmpty())
                    reason = terser.get(pv2Path + "-3-1");
                if (reason == null || reason.isEmpty())
                    reason = terser.get(pv2Path + "-3");
                if (reason != null && !reason.isEmpty()) {
                    reason = reason.replace("^", "").trim();
                    encounter.addReasonCode().setText(reason);
                }
            } catch (Exception ex) {
            }

            // 10. hospitalization (MUST BE BEFORE location)
            String dischargeDisp = terser.get(mainPathToUse + "-36");
            if (dischargeDisp != null && !dischargeDisp.isEmpty()) {
                encounter.setHospitalization(new Encounter.EncounterHospitalizationComponent());
                encounter.getHospitalization().setDischargeDisposition(new CodeableConcept().addCoding(
                        new Coding().setSystem(MappingConstants.SYSTEM_V2_DISCHARGE_DISPOSITION)
                                .setCode(dischargeDisp)));
            }

            // 11. location
            String pointOfCare = terser.get(mainPathToUse + "-3-1");
            String room = terser.get(mainPathToUse + "-3-2");
            String bed = terser.get(mainPathToUse + "-3-3");
            String facilityValue = terser.get(mainPathToUse + "-3-4");

            if (pointOfCare != null || room != null || bed != null) {
                Location location = new Location();
                location.setId(UUID.randomUUID().toString());
                location.setMode(Location.LocationMode.INSTANCE);
                location.setStatus(Location.LocationStatus.ACTIVE);

                StringBuilder locName = new StringBuilder();
                if (pointOfCare != null)
                    locName.append(pointOfCare);
                if (room != null)
                    locName.append(" ").append(room);
                if (bed != null)
                    locName.append("-").append(bed);
                location.setName(locName.toString().trim());

                if (facilityValue != null) {
                    location.setDescription(location.getName() + " (" + facilityValue + ")");
                }

                if (bed != null) {
                    location.setPhysicalType(new CodeableConcept().addCoding(
                            new Coding().setSystem("http://terminology.hl7.org/CodeSystem/location-physical-type")
                                    .setCode("bd").setDisplay("Bed")));
                } else if (room != null) {
                    location.setPhysicalType(new CodeableConcept().addCoding(
                            new Coding().setSystem("http://terminology.hl7.org/CodeSystem/location-physical-type")
                                    .setCode("ro").setDisplay("Room")));
                }

                bundle.addEntry().setResource(location).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Location");
                encounter.addLocation().setLocation(new Reference("Location/" + location.getId())
                        .setDisplay(locName.toString().trim()));
            }

            return Collections.singletonList(encounter);

        } catch (Exception e) {
            log.error("Error converting Encounter segment", e);
            return Collections.emptyList();
        }
    }

    private void processParticipants(Terser terser, String mainPathToUse, Encounter encounter) {
        // PV1-7 Attending Doctor (Repeating)
        mapDoctorRep(terser, mainPathToUse + "-7", "ATND", "attender", encounter);
        // PV1-8 Referring Doctor (Repeating)
        mapDoctorRep(terser, mainPathToUse + "-8", "REFR", "referrer", encounter);
        // PV1-9 Consulting Doctor (Repeating)
        mapDoctorRep(terser, mainPathToUse + "-9", "CON", "consultant", encounter);
    }

    private void mapDoctorRep(Terser terser, String baseField, String roleCode, String roleDisplay,
            Encounter encounter) {
        int index = 0;
        while (index < 10) { // Safety limit
            try {
                String path = baseField + "(" + index + ")";
                String docId = terser.get(path + "-1");
                String docFamily = terser.get(path + "-2");
                if (docId == null && docFamily == null) {
                    // Try without index for first repetition if index 0 fails
                    if (index == 0) {
                        path = baseField;
                        docId = terser.get(path + "-1");
                        docFamily = terser.get(path + "-2");
                        if (docId == null && docFamily == null)
                            break;
                    } else {
                        break;
                    }
                }

                Encounter.EncounterParticipantComponent participant = encounter.addParticipant();
                participant.addType().addCoding()
                        .setSystem(MappingConstants.SYSTEM_V2_PARTICIPATION_TYPE).setCode(roleCode)
                        .setDisplay(roleDisplay);

                HumanName docName = new HumanName();
                if (docFamily != null)
                    docName.setFamily(docFamily);
                String docGiven = terser.get(path + "-3");
                if (docGiven != null)
                    docName.addGiven(docGiven);

                Reference docRef = new Reference();
                if (docId != null)
                    docRef.setReference("Practitioner/" + docId);
                docRef.setDisplay(docName.isEmpty() ? "Unknown Doctor" : docName.getNameAsSingleString());
                participant.setIndividual(docRef);

                index++;
            } catch (Exception e) {
                break;
            }
        }
    }
}
