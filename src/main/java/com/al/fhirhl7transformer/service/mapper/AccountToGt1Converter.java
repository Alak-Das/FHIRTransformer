package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

/**
 * Converts FHIR Account (Guarantor) to HL7 GT1 (Guarantor) segment.
 */
@Component
public class AccountToGt1Converter implements FhirToHl7Converter<Account> {

    private int gt1Index = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Account;
    }

    @Override
    public void convert(Account account, Message message, Terser terser) throws HL7Exception {
        // Account with guarantor information maps to GT1
        if (account.hasGuarantor()) {
            for (Account.GuarantorComponent guarantor : account.getGuarantor()) {
                String gt1Path = "/.GT1(" + gt1Index + ")";

                // GT1-1 Set ID
                terser.set(gt1Path + "-1", String.valueOf(gt1Index + 1));

                // GT1-2 Guarantor Number
                if (account.hasIdentifier()) {
                    Identifier id = account.getIdentifierFirstRep();
                    if (id.hasValue()) {
                        terser.set(gt1Path + "-2-1", id.getValue());
                    }
                }

                // The guarantor party reference contains the actual person/org details
                if (guarantor.hasParty()) {
                    Reference party = guarantor.getParty();

                    // If we have display name, parse it for GT1-3 (Guarantor Name)
                    if (party.hasDisplay()) {
                        String display = party.getDisplay();
                        // Try to parse "Family, Given" format
                        if (display.contains(",")) {
                            String[] parts = display.split(",", 2);
                            terser.set(gt1Path + "-3-1", parts[0].trim()); // Family
                            if (parts.length > 1) {
                                terser.set(gt1Path + "-3-2", parts[1].trim()); // Given
                            }
                        } else if (display.contains(" ")) {
                            String[] parts = display.split(" ", 2);
                            terser.set(gt1Path + "-3-2", parts[0].trim()); // Given
                            if (parts.length > 1) {
                                terser.set(gt1Path + "-3-1", parts[1].trim()); // Family
                            }
                        } else {
                            terser.set(gt1Path + "-3-1", display);
                        }
                    }

                    // Reference ID for GT1-2
                    if (party.hasReference()) {
                        String ref = party.getReference();
                        if (ref.contains("/")) {
                            String refId = ref.substring(ref.lastIndexOf("/") + 1);
                            if (terser.get(gt1Path + "-2-1") == null) {
                                terser.set(gt1Path + "-2-1", refId);
                            }
                        }
                    }
                }

                // Note: FHIR Account doesn't have telecom directly
                // Phone/contact info would come from the referenced guarantor party
                // GT1-6 Guarantor Phone Number - Home
                // GT1-7 Guarantor Phone Number - Business
                // (Would need to resolve guarantor.getParty() reference to get contact info)

                // GT1-10 Guarantor Type
                if (guarantor.hasParty()) {
                    String ref = guarantor.getParty().getReference();
                    if (ref != null) {
                        if (ref.contains("Organization")) {
                            terser.set(gt1Path + "-10", "O"); // Organization
                        } else if (ref.contains("RelatedPerson")) {
                            terser.set(gt1Path + "-10", "F"); // Family
                        } else {
                            terser.set(gt1Path + "-10", "P"); // Person
                        }
                    }
                }

                // GT1-11 Guarantor Relationship
                // GT1-12 Guarantor SSN

                // GT1-13 Guarantor Date - Begin
                if (guarantor.hasPeriod()) {
                    Period period = guarantor.getPeriod();
                    if (period.hasStart()) {
                        terser.set(gt1Path + "-13", new java.text.SimpleDateFormat("yyyyMMdd")
                                .format(period.getStart()));
                    }
                    // GT1-14 Guarantor Date - End
                    if (period.hasEnd()) {
                        terser.set(gt1Path + "-14", new java.text.SimpleDateFormat("yyyyMMdd")
                                .format(period.getEnd()));
                    }
                }

                // GT1-15 Guarantor Priority
                // GT1-16 Guarantor Employer Name
                // GT1-17 Guarantor Employer Address

                // GT1-51 Guarantor Financial Class
                if (account.hasType()) {
                    CodeableConcept type = account.getType();
                    if (type.hasCoding()) {
                        terser.set(gt1Path + "-51-1", type.getCodingFirstRep().getCode());
                    }
                }

                gt1Index++;
            }
        } else {
            // Account without explicit guarantor - use account owner as guarantor
            String gt1Path = "/.GT1(" + gt1Index + ")";

            terser.set(gt1Path + "-1", String.valueOf(gt1Index + 1));

            if (account.hasIdentifier()) {
                terser.set(gt1Path + "-2-1", account.getIdentifierFirstRep().getValue());
            }

            if (account.hasName()) {
                terser.set(gt1Path + "-3-1", account.getName());
            }

            if (account.hasOwner()) {
                Reference owner = account.getOwner();
                if (owner.hasDisplay()) {
                    terser.set(gt1Path + "-3-2", owner.getDisplay());
                }
            }

            // Status mapping
            if (account.hasStatus()) {
                String status = account.getStatus().toCode();
                if ("active".equals(status)) {
                    // Active guarantor
                } else if ("inactive".equals(status)) {
                    // Could set end date
                }
            }

            gt1Index++;
        }
    }
}
