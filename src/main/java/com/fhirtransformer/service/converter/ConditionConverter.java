package com.fhirtransformer.service.converter;

import com.fhirtransformer.util.MappingConstants;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ConditionConverter implements SegmentConverter<Condition> {

    public String getSegmentName() {
        return "DG1";
    }

    @Override
    public List<Condition> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Condition> conditions = new ArrayList<>();
        int dg1Index = 0;

        while (true) {
            if (dg1Index > 50) {
                log.warn("Max DG1 segments reached for Condition");
                break;
            }
            String dg1Path = "/.DG1(" + dg1Index + ")";
            String mainPathToUse = dg1Path;
            boolean found = false;

            // Try generic/root path first
            try {
                if (terser.getSegment(dg1Path) != null) {
                    found = true;
                }
            } catch (Exception e) {
                // Not found at root, try PROBLEM group (common in some ADT structures)
                String adtPath = "/.PROBLEM(" + dg1Index + ")/DG1";
                try {
                    if (terser.getSegment(adtPath) != null) {
                        mainPathToUse = adtPath;
                        found = true;
                    }
                } catch (Exception ex) {
                    // Not found
                }
            }

            if (!found) {
                break;
            }

            try {
                // Check if segment exists - check for presence of DG1-1
                String dg1Id = terser.get(mainPathToUse + "-1");
                if (dg1Id == null || dg1Id.isEmpty()) {
                    break;
                }

                String diagnosisCode = terser.get(mainPathToUse + "-3-1");
                if (diagnosisCode == null)
                    break;

                Condition condition = new Condition();
                condition.setId(UUID.randomUUID().toString());
                condition.setSubject(new Reference("Patient/" + context.getPatientId()));
                if (context.getEncounterId() != null) {
                    condition.setEncounter(new Reference("Encounter/" + context.getEncounterId()));
                }

                // Verification Status: Confirmed
                condition.setVerificationStatus(new CodeableConcept().addCoding(new Coding()
                        .setSystem(MappingConstants.SYSTEM_CONDITION_VER_STATUS)
                        .setCode(MappingConstants.CODE_CONFIRMED)));

                // Clinical Status: Active
                condition.setClinicalStatus(new CodeableConcept().addCoding(new Coding()
                        .setSystem(MappingConstants.SYSTEM_CONDITION_CLINICAL)
                        .setCode(MappingConstants.CODE_ACTIVE)));

                // Diagnosis Code and Name
                String diagnosisName = terser.get(mainPathToUse + "-3-2");
                CodeableConcept code = new CodeableConcept();
                code.addCoding().setSystem("http://hl7.org/fhir/sid/icd-10").setCode(diagnosisCode)
                        .setDisplay(diagnosisName);
                if (diagnosisName != null) {
                    code.setText(diagnosisName);
                }
                condition.setCode(code);

                // Category (DG1-6) - e.g., Admitting, Discharge, etc.
                String type = terser.get(mainPathToUse + "-6");
                if (type != null && !type.isEmpty()) {
                    CodeableConcept category = new CodeableConcept();
                    String display = type;
                    String code_str = "encounter-diagnosis";

                    // Basic mapping of DG1-6 (Diagnosis Type) to FHIR category
                    if ("A".equalsIgnoreCase(type))
                        display = "Admitting";
                    else if ("W".equalsIgnoreCase(type))
                        display = "Working";
                    else if ("F".equalsIgnoreCase(type))
                        display = "Final";

                    category.addCoding().setSystem(MappingConstants.SYSTEM_CONDITION_CATEGORY)
                            .setCode(code_str).setDisplay(display);
                    condition.addCategory(category);
                }

                // CHECK FOR NTE SEGMENTS (Notes)
                try {
                    Segment dg1Segment = terser.getSegment(mainPathToUse);
                    if (dg1Segment != null) {
                        Structure parent = dg1Segment.getParent();
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
                                                condition.addNote(annotation);
                                                log.debug("Mapped NTE-3 to Condition.note: {}", commentText);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing DG1 NTEs: {}", e.getMessage());
                }

                conditions.add(condition);
                dg1Index++;
            } catch (Exception e) {
                log.error("Error converting Condition at {}: {}", mainPathToUse, e.getMessage());
                dg1Index++;
            }
        }
        return conditions;
    }
}
