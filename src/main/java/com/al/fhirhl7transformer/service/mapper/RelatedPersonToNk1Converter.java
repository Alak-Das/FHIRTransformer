package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

/**
 * Converts FHIR RelatedPerson to HL7 NK1 (Next of Kin) segment.
 */
@Component
public class RelatedPersonToNk1Converter implements FhirToHl7Converter<RelatedPerson> {

    private int nk1Index = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof RelatedPerson;
    }

    @Override
    public void convert(RelatedPerson relatedPerson, Message message, Terser terser) throws HL7Exception {
        String nk1Path = "/.NK1(" + nk1Index + ")";

        // NK1-1 Set ID
        terser.set(nk1Path + "-1", String.valueOf(nk1Index + 1));

        // NK1-2 Name
        if (relatedPerson.hasName()) {
            HumanName name = relatedPerson.getNameFirstRep();
            if (name.hasFamily()) {
                terser.set(nk1Path + "-2-1", name.getFamily());
            }
            if (name.hasGiven()) {
                terser.set(nk1Path + "-2-2", name.getGivenAsSingleString());
            }
            if (name.hasPrefix()) {
                terser.set(nk1Path + "-2-5", name.getPrefixAsSingleString());
            }
            if (name.hasSuffix()) {
                terser.set(nk1Path + "-2-4", name.getSuffixAsSingleString());
            }
        }

        // NK1-3 Relationship
        if (relatedPerson.hasRelationship()) {
            CodeableConcept relationship = relatedPerson.getRelationshipFirstRep();
            if (relationship.hasCoding()) {
                Coding coding = relationship.getCodingFirstRep();
                terser.set(nk1Path + "-3-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(nk1Path + "-3-2", coding.getDisplay());
                }
                if (coding.hasSystem()) {
                    terser.set(nk1Path + "-3-3", coding.getSystem());
                }
            } else if (relationship.hasText()) {
                terser.set(nk1Path + "-3-2", relationship.getText());
            }
        }

        // NK1-4 Address
        if (relatedPerson.hasAddress()) {
            Address address = relatedPerson.getAddressFirstRep();
            if (address.hasLine()) {
                terser.set(nk1Path + "-4-1", address.getLine().get(0).getValue());
                if (address.getLine().size() > 1) {
                    terser.set(nk1Path + "-4-2", address.getLine().get(1).getValue());
                }
            }
            if (address.hasCity()) {
                terser.set(nk1Path + "-4-3", address.getCity());
            }
            if (address.hasState()) {
                terser.set(nk1Path + "-4-4", address.getState());
            }
            if (address.hasPostalCode()) {
                terser.set(nk1Path + "-4-5", address.getPostalCode());
            }
            if (address.hasCountry()) {
                terser.set(nk1Path + "-4-6", address.getCountry());
            }
        }

        // NK1-5 Phone Number (Home)
        // NK1-6 Business Phone Number
        if (relatedPerson.hasTelecom()) {
            int homeIdx = 0;
            int workIdx = 0;
            for (ContactPoint telecom : relatedPerson.getTelecom()) {
                if (telecom.hasValue()) {
                    if (ContactPoint.ContactPointUse.WORK.equals(telecom.getUse())) {
                        terser.set(nk1Path + "-6(" + workIdx + ")-1", telecom.getValue());
                        workIdx++;
                    } else {
                        terser.set(nk1Path + "-5(" + homeIdx + ")-1", telecom.getValue());
                        homeIdx++;
                    }
                }
            }
        }

        // NK1-7 Contact Role
        // NK1-8 Start Date
        if (relatedPerson.hasPeriod()) {
            Period period = relatedPerson.getPeriod();
            if (period.hasStart()) {
                terser.set(nk1Path + "-8", new java.text.SimpleDateFormat("yyyyMMdd")
                        .format(period.getStart()));
            }
            // NK1-9 End Date
            if (period.hasEnd()) {
                terser.set(nk1Path + "-9", new java.text.SimpleDateFormat("yyyyMMdd")
                        .format(period.getEnd()));
            }
        }

        // NK1-13 Organization Name
        // NK1-15 Administrative Sex
        if (relatedPerson.hasGender()) {
            switch (relatedPerson.getGender()) {
                case MALE:
                    terser.set(nk1Path + "-15", "M");
                    break;
                case FEMALE:
                    terser.set(nk1Path + "-15", "F");
                    break;
                case OTHER:
                    terser.set(nk1Path + "-15", "O");
                    break;
                default:
                    terser.set(nk1Path + "-15", "U");
            }
        }

        // NK1-16 Date of Birth
        if (relatedPerson.hasBirthDate()) {
            terser.set(nk1Path + "-16", new java.text.SimpleDateFormat("yyyyMMdd")
                    .format(relatedPerson.getBirthDate()));
        }

        // NK1-20 Primary Language
        if (relatedPerson.hasCommunication()) {
            RelatedPerson.RelatedPersonCommunicationComponent comm = relatedPerson.getCommunicationFirstRep();
            if (comm.hasLanguage() && comm.getLanguage().hasCoding()) {
                terser.set(nk1Path + "-20-1", comm.getLanguage().getCodingFirstRep().getCode());
            }
        }

        // NK1-30 Contact Person's Name (same as NK1-2 in most cases)
        // NK1-31 Contact Person's Telephone Number
        // NK1-32 Contact Person's Address

        // NK1-33 Next of Kin/Associated Party's Identifiers
        if (relatedPerson.hasIdentifier()) {
            Identifier id = relatedPerson.getIdentifierFirstRep();
            if (id.hasValue()) {
                terser.set(nk1Path + "-33-1", id.getValue());
            }
            if (id.hasSystem()) {
                terser.set(nk1Path + "-33-4", id.getSystem());
            }
        }

        nk1Index++;
    }
}
