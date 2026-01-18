package com.fhirtransformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Group;
import com.fhirtransformer.util.DateTimeUtil;
import com.fhirtransformer.util.MappingConstants;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ProcedureConverter implements SegmentConverter<Procedure> {

    public String getSegmentName() {
        return "PR1";
    }

    @Override
    public List<Procedure> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Procedure> procedures = new ArrayList<>();
        int pr1Index = 0;

        while (true) {
            String pr1Path = "/.PR1(" + pr1Index + ")";
            String mainPathToUse = pr1Path;
            boolean found = false;

            // Try generic/root path first
            try {
                if (terser.getSegment(pr1Path) != null) {
                    found = true;
                }
            } catch (Exception e) {
                // Not found at root, try ADT structure (PROCEDURE group)
                String adtPath = "/.PROCEDURE(" + pr1Index + ")/PR1";
                try {
                    if (terser.getSegment(adtPath) != null) {
                        mainPathToUse = adtPath;
                        found = true;
                    }
                } catch (Exception ex) {
                    // Not found in either location
                }
            }

            if (!found) {
                break;
            }

            try {
                // PR1-3 Procedure Code
                String codeVal = terser.get(mainPathToUse + "-3-1");

                if (codeVal == null) {
                    // Segment exists but code is missing. Skip this segment or break?
                    // To be safe and avoid infinite loops if structure is weird, we'll continue but
                    // log warning.
                    // However, standard break approach assumes if validation fails, stop.
                    // But here found=true means segment exists.
                    log.warn("PR1 segment found at {} but missing code (field 3-1). Breaking loop.", mainPathToUse);
                    break;
                }

                Procedure procedure = new Procedure();
                procedure.setId(UUID.randomUUID().toString());
                procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
                procedure.setSubject(new Reference("Patient/" + context.getPatientId()));
                if (context.getEncounterId() != null) {
                    procedure.setEncounter(new Reference("Encounter/" + context.getEncounterId()));
                }

                // Procedure Code details
                String codeText = terser.get(mainPathToUse + "-3-2");
                CodeableConcept code = new CodeableConcept();
                code.addCoding().setSystem(MappingConstants.SYSTEM_CPT).setCode(codeVal).setDisplay(codeText);
                procedure.setCode(code);

                // PR1-5 Procedure Date/Time
                String procDate = terser.get(mainPathToUse + "-5");
                if (procDate != null && !procDate.isEmpty()) {
                    try {
                        procedure.setPerformed(DateTimeUtil.hl7DateTimeToFhir(procDate));
                    } catch (Exception e) {
                        log.warn("Failed to parse procedure date: {}", procDate);
                    }
                }

                // PR1-11 Surgeon -> Performer
                String surgeonId = terser.get(mainPathToUse + "-11-1");
                String surgeonName = terser.get(mainPathToUse + "-11-2");
                if (surgeonId != null || surgeonName != null) {
                    Procedure.ProcedurePerformerComponent performer = new Procedure.ProcedurePerformerComponent();
                    Reference actor = new Reference();
                    if (surgeonId != null)
                        actor.setReference("Practitioner/" + surgeonId);
                    if (surgeonName != null)
                        actor.setDisplay(surgeonName);
                    performer.setActor(actor);
                    performer.setFunction(new CodeableConcept().addCoding(new Coding()
                            .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType")
                            .setCode("PPRF").setDisplay("Primary Performer")));
                    procedure.addPerformer(performer);
                }

                // PR1-12 Anesthesiologist -> Performer
                String anesId = terser.get(mainPathToUse + "-12-1");
                String anesName = terser.get(mainPathToUse + "-12-2");
                if (anesId != null || anesName != null) {
                    Procedure.ProcedurePerformerComponent performer = new Procedure.ProcedurePerformerComponent();
                    Reference actor = new Reference();
                    if (anesId != null)
                        actor.setReference("Practitioner/" + anesId);
                    if (anesName != null)
                        actor.setDisplay(anesName);
                    performer.setActor(actor);
                    performer.setFunction(new CodeableConcept().addCoding(new Coding()
                            .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType")
                            .setCode("SPRF").setDisplay("Secondary Performer")));
                    procedure.addPerformer(performer);
                }

                // CHECK FOR NTE SEGMENTS (Notes)
                try {
                    Segment pr1Segment = terser.getSegment(mainPathToUse);
                    if (pr1Segment != null) {
                        Structure parent = pr1Segment.getParent();
                        if (parent instanceof Group) {
                            Group group = (Group) parent;
                            // Safe check if NTE exists in this group's definition
                            boolean hasNte = false;
                            try {
                                String[] names = group.getNames();
                                for (String name : names) {
                                    if ("NTE".equals(name)) {
                                        hasNte = true;
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore
                            }

                            if (hasNte) {
                                Structure[] ntes = group.getAll("NTE");
                                for (Structure nteStruct : ntes) {
                                    if (nteStruct instanceof Segment) {
                                        Segment nte = (Segment) nteStruct;
                                        // NTE-3: Comment
                                        ca.uhn.hl7v2.model.Type[] comments = nte.getField(3);
                                        for (ca.uhn.hl7v2.model.Type c : comments) {
                                            String commentText = c.toString();
                                            if (!commentText.isEmpty()) {
                                                Annotation annotation = new Annotation();
                                                annotation.setText(commentText);
                                                procedure.addNote(annotation);
                                                log.debug("Mapped NTE-3 to Procedure.note: {}", commentText);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing PR1 NTEs: {}", e.getMessage());
                }

                procedures.add(procedure);
                pr1Index++;
            } catch (Exception e) {
                log.error("Error processing PR1 segment index {}", pr1Index, e);
                break;
            }
        }
        return procedures;
    }
}
