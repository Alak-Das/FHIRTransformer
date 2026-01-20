package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Converts FHIR CarePlan to HL7 ORC (Common Order) segment.
 * CarePlan activities map to individual order segments.
 */
@Component
public class CarePlanToOrcConverter implements FhirToHl7Converter<CarePlan> {

    private int orcIndex = 0;
    private final SimpleDateFormat hl7DateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof CarePlan;
    }

    @Override
    public void convert(CarePlan carePlan, Message message, Terser terser) throws HL7Exception {
        String msgStruct = message.getName();
        String orcPath;
        if (msgStruct != null && msgStruct.contains("ORM")) {
            orcPath = "/.ORDER(" + orcIndex + ")/ORC";
        } else {
            orcPath = "/.ORC(" + orcIndex + ")";
        }

        // ORC-1 Order Control (based on CarePlan status)
        String orderControl = "NW"; // Default: New Order
        if (carePlan.hasStatus()) {
            switch (carePlan.getStatus()) {
                case ACTIVE:
                    orderControl = "NW"; // New Order
                    break;
                case REVOKED:
                    orderControl = "CA"; // Cancel
                    break;
                case ONHOLD:
                    orderControl = "HD"; // Hold
                    break;
                case COMPLETED:
                    orderControl = "SC"; // Status Changed
                    break;
                case DRAFT:
                    orderControl = "SN"; // Send Order Number
                    break;
                case ENTEREDINERROR:
                    orderControl = "CA"; // Cancel
                    break;
                default:
                    orderControl = "NW";
            }
        }
        terser.set(orcPath + "-1", orderControl);

        // ORC-2 Placer Order Number
        if (carePlan.hasIdentifier()) {
            for (Identifier id : carePlan.getIdentifier()) {
                if (id.hasType() && id.getType().hasCoding()) {
                    String typeCode = id.getType().getCodingFirstRep().getCode();
                    if ("PLAC".equals(typeCode)) {
                        terser.set(orcPath + "-2-1", id.getValue());
                        if (id.hasSystem()) {
                            terser.set(orcPath + "-2-2", id.getSystem());
                        }
                    } else if ("FILL".equals(typeCode)) {
                        terser.set(orcPath + "-3-1", id.getValue());
                        if (id.hasSystem()) {
                            terser.set(orcPath + "-3-2", id.getSystem());
                        }
                    }
                } else if (id.hasValue()) {
                    // Default to placer order number
                    if (terser.get(orcPath + "-2-1") == null) {
                        terser.set(orcPath + "-2-1", id.getValue());
                    }
                }
            }
        }

        // If no identifier, use CarePlan ID
        if (terser.get(orcPath + "-2-1") == null && carePlan.hasId()) {
            terser.set(orcPath + "-2-1", carePlan.getIdElement().getIdPart());
        }

        // ORC-5 Order Status
        if (carePlan.hasStatus()) {
            String orderStatus;
            switch (carePlan.getStatus()) {
                case ACTIVE:
                    orderStatus = "IP"; // In Process
                    break;
                case COMPLETED:
                    orderStatus = "CM"; // Complete
                    break;
                case REVOKED:
                    orderStatus = "CA"; // Canceled
                    break;
                case ONHOLD:
                    orderStatus = "HD"; // On Hold
                    break;
                case DRAFT:
                    orderStatus = "SC"; // Scheduled
                    break;
                default:
                    orderStatus = "A"; // Some (but not all) results available
            }
            terser.set(orcPath + "-5", orderStatus);
        }

        // ORC-7 Quantity/Timing (from period)
        if (carePlan.hasPeriod()) {
            Period period = carePlan.getPeriod();
            // ORC-7-4 Start DateTime
            if (period.hasStart()) {
                terser.set(orcPath + "-7-4", hl7DateTimeFormat.format(period.getStart()));
            }
            // ORC-7-5 End DateTime
            if (period.hasEnd()) {
                terser.set(orcPath + "-7-5", hl7DateTimeFormat.format(period.getEnd()));
            }
        }

        // ORC-9 Date/Time of Transaction (created date)
        if (carePlan.hasCreated()) {
            terser.set(orcPath + "-9", hl7DateTimeFormat.format(carePlan.getCreated()));
        }

        // ORC-10 Entered By (author)
        if (carePlan.hasAuthor()) {
            Reference author = carePlan.getAuthor();
            if (author.hasReference()) {
                String ref = author.getReference();
                if (ref.contains("/")) {
                    terser.set(orcPath + "-10-1", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
            if (author.hasDisplay()) {
                String display = author.getDisplay();
                if (display.contains(",")) {
                    String[] parts = display.split(",", 2);
                    terser.set(orcPath + "-10-2", parts[0].trim()); // Family
                    if (parts.length > 1) {
                        terser.set(orcPath + "-10-3", parts[1].trim()); // Given
                    }
                } else if (display.contains(" ")) {
                    String[] parts = display.split(" ", 2);
                    terser.set(orcPath + "-10-3", parts[0].trim()); // Given
                    if (parts.length > 1) {
                        terser.set(orcPath + "-10-2", parts[1].trim()); // Family
                    }
                }
            }
        }

        // ORC-12 Ordering Provider (from contributor)
        if (carePlan.hasContributor()) {
            for (Reference contributor : carePlan.getContributor()) {
                if (contributor.hasReference() && contributor.getReference().contains("Practitioner")) {
                    String ref = contributor.getReference();
                    terser.set(orcPath + "-12-1", ref.substring(ref.lastIndexOf("/") + 1));
                    if (contributor.hasDisplay()) {
                        String display = contributor.getDisplay();
                        if (display.contains(",")) {
                            String[] parts = display.split(",", 2);
                            terser.set(orcPath + "-12-2", parts[0].trim());
                            if (parts.length > 1) {
                                terser.set(orcPath + "-12-3", parts[1].trim());
                            }
                        } else if (display.contains(" ")) {
                            String[] parts = display.split(" ", 2);
                            terser.set(orcPath + "-12-3", parts[0].trim());
                            if (parts.length > 1) {
                                terser.set(orcPath + "-12-2", parts[1].trim());
                            }
                        }
                    }
                    break;
                }
            }
        }

        // ORC-14 Call Back Phone Number (from author's telecom if available)
        // Not directly available in CarePlan

        // ORC-16 Order Control Code Reason (from activity reason)
        if (carePlan.hasActivity()) {
            CarePlan.CarePlanActivityComponent activity = carePlan.getActivityFirstRep();
            if (activity.hasDetail() && activity.getDetail().hasCode()) {
                CodeableConcept code = activity.getDetail().getCode();
                if (code.hasCoding()) {
                    Coding coding = code.getCodingFirstRep();
                    terser.set(orcPath + "-16-1", coding.getCode());
                    if (coding.hasDisplay()) {
                        terser.set(orcPath + "-16-2", coding.getDisplay());
                    }
                }
            }
        }

        // ORC-17 Entering Organization
        if (carePlan.hasContributor()) {
            for (Reference contributor : carePlan.getContributor()) {
                if (contributor.hasReference() && contributor.getReference().contains("Organization")) {
                    if (contributor.hasDisplay()) {
                        terser.set(orcPath + "-17-1", contributor.getDisplay());
                    }
                    break;
                }
            }
        }

        // ORC-21 Ordering Facility Name
        if (carePlan.hasContributor()) {
            for (Reference contributor : carePlan.getContributor()) {
                if (contributor.hasReference() && contributor.getReference().contains("Organization")) {
                    if (contributor.hasDisplay()) {
                        terser.set(orcPath + "-21-1", contributor.getDisplay());
                    }
                    break;
                }
            }
        }

        orcIndex++;
    }
}
