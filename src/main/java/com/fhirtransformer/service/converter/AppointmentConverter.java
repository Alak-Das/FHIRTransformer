package com.fhirtransformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import com.fhirtransformer.util.DateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class AppointmentConverter implements SegmentConverter<Appointment> {

    @Override
    public List<Appointment> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Appointment> appointments = new ArrayList<>();
        int index = 0;
        while (index < 50) { // Safety limit
            try {
                String schPath = "/.SCH(" + index + ")";
                String mainPathToUse = schPath;
                boolean found = false;

                // Try root path first
                try {
                    if (terser.getSegment(schPath) != null) {
                        found = true;
                    }
                } catch (Exception e) {
                    // Try APPOINTMENT group path
                    String apptPath = "/.APPOINTMENT(" + index + ")/SCH";
                    try {
                        if (terser.getSegment(apptPath) != null) {
                            mainPathToUse = apptPath;
                            found = true;
                        }
                    } catch (Exception ex) {
                        // Not found
                    }
                }

                if (!found)
                    break;

                String fillerId = terser.get(mainPathToUse + "-2");
                if (fillerId == null) {
                    index++;
                    continue;
                }

                Appointment appointment = new Appointment();
                appointment.setId(UUID.randomUUID().toString());

                // Status Mapping (SCH-25)
                String statusSpecId = terser.get(mainPathToUse + "-25");
                if ("Blocked".equalsIgnoreCase(statusSpecId))
                    appointment.setStatus(Appointment.AppointmentStatus.BOOKED);
                else if ("Cancelled".equalsIgnoreCase(statusSpecId))
                    appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
                else if ("Overbooked".equalsIgnoreCase(statusSpecId))
                    appointment.setStatus(Appointment.AppointmentStatus.BOOKED);
                else
                    appointment.setStatus(Appointment.AppointmentStatus.BOOKED);

                // Priority (SCH-7)
                String priority = terser.get(mainPathToUse + "-7-1");
                if (priority != null) {
                    try {
                        appointment.setPriority(Integer.parseInt(priority));
                    } catch (NumberFormatException e) {
                        log.debug("Failed to parse Appointment priority: {}", priority);
                    }
                }

                // Identifiers
                appointment.addIdentifier().setValue(fillerId);
                String placerId = terser.get(mainPathToUse + "-1");
                if (placerId != null)
                    appointment.addIdentifier().setValue(placerId);

                // Reason
                String reasonStr = terser.get(mainPathToUse + "-6-2");
                if (reasonStr != null) {
                    appointment.addReasonCode().setText(reasonStr);
                }

                // Schedule Timing
                String start = terser.get(mainPathToUse + "-11-4");
                if (start != null) {
                    DateTimeType dateType = DateTimeUtil.hl7DateTimeToFhir(start);
                    if (dateType != null)
                        appointment.setStart(dateType.getValue());
                }

                if (context.getPatientId() != null) {
                    appointment.addParticipant()
                            .setActor(new Reference("Patient/" + context.getPatientId()))
                            .setStatus(Appointment.ParticipationStatus.ACCEPTED);
                }

                // Link to Encounter if available
                if (context.getEncounterId() != null) {
                    // Appointment doesn't have a direct 'encounter' field in R4 like Observation
                    // does.
                    // It can be linked via 'Appointment.appointmentType' or extensions,
                    // but standard practice for ADT link is often through the Encounter itself
                    // pointing to Appointment.
                }

                appointments.add(appointment);
                index++;
            } catch (Exception e) {
                log.debug("Error processing Appointment index {}: {}", index, e.getMessage());
                break;
            }
        }
        return appointments;
    }
}
