package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import com.al.fhirhl7transformer.util.MappingConstants;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class InsuranceConverter implements SegmentConverter<DomainResource> {

    public String getSegmentName() {
        return "IN1/GT1";
    }

    @Override
    public List<DomainResource> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<DomainResource> resources = new ArrayList<>();

        // Process IN1 (Insurance) -> Coverage + Organization (Payor)
        processInsurance(terser, context, resources, bundle);

        // Process GT1 (Guarantor) -> RelatedPerson
        processGuarantor(terser, context, resources);

        return resources;
    }

    private void processInsurance(Terser terser, ConversionContext context, List<DomainResource> resources,
            Bundle bundle) {
        int in1Index = 0;
        while (in1Index < 50) { // Safety limit
            try {
                String in1Path = "/.IN1(" + in1Index + ")";
                String mainPathToUse = in1Path;
                boolean found = false;

                // Try root path first
                try {
                    if (terser.getSegment(in1Path) != null) {
                        found = true;
                    }
                } catch (Exception e) {
                    // Try INSURANCE group path
                    String insPath = "/.INSURANCE(" + in1Index + ")/IN1";
                    try {
                        if (terser.getSegment(insPath) != null) {
                            mainPathToUse = insPath;
                            found = true;
                        }
                    } catch (Exception ex) {
                        // Not found
                    }
                }

                if (!found)
                    break;

                String planId = terser.get(mainPathToUse + "-2-1"); // IN1-2 Insurance Plan ID
                String companyId = terser.get(mainPathToUse + "-3-1");

                // If both primary fields are missing but segment found, maybe skip?
                if (planId == null && companyId == null) {
                    in1Index++;
                    continue;
                }

                Coverage coverage = new Coverage();
                coverage.setId(UUID.randomUUID().toString());
                coverage.setStatus(Coverage.CoverageStatus.ACTIVE);
                String subId = terser.get(mainPathToUse + "-36-1");
                if (subId == null || subId.isEmpty()) {
                    subId = terser.get(mainPathToUse + "-36");
                }
                coverage.setSubscriberId(subId); // IN1-36 Policy Number

                // Beneficiary
                coverage.setBeneficiary(new Reference("Patient/" + context.getPatientId()));

                // Payor (Organization)
                if (companyId != null) {
                    Organization payor = new Organization();
                    payor.setId(UUID.randomUUID().toString());
                    payor.addIdentifier().setValue(companyId);
                    payor.setName(terser.get(mainPathToUse + "-4-1")); // IN1-4 Company Name

                    resources.add(payor);
                    coverage.addPayor(new Reference("Organization/" + payor.getId()));
                }

                // Type
                String planType = terser.get(mainPathToUse + "-47");
                if (planType != null) {
                    CodeableConcept type = new CodeableConcept();
                    type.addCoding().setSystem(MappingConstants.SYSTEM_COVERAGE_TYPE).setCode(planType);
                    coverage.setType(type);
                }

                resources.add(coverage);
                in1Index++;
            } catch (Exception e) {
                log.error("Error processing IN1 segment index {}", in1Index, e);
                break;
            }
        }
    }

    private void processGuarantor(Terser terser, ConversionContext context, List<DomainResource> resources) {
        int gt1Index = 0;
        while (true) {
            try {
                String gt1Path = "/.GT1(" + gt1Index + ")";
                try {
                    if (terser.getSegment(gt1Path) == null)
                        break;
                } catch (Exception ex) {
                    break;
                }
                String guarantorName = terser.get(gt1Path + "-3-1"); // Family Name

                if (guarantorName == null && gt1Index > 0)
                    break;
                if (guarantorName == null)
                    break;

                RelatedPerson rp = new RelatedPerson();
                rp.setId(UUID.randomUUID().toString());
                rp.setPatient(new Reference("Patient/" + context.getPatientId()));
                rp.setActive(true);

                // GT1-3 Name
                HumanName name = new HumanName();
                name.setFamily(guarantorName);
                String given = terser.get(gt1Path + "-3-2");
                if (given != null)
                    name.addGiven(given);
                rp.addName(name);

                // GT1-5 Address
                String addrLine = terser.get(gt1Path + "-5-1");
                if (addrLine != null) {
                    Address address = new Address();
                    address.addLine(addrLine);
                    address.setCity(terser.get(gt1Path + "-5-3"));
                    address.setState(terser.get(gt1Path + "-5-4"));
                    address.setPostalCode(terser.get(gt1Path + "-5-5"));
                    rp.addAddress(address);
                }

                // GT1-6 Phone
                String phone = terser.get(gt1Path + "-6-1");
                if (phone != null) {
                    rp.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(phone);
                }

                // GT1-11 Relationship
                String relCode = terser.get(gt1Path + "-11-1"); // Code
                if (relCode != null) {
                    CodeableConcept relation = new CodeableConcept();
                    relation.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0063").setCode(relCode);
                    rp.addRelationship(relation);
                }

                resources.add(rp);
                gt1Index++;
            } catch (Exception e) {
                log.error("Error processing GT1 segment index {}", gt1Index, e);
                break;
            }
        }
    }
}
