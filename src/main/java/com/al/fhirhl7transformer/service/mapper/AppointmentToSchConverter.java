package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Converts FHIR Appointment to HL7 SCH (Scheduling Activity Information)
 * segment.
 */
@Component
public class AppointmentToSchConverter implements FhirToHl7Converter<Appointment> {

    private static final SimpleDateFormat HL7_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");
    private int schIndex = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Appointment;
    }

    @Override
    public void convert(Appointment appointment, Message message, Terser terser) throws HL7Exception {
        String schPath = "/.SCH(" + schIndex + ")";

        // SCH-1 Placer Appointment ID
        // SCH-2 Filler Appointment ID
        if (appointment.hasIdentifier()) {
            int placerSet = 0;
            int fillerSet = 0;
            for (Identifier id : appointment.getIdentifier()) {
                if (id.hasType() && id.getType().hasCoding()) {
                    String typeCode = id.getType().getCodingFirstRep().getCode();
                    if ("PLAC".equals(typeCode) && placerSet == 0) {
                        terser.set(schPath + "-1", id.getValue());
                        placerSet = 1;
                    } else if ("FILL".equals(typeCode) && fillerSet == 0) {
                        terser.set(schPath + "-2", id.getValue());
                        fillerSet = 1;
                    }
                } else if (id.hasValue()) {
                    // Default: first identifier to SCH-2 (filler)
                    if (fillerSet == 0) {
                        terser.set(schPath + "-2", id.getValue());
                        fillerSet = 1;
                    } else if (placerSet == 0) {
                        terser.set(schPath + "-1", id.getValue());
                        placerSet = 1;
                    }
                }
            }
        }

        // SCH-6 Event Reason (from reasonCode)
        if (appointment.hasReasonCode()) {
            CodeableConcept reason = appointment.getReasonCodeFirstRep();
            if (reason.hasCoding()) {
                Coding coding = reason.getCodingFirstRep();
                terser.set(schPath + "-6-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(schPath + "-6-2", coding.getDisplay());
                }
            } else if (reason.hasText()) {
                terser.set(schPath + "-6-2", reason.getText());
            }
        }

        // SCH-7 Appointment Reason
        if (appointment.hasAppointmentType()) {
            CodeableConcept type = appointment.getAppointmentType();
            if (type.hasCoding()) {
                terser.set(schPath + "-7-1", type.getCodingFirstRep().getCode());
            }
        }

        // SCH-8 Appointment Type
        if (appointment.hasServiceType()) {
            CodeableConcept serviceType = appointment.getServiceTypeFirstRep();
            if (serviceType.hasCoding()) {
                terser.set(schPath + "-8-1", serviceType.getCodingFirstRep().getCode());
                if (serviceType.getCodingFirstRep().hasDisplay()) {
                    terser.set(schPath + "-8-2", serviceType.getCodingFirstRep().getDisplay());
                }
            }
        }

        // SCH-9 Appointment Duration
        if (appointment.hasMinutesDuration()) {
            terser.set(schPath + "-9", String.valueOf(appointment.getMinutesDuration()));
            terser.set(schPath + "-10", "min");
        }

        // SCH-11 Appointment Timing Quantity (TQ)
        // SCH-11-4 Start Date/Time
        if (appointment.hasStart()) {
            terser.set(schPath + "-11-4", HL7_DATE_FORMAT.format(appointment.getStart()));
        }
        // SCH-11-5 End Date/Time
        if (appointment.hasEnd()) {
            terser.set(schPath + "-11-5", HL7_DATE_FORMAT.format(appointment.getEnd()));
        }

        // SCH-12 Placer Contact Person (from participants)
        // SCH-16 Filler Contact Person
        if (appointment.hasParticipant()) {
            for (Appointment.AppointmentParticipantComponent participant : appointment.getParticipant()) {
                if (participant.hasActor()) {
                    Reference actor = participant.getActor();
                    String ref = actor.getReference();

                    if (ref != null && ref.contains("Patient")) {
                        // Patient participant - link via basedOn or extension
                    } else if (ref != null && ref.contains("Practitioner")) {
                        // Practitioner - SCH-12 or SCH-16
                        String practId = ref.substring(ref.lastIndexOf("/") + 1);
                        terser.set(schPath + "-16-1", practId);
                        if (actor.hasDisplay()) {
                            terser.set(schPath + "-16-2", actor.getDisplay());
                        }
                    } else if (ref != null && ref.contains("Location")) {
                        // Location - SCH-17
                        String locId = ref.substring(ref.lastIndexOf("/") + 1);
                        terser.set(schPath + "-17-1", locId);
                        if (actor.hasDisplay()) {
                            terser.set(schPath + "-17-2", actor.getDisplay());
                        }
                    }
                }
            }
        }

        // SCH-20 Entered By Person
        // SCH-25 Filler Status Code (from status)
        if (appointment.hasStatus()) {
            String status = appointment.getStatus().toCode();
            switch (status) {
                case "proposed":
                    terser.set(schPath + "-25", "Pending");
                    break;
                case "booked":
                    terser.set(schPath + "-25", "Booked");
                    break;
                case "arrived":
                    terser.set(schPath + "-25", "Started");
                    break;
                case "fulfilled":
                    terser.set(schPath + "-25", "Complete");
                    break;
                case "cancelled":
                    terser.set(schPath + "-25", "Cancelled");
                    break;
                case "noshow":
                    terser.set(schPath + "-25", "Noshow");
                    break;
                default:
                    terser.set(schPath + "-25", status);
            }
        }

        // SCH-26 Placer Order Number (links to ServiceRequest)
        if (appointment.hasBasedOn()) {
            Reference basedOn = appointment.getBasedOnFirstRep();
            if (basedOn.hasReference()) {
                String ref = basedOn.getReference();
                if (ref.contains("/")) {
                    terser.set(schPath + "-26", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
        }

        schIndex++;
    }
}
