package com.fhirtransformer.service.converter;

import ca.uhn.hl7v2.model.Message;
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
            String checkPv1 = terser.get("/.PV1-1");
            log.info("Checking for PV1 segment... PV1-1='{}'", checkPv1);

            if (checkPv1 == null) {
                return Collections.emptyList();
            }

            Encounter encounter = new Encounter();
            encounter.setId(UUID.randomUUID().toString());
            encounter.setStatus(Encounter.EncounterStatus.FINISHED);

            if (context.getPatientId() != null) {
                encounter.setSubject(new Reference("Patient/" + context.getPatientId()));
            }

            String visitNum = terser.get("/.PV1-19");
            log.debug("Processing Encounter for Patient {}: Visit Num='{}'", context.getPatientId(), visitNum);
            if (visitNum != null) {
                encounter.addIdentifier().setValue(visitNum);
            }

            // PV1-3 Assigned Patient Location
            String pointOfCare = terser.get("/.PV1-3-1");
            String room = terser.get("/.PV1-3-2");
            String bed = terser.get("/.PV1-3-3");
            if (pointOfCare != null || room != null || bed != null) {
                StringBuilder locName = new StringBuilder();
                if (pointOfCare != null)
                    locName.append(pointOfCare);
                if (room != null)
                    locName.append(" ").append(room);
                if (bed != null)
                    locName.append("-").append(bed);
                encounter.addLocation().setLocation(new Reference().setDisplay(locName.toString().trim()));
            }

            String patientClass = terser.get("/.PV1-2");
            if (patientClass != null) {
                encounter.setClass_(new Coding()
                        .setSystem(MappingConstants.SYSTEM_V2_ACT_CODE).setCode(patientClass));
            }

            // PV1-4 Admission Type
            String admType = terser.get("/.PV1-4");
            if (admType != null) {
                encounter.addType().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0007")
                        .setCode(admType);
            }

            // PV1-10 Hospital Service
            String hospServ = terser.get("/.PV1-10");
            if (hospServ != null) {
                CodeableConcept sc = new CodeableConcept();
                sc.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0069").setCode(hospServ);
                encounter.setServiceType(sc);
            }

            // Map multiple participants (Attending, Referring, Consulting)
            processParticipants(terser, encounter);

            String admitDateStr = terser.get("/.PV1-44");
            if (admitDateStr == null || admitDateStr.isEmpty()) {
                admitDateStr = terser.get("/.EVN-2");
            }

            Date admitDate = null;
            if (admitDateStr != null && !admitDateStr.isEmpty()) {
                try {
                    admitDate = Date.from(DateTimeUtil.parseHl7DateTime(admitDateStr).toInstant());
                } catch (Exception e) {
                    log.warn("Failed to parse admit date: {}", admitDateStr);
                }
            }

            String dischargeDateStr = terser.get("/.PV1-45");
            Date dischargeDate = null;
            if (dischargeDateStr != null && !dischargeDateStr.isEmpty()) {
                try {
                    dischargeDate = Date.from(DateTimeUtil.parseHl7DateTime(dischargeDateStr).toInstant());
                } catch (Exception e) {
                    log.warn("Failed to parse discharge date: {}", dischargeDateStr);
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

            // PV2-3 Admit Reason
            try {
                String reason = terser.get("/.PV2-3-2"); // Text
                if (reason == null || reason.isEmpty()) {
                    reason = terser.get("/.PV2-3-1"); // Fallback to Identifier
                }
                if (reason == null || reason.isEmpty()) {
                    reason = terser.get("/.PV2-3"); // Fallback to whole field
                }
                log.debug("Processing PV2-3 (Admit Reason): original='{}', cleaned='{}'",
                        reason, (reason != null ? reason.replace("^", "").trim() : "null"));
                if (reason != null && !reason.isEmpty()) {
                    // Cleanup carets if whole field returned
                    reason = reason.replace("^", "").trim();
                    encounter.addReasonCode().setText(reason);
                }
            } catch (Exception ex) {
                // PV2 might not exist
            }

            return Collections.singletonList(encounter);

        } catch (Exception e) {
            log.error("Error converting Encounter segment", e);
            throw new RuntimeException("Encounter conversion failed", e);
        }
    }

    private void processParticipants(Terser terser, Encounter encounter) {
        // PV1-7 Attending Doctor (Repeating)
        mapDoctorRep(terser, "/.PV1-7", "ATND", "attender", encounter);
        // PV1-8 Referring Doctor (Repeating)
        mapDoctorRep(terser, "/.PV1-8", "REFR", "referrer", encounter);
        // PV1-9 Consulting Doctor (Repeating)
        mapDoctorRep(terser, "/.PV1-9", "CON", "consultant", encounter);
    }

    private void mapDoctorRep(Terser terser, String baseField, String roleCode, String roleDisplay,
            Encounter encounter) {
        int index = 0;
        while (true) {
            try {
                String path = baseField + "(" + index + ")";
                String docId = terser.get(path + "-1");
                String docFamily = terser.get(path + "-2");
                if (docId == null && docFamily == null)
                    break;

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
