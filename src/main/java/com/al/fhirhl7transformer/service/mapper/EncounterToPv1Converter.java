package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.segment.PV1;
import ca.uhn.hl7v2.model.v25.segment.PV2;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

@Component
public class EncounterToPv1Converter implements FhirToHl7Converter<Encounter> {

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Encounter;
    }

    @Override
    public void convert(Encounter encounter, Message message, Terser terser) throws HL7Exception {
        // PV1-1 Set ID
        terser.set("PV1-1", "1");

        PV1 pv1 = (PV1) terser.getSegment("PV1");

        // PV1-19 Visit Number
        if (encounter.hasIdentifier()) {
            pv1.getVisitNumber().getIDNumber().setValue(encounter.getIdentifierFirstRep().getValue());
        }

        // PV1-2 Patient Class
        if (encounter.hasClass_()) {
            pv1.getPatientClass().setValue(encounter.getClass_().getCode());
        }

        // PV1-3 Assigned Patient Location
        if (encounter.hasLocation()) {
            String loc = encounter.getLocationFirstRep().getLocation().getDisplay();
            if (loc != null) {
                pv1.getAssignedPatientLocation().getPointOfCare().setValue(loc);
            }
        }

        // PV1-4 Admission Type
        if (encounter.hasType() && !encounter.getType().isEmpty()) {
            pv1.getAdmissionType().setValue(encounter.getType().get(0).getCodingFirstRep().getCode());
        }

        // PV1-7/8/9 doctors
        if (encounter.hasParticipant()) {
            int attendIdx = 0;
            int referIdx = 0;
            int consultIdx = 0;
            for (Encounter.EncounterParticipantComponent participant : encounter.getParticipant()) {
                if (participant.hasType() && participant.getTypeFirstRep().hasCoding()) {
                    String type = participant.getTypeFirstRep().getCodingFirstRep().getCode();
                    String docName = participant.hasIndividual() && participant.getIndividual().hasDisplay()
                            ? participant.getIndividual().getDisplay()
                            : "Unknown Doc";

                    if ("ATND".equals(type)) {
                        pv1.getAttendingDoctor(attendIdx++).getFamilyName().getSurname().setValue(docName);
                    } else if ("REFR".equals(type)) {
                        pv1.getReferringDoctor(referIdx++).getFamilyName().getSurname().setValue(docName);
                    } else if ("CON".equals(type)) {
                        pv1.getConsultingDoctor(consultIdx++).getFamilyName().getSurname().setValue(docName);
                    }
                }
            }
        }

        // PV1-10 Hospital Service
        if (encounter.hasServiceType()) {
            pv1.getHospitalService().setValue(encounter.getServiceType().getCodingFirstRep().getCode());
        }

        // PV1-44 Admit Date
        if (encounter.hasPeriod() && encounter.getPeriod().hasStart()) {
            pv1.getAdmitDateTime().getTime()
                    .setValue(new SimpleDateFormat("yyyyMMddHHmm").format(encounter.getPeriod().getStart()));
        }

        // PV1-45 Discharge Date
        if (encounter.hasPeriod() && encounter.getPeriod().hasEnd()) {
            pv1.getDischargeDateTime(0).getTime()
                    .setValue(new SimpleDateFormat("yyyyMMddHHmm").format(encounter.getPeriod().getEnd()));
        }

        // Ensure PV2 exists and populate it
        // Note: PV2 is often optional, but if we have data we map it.
        // Terser getSegment might not create it if it doesn't exist?
        // We will assume standard behavior where getters in HAPI create if missing or
        // use Terser.
        // Using explicit casting as in original code:
        // ca.uhn.hl7v2.model.v25.segment.PV2 pv2 = adt.getPV2();
        // But here we rely on Terser or direct segment access.
        // Let's use Terser to be safe/generic or use getSegment.

        PV2 pv2 = (PV2) terser.getSegment("PV2");

        // PV2-3 Admit Reason
        if (encounter.hasReasonCode()) {
            CodeableConcept reasonCode = encounter.getReasonCodeFirstRep();
            String text = reasonCode.hasText() ? reasonCode.getText() : null;
            Coding reason = reasonCode.getCodingFirstRep();
            String val = (reason != null && reason.hasCode()) ? reason.getCode() : text;
            if (val != null) {
                pv2.getAdmitReason().getIdentifier().setValue(val);
                pv2.getAdmitReason().getText().setValue(val);
                // Also set in PV1-18 as a fallback for some legacy systems/tests
                pv1.getPatientType().setValue(val);
            }
        }
    }
}
