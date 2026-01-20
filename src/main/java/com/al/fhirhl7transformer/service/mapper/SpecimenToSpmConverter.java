package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Converts FHIR Specimen to HL7 SPM (Specimen) segment.
 */
@Component
public class SpecimenToSpmConverter implements FhirToHl7Converter<Specimen> {

    private static final SimpleDateFormat HL7_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");
    private int spmIndex = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Specimen;
    }

    @Override
    public void convert(Specimen specimen, Message message, Terser terser) throws HL7Exception {
        String spmPath = "/.SPM(" + spmIndex + ")";

        // SPM-1 Set ID
        terser.set(spmPath + "-1", String.valueOf(spmIndex + 1));

        // SPM-2 Specimen ID
        if (specimen.hasIdentifier()) {
            Identifier id = specimen.getIdentifierFirstRep();
            if (id.hasValue()) {
                terser.set(spmPath + "-2-1", id.getValue());
            }
            if (id.hasSystem()) {
                terser.set(spmPath + "-2-2", id.getSystem());
            }
        } else if (specimen.hasId()) {
            terser.set(spmPath + "-2-1", specimen.getIdElement().getIdPart());
        }

        // SPM-3 Specimen Parent IDs
        if (specimen.hasParent()) {
            int parentIdx = 0;
            for (Reference parent : specimen.getParent()) {
                if (parent.hasReference()) {
                    String ref = parent.getReference();
                    if (ref.contains("/")) {
                        terser.set(spmPath + "-3(" + parentIdx + ")-1", ref.substring(ref.lastIndexOf("/") + 1));
                    }
                    parentIdx++;
                }
            }
        }

        // SPM-4 Specimen Type
        if (specimen.hasType()) {
            CodeableConcept type = specimen.getType();
            if (type.hasCoding()) {
                Coding coding = type.getCodingFirstRep();
                terser.set(spmPath + "-4-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(spmPath + "-4-2", coding.getDisplay());
                }
                if (coding.hasSystem()) {
                    terser.set(spmPath + "-4-3", coding.getSystem());
                }
            }
        }

        // SPM-5 Specimen Type Modifier
        // SPM-6 Specimen Additives

        // SPM-7 Specimen Collection Method
        if (specimen.hasCollection()) {
            Specimen.SpecimenCollectionComponent collection = specimen.getCollection();

            if (collection.hasMethod()) {
                CodeableConcept method = collection.getMethod();
                if (method.hasCoding()) {
                    terser.set(spmPath + "-7-1", method.getCodingFirstRep().getCode());
                    if (method.getCodingFirstRep().hasDisplay()) {
                        terser.set(spmPath + "-7-2", method.getCodingFirstRep().getDisplay());
                    }
                }
            }

            // SPM-8 Specimen Source Site
            if (collection.hasBodySite()) {
                CodeableConcept bodySite = collection.getBodySite();
                if (bodySite.hasCoding()) {
                    terser.set(spmPath + "-8-1", bodySite.getCodingFirstRep().getCode());
                    if (bodySite.getCodingFirstRep().hasDisplay()) {
                        terser.set(spmPath + "-8-2", bodySite.getCodingFirstRep().getDisplay());
                    }
                }
            }

            // SPM-11 Specimen Role
            // SPM-12 Specimen Collection Amount
            if (collection.hasQuantity()) {
                Quantity qty = collection.getQuantity();
                if (qty.hasValue()) {
                    terser.set(spmPath + "-12-1", qty.getValue().toPlainString());
                }
                if (qty.hasUnit()) {
                    terser.set(spmPath + "-12-2", qty.getUnit());
                }
            }

            // SPM-17 Specimen Collection Date/Time
            if (collection.hasCollectedDateTimeType()) {
                terser.set(spmPath + "-17-1", HL7_DATE_FORMAT.format(collection.getCollectedDateTimeType().getValue()));
            } else if (collection.hasCollectedPeriod()) {
                Period period = collection.getCollectedPeriod();
                if (period.hasStart()) {
                    terser.set(spmPath + "-17-1", HL7_DATE_FORMAT.format(period.getStart()));
                }
                if (period.hasEnd()) {
                    terser.set(spmPath + "-17-2", HL7_DATE_FORMAT.format(period.getEnd()));
                }
            }

            // SPM-15 Specimen Handling Code (from collection.fastingStatus)
            if (collection.hasFastingStatusCodeableConcept()) {
                CodeableConcept fasting = collection.getFastingStatusCodeableConcept();
                if (fasting.hasCoding()) {
                    terser.set(spmPath + "-15-1", fasting.getCodingFirstRep().getCode());
                }
            }
        }

        // SPM-14 Specimen Description
        // SPM-18 Specimen Received Date/Time
        if (specimen.hasReceivedTime()) {
            terser.set(spmPath + "-18", HL7_DATE_FORMAT.format(specimen.getReceivedTime()));
        }

        // SPM-20 Specimen Availability
        if (specimen.hasStatus()) {
            String status = specimen.getStatus().toCode();
            switch (status) {
                case "available":
                    terser.set(spmPath + "-20", "Y");
                    break;
                case "unavailable":
                case "unsatisfactory":
                    terser.set(spmPath + "-20", "N");
                    break;
                case "entered-in-error":
                    terser.set(spmPath + "-20", "N");
                    break;
            }
        }

        // SPM-21 Specimen Reject Reason
        if (specimen.hasCondition()) {
            CodeableConcept condition = specimen.getConditionFirstRep();
            if (condition.hasCoding()) {
                terser.set(spmPath + "-21-1", condition.getCodingFirstRep().getCode());
            }
        }

        // SPM-24 Specimen Condition
        // SPM-27 Container Type
        if (specimen.hasContainer()) {
            Specimen.SpecimenContainerComponent container = specimen.getContainerFirstRep();
            if (container.hasType()) {
                CodeableConcept containerType = container.getType();
                if (containerType.hasCoding()) {
                    terser.set(spmPath + "-27-1", containerType.getCodingFirstRep().getCode());
                }
            }
        }

        // SPM-29 Number of Specimen Containers
        if (specimen.hasContainer()) {
            terser.set(spmPath + "-29", String.valueOf(specimen.getContainer().size()));
        }

        spmIndex++;
    }
}
