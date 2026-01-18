package com.fhirtransformer.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class Hl7ToFhirServiceTest {

    @Autowired
    private Hl7ToFhirService hl7ToFhirService;

    @Test
    public void testConversion() throws Exception {
        // Valid HL7 v2.5 Message
        // MSH-9: ADT^A01, MSH-10: 1001, MSH-11: P, MSH-12: 2.5
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|1001|P|2.5\r" +
                "PID|1||100||DOE^JOHN||19700101|M||||||||||1000\r" +
                "PV1|1|I|2000^2012^01||||002970^FUSILIER^KAMERA^^^MD^Dr||||||||| |||||||||||||||||||||||||20250101000100";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        System.out.println(fhir);
        assertNotNull(fhir);
        assertTrue(fhir.contains("\"resourceType\": \"Bundle\""));
        assertTrue(fhir.contains("\"family\": \"DOE\""));
        assertTrue(fhir.contains("\"gender\": \"male\""));
    }

    @Test
    public void testZSegmentConversion() throws Exception {
        // HL7 with ZPI segment
        // MSH-9: ADT^A01 (Triggers CustomADT_A01)
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|1002|P|2.5\r" +
                "PID|1||100||DOE^JANE||19700101|F\r" +
                "PV1|1|I\r" +
                "ZPI|1|Fluffy|VIP-Gold|Active"; // Custom ZPI Segment

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        System.out.println("Z-Segment Test Result: " + fhir);
        assertNotNull(fhir);
        assertTrue(fhir.contains("Fluffy"), "Should contain Pet Name from ZPI-2");
        assertTrue(fhir.contains("VIP-Gold"), "Should contain VIP Level from ZPI-3");
        assertTrue(fhir.contains("http://example.org/fhir/StructureDefinition/pet-name"),
                "Should contain extension URL");
    }

    @Test
    public void testMedicationConversion() throws Exception {
        // HL7 with RXE segment
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||UNK^UNK|MSG001|P|2.5\r" +
                "PID|1||104||MEDICATION^TEST||19900101|M\r" +
                "PD1||||Dr^Smith\r" +
                "PV1|1|I||||||||||||||||||||||||||||||||||\r" +
                "RXE|1|RX12345^Aspirin 81mg|81||mg||Take with food|||30|tablets|3";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        System.out.println("Medication Test Result: " + fhir);
        assertNotNull(fhir);
        assertTrue(fhir.contains("MedicationRequest"), "Should contain MedicationRequest resource");
        assertTrue(fhir.contains("RX12345"), "Should contain Medication Code");
        assertTrue(fhir.contains("Aspirin 81mg"), "Should contain Medication Name");
        assertTrue(fhir.contains("\"value\": 81"), "Should contain Dose Amount");
        assertTrue(fhir.contains("Take with food"), "Should contain Instructions");
        assertTrue(fhir.contains("\"value\": 30"), "Should contain Dispense Amount");
        assertTrue(fhir.contains("\"numberOfRepeatsAllowed\": 3"), "Should contain Refills");
    }

    @Test
    public void testNteConversion() throws Exception {
        // HL7 with OBR, OBX and NTE segments
        // NTE after OBR -> DiagnosticReport.conclusion
        // NTE after OBX -> Observation.note
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ORU^R01|MSG002|P|2.5\r" +
                "PID|1||105||TEST^NTE||19900101|M\r" +
                "OBR|1|ORDER1|FILL1|88304^SURGICAL PATHOLOGY^LN|||202301010000\r" +
                "NTE|1|L|Report Conclusion Text\r" +
                "OBX|1|NM|21612-7^AGE^LN||32|a||||F\r" +
                "NTE|1|L|Observation Comment Text";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        System.out.println("NTE Test Result: " + fhir);
        assertNotNull(fhir);

        // Check DiagnosticReport Conclusion
        assertTrue(fhir.contains("DiagnosticReport"), "Should contain DiagnosticReport");
        assertTrue(fhir.contains("Report Conclusion Text"), "Should contain Report Conclusion from NTE");

        // Check Observation Note
        assertTrue(fhir.contains("Observation"), "Should contain Observation");
        assertTrue(fhir.contains("Observation Comment Text"), "Should contain Observation Note from NTE");
    }

    @Test
    public void testConditionAndProcedureImprovements() throws Exception {
        // HL7 with DG1 and PR1 + NTEs + Practitioners
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|MSG003|P|2.5\r" +
                "PID|1||106||TEST^PHASE3||19900101|M\r" +
                "DG1|1||I10^Hypertension||A\r" +
                "NTE|1|L|Condition Note Text\r" +
                "PR1|1||CPT^47600^Cholecystectomy||202301011200||||||Dr. Surgeon|Dr. Anes\r" +
                "NTE|1|L|Procedure Note Text";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        System.out.println("Phase 3 Test Result: " + fhir);
        assertNotNull(fhir);

        // Verify Condition Note
        assertTrue(fhir.contains("Condition"), "Should contain Condition");
        assertTrue(fhir.contains("Condition Note Text"), "Should contain Condition Note");

        // Verify Procedure Note
        assertTrue(fhir.contains("Procedure"), "Should contain Procedure");
        assertTrue(fhir.contains("Procedure Note Text"), "Should contain Procedure Note");

        // Verify Procedure Performers
        assertTrue(fhir.contains("Dr. Surgeon"), "Should contain Surgeon");
        assertTrue(fhir.contains("Dr. Anes"), "Should contain Anesthesiologist");
        assertTrue(fhir.contains("PPRF"), "Should contain Primary Performer Role");
        assertTrue(fhir.contains("SPRF"), "Should contain Secondary Performer Role");
    }

    @Test
    public void testPhase4Enhancements() throws Exception {
        // HL7 with Phase 4 features:
        // AL1: Category=AA (Animal -> Environment), Severity=SV (Severe -> High
        // Criticality)
        // RXA: Note (RXA-9), Dose (RXA-12), Expiration (RXA-16)
        // RXR: Route (RXR-1), Site (RXR-2)
        // PV1: Discharge Disposition (PV1-36)

        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|MSG004|P|2.5\r" +
                "PID|1||108||TEST^PHASE4||19900101|M\r" +
                "PV1|1|I|WARD 1^^^|R||||||||||||||||||||||||||||||||01\r" + // PV1-36 = 01 (Discharged to home)
                "AL1|1|AA|Cat Dander|SV|Hives|20230101\r" + // AA=Animal, SV=Severe
                "ORC|RE\r" + // Required group for VXU but ADT might have it under procedure or separate.
                             // Current ImmunizationConverter just looks for RXA.
                "RXA|0|1|20230101||10^Polio^CVX|0.5|mL^Milliliter^ISO+||||||||LOT1234|20250101||CP\r" + // RXA-12=0.5,
                // RXA-11=mL,
                // RXA-15=LOT1234, RXA-16=20250101
                "RXR|IM^Intramuscular^HL70162|LA^Left Arm^HL70163"; // Route=IM, Site=LA

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        assertNotNull(fhir);
        System.out.println("Phase 4 Test Result: " + fhir);

        // Verify Encounter Discharge Disposition
        assertTrue(fhir.contains("Encounter"), "Should contain Encounter");
        assertTrue(fhir.contains("http://terminology.hl7.org/CodeSystem/v2-0112"), "Should contain Discharge System");
        assertTrue(fhir.contains("\"code\": \"01\""), "Should contain Discharge Code 01");

        // Verify Allergy Improvements
        assertTrue(fhir.contains("AllergyIntolerance"), "Should contain Allergy");
        assertTrue(fhir.contains("\"criticality\": \"high\""), "Should map SV to High Criticality");
        assertTrue(fhir.contains("\"category\": [ \"environment\" ]"), "Should map AA to Environment");

        // Verify Immunization Improvements
        assertTrue(fhir.contains("Immunization"), "Should contain Immunization");
        assertTrue(fhir.contains("\"value\": 0.5"), "Should contain Dose Quantity 0.5");
        assertTrue(fhir.contains("\"unit\": \"Milliliter\""), "Should contain Dose Unit");

        // Expiration Date (RXA-16) - JSON serialization might vary, checking simple
        // presence or date string
        // 2025-01-01
        assertTrue(fhir.contains("2025-01-01"), "Should contain Expiration Date");

        // RXR Route and Site
        assertTrue(fhir.contains("Intramuscular"), "Should contain Route Text");
        assertTrue(fhir.contains("Left Arm"), "Should contain Site Text");
        assertTrue(fhir.contains("IM"), "Should contain Route Code");
        assertTrue(fhir.contains("LA"), "Should contain Site Code");
    }

    @Test
    public void testPhase5Enhancements() throws Exception {
        // HL7 with Phase 5 features:
        // PV1-3: Detailed Location (Ward 1, Room 202, Bed 1, Facility General Hospital)
        // ORC: Placer/Filler IDs for Linking
        // OBR: Matching IDs

        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ORM^O01|MSG005|P|2.5\r" +
                "PID|1||109||TEST^PHASE5||19900101|M\r" +
                "PV1|1|I|WARD 1^202^1^General Hospital||||||||||||||||||||||||||||||||||\r" +
                "ORC|NW|PLACER123|FILLER456\r" +
                "OBR|1|PLACER123|FILLER456|88304^SURGICAL PATHOLOGY^LN|||202301010000";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        assertNotNull(fhir);
        System.out.println("Phase 5 Test Result: " + fhir);

        // Verify Location Resource
        assertTrue(fhir.contains("Location"), "Should contain Location resource");
        assertTrue(fhir.contains("WARD 1"), "Should contain Point of Care");
        assertTrue(fhir.contains("202"), "Should contain Room");
        assertTrue(fhir.contains("General Hospital"), "Should contain Facility");

        // Verify Encounter Link
        assertTrue(fhir.contains("Encounter"), "Should contain Encounter");
        assertTrue(fhir.contains("\"reference\": \"Location/"), "Encounter should reference Location");

        // Verify ServiceRequest Identifiers
        assertTrue(fhir.contains("ServiceRequest"), "Should contain ServiceRequest");
        assertTrue(fhir.contains("PLACER123"), "ServiceRequest should have Placer ID");
        assertTrue(fhir.contains("FILLER456"), "ServiceRequest should have Filler ID");

        // Verify DiagnosticReport Linking
        assertTrue(fhir.contains("DiagnosticReport"), "Should contain DiagnosticReport");
        // We expect DiagnosticReport to contain a reference to the ServiceRequest
        // checking for "basedOn": [ { "reference": "ServiceRequest/..."
        assertTrue(fhir.contains("\"basedOn\""), "DiagnosticReport should have basedOn");
        assertTrue(fhir.contains("ServiceRequest/"), "basedOn should point to ServiceRequest");
    }

    @Test
    public void testPractitionerAndMedicationAdministration() throws Exception {
        // HL7 with PV1 (Practitioner), ORC/RXE (MedicationRequest), and ORC/RXA
        // (MedicationAdministration)
        // ORC-2/3 used for linking RXE and RXA
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|202301011200||ADT^A01|MSG005|P|2.5\r" +
                "PID|1||110||PRACTITIONER^TEST||19900101|M\r" +
                "PV1|1|I|||||DOC123^SMITH^JOHN^^^^^MD||||||||||||||||||||||||||||||||||||\r" +
                "ORC|NW|ORD789|FILL789|||||||||100^NURSE^MARY\r" +
                "RXE|1|MED001^Ibuprofen 400mg|400||mg\r" +
                "ORC|RE|ORD789|FILL789|||||||||100^NURSE^MARY\r" +
                "RXA|0|1|202301011200||MED001^Ibuprofen 400mg|400|mg|||||||||||";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        System.out.println("Practitioner/MedicationAdmin Test Result: " + fhir);
        assertNotNull(fhir);

        // Verify Practitioner from PV1
        assertTrue(fhir.contains("Practitioner"), "Should contain Practitioner resource");
        assertTrue(fhir.contains("SMITH"), "Should contain Practitioner Family Name");
        assertTrue(fhir.contains("DOC123"), "Should contain Practitioner ID");

        // Verify Practitioner from ORC
        assertTrue(fhir.contains("NURSE"), "Should contain Nurse from ORC");
        assertTrue(fhir.contains("100"), "Should contain Nurse ID");

        // Verify MedicationRequest
        assertTrue(fhir.contains("MedicationRequest"), "Should contain MedicationRequest");
        assertTrue(fhir.contains("ORD789"), "Should contain Placer ID in MedicationRequest");

        // Verify MedicationAdministration and Linking
        assertTrue(fhir.contains("MedicationAdministration"), "Should contain MedicationAdministration");
        assertTrue(fhir.contains("MedicationRequest/"), "Should contain reference to MedicationRequest");

        // Check for correct linking - MedicationAdministration should have a 'request'
        // reference
        assertTrue(fhir.contains("\"request\": {"), "Should have a request field in MedicationAdministration");
    }

    @Test
    public void testEnhancedMappingsAndEncounterLinking() throws Exception {
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|202301011200||ADT^A01|MSG006|P|2.5\r" +
                "EVN|A01|202301011200\r" +
                "PID|1||PID123||ENHANCED^TEST||19850101|F\r" +
                "PV1|1|I|POINT^ROOM^BED^FACILITY||||DOC456^DOE^JANE^^^^^MD||||||||||||VISIT789\r" +
                "OBX|1|NM|8867-4^Heart Rate^LN||72|bpm|60-100||||F|||202301011200||OBSERVER789^BROWN^CHARLIE\r" +
                "AL1|1|DA|ALR123^PENICILLIN|SV|Hives^H1^0127\r" +
                "DG1|1|F|I10^Essential Hypertension^I10|Hypertension|20230101|F\r" +
                "PR1|1|CPT|93000^EKG^CPT||202301011200||||||SURGEON123^CUTTER^JACK\r" +
                "ORC|NW|ORD123|FILL123|||||||||REQ789^ORDERER^SAM\r" +
                "RXE|1|MED123^Medicine|10||mg\r";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);
        assertNotNull(fhir);

        // Verify Encounter Linking
        assertTrue(fhir.contains("Encounter"), "Should contain Encounter");
        assertTrue(fhir.contains("VISIT789"), "Should contain Visit Number");
        assertTrue(fhir.contains("Encounter/"), "Should have links to Encounter");

        // Verify Observation Enhanced Mapping
        assertTrue(fhir.contains("8867-4"), "Should contain Heart Rate code");
        assertTrue(fhir.contains("60-100"), "Should contain Reference Range");
        assertTrue(fhir.contains("Practitioner/OBSERVER789"), "Should contain Performer reference");

        // Verify Condition Enhanced Mapping
        assertTrue(fhir.contains("Hypertension"), "Should contain Diagnosis");
        assertTrue(fhir.contains("Final"), "Should contain mapped Diagnosis Category (Final)");

        // Verify Procedure Practitioner Referencing
        assertTrue(fhir.contains("Practitioner/SURGEON123"), "Should contain Performer reference in Procedure");
        assertTrue(fhir.contains("CUTTER"), "Should contain Performer display name");

        // Verify Allergy Enhanced Mapping
        assertTrue(fhir.contains("PENICILLIN"), "Should contain Allergen");
        assertTrue(fhir.contains("Hives"), "Should contain Reaction manifestation");
        assertTrue(fhir.contains("high"), "Should contain mapped Criticality (SV -> high)");

        // Verify MedicationRequest Requester
        assertTrue(fhir.contains("Practitioner/REQ789"), "Should contain Requester reference in MedicationRequest");
        assertTrue(fhir.contains("ORDERER"), "Should contain Requester display name");
    }

    @Test
    public void testPhase8AdvancedMappings() throws Exception {
        // HL7 message with OBR in ORDER group and SCH in APPOINTMENT group
        // Also includes ordering provider (OBR-16), timing (OBR-27.4), and reason
        // (OBR-31)
        String hl7 = "MSH|^~\\&|SENDAPP|SENDFAC|RECVAPP|RECVFAC|20260119120000||OML^O21^OML_O21|MSG999|P|2.5.1\r" +
                "PID|1||PAT123||DOE^JOHN||19800101|M\r" +
                "PV1|1|O|CLINIC1||||||||||||||||OP123\r" +
                "ORC|NW|PLACER123|FILLER123|||||||20260119120000\r" +
                "OBR|1|PLACER123|FILLER123|8867-4^HEART RATE^LN|||20260119100000|||||||||PROV123^SMITH^DR||||||20260119110000|||F||1^once^Every 8 hours^20260119103000||||Routine Checkup\r"
                +
                "SCH|1|SCH_PLACER|SCH_FILLER|||REASON123^Annual Exam|||||^^^20260119140000|||||||||||||Blocked\r";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);
        assertNotNull(fhir);

        // Verification
        assertTrue(fhir.contains("Provenance"), "Should contain Provenance resource");
        assertTrue(fhir.contains("SENDAPP at SENDFAC"), "Provenance agent should contain sending app/facility");
        assertTrue(fhir.contains("2026-01-19T12:00:00"), "Provenance should contain MSH-7 timestamp");

        assertTrue(fhir.contains("ServiceRequest"), "Should contain ServiceRequest");
        assertTrue(fhir.contains("Practitioner/PROV123"), "ServiceRequest should have ordering provider/requester");
        assertTrue(fhir.contains("2026-01-19T10:30:00"), "ServiceRequest should have timing from OBR-27.4");
        assertTrue(fhir.contains("Routine Checkup"), "ServiceRequest should have reason from OBR-31");

        assertTrue(fhir.contains("DiagnosticReport"), "Should contain DiagnosticReport");
        assertTrue(fhir.contains("2026-01-19T10:00:00"), "DiagnosticReport should have effective time from OBR-7");
        assertTrue(fhir.contains("2026-01-19T11:00:00"), "DiagnosticReport should have issued time from OBR-22");

        assertTrue(fhir.contains("Appointment"), "Should contain Appointment");
        assertTrue(fhir.contains("2026-01-19T14:00:00"), "Appointment should have start time");
    }

    @Test
    public void testPhase9RigorousImprovements() throws Exception {
        // HL7 message with grouped PID/PV1/OBR/OBX to test robust path resolution and
        // linking
        String hl7 = "MSH|^~\\&|SENDAPP|SENDFAC|RECVAPP|RECVFAC|20260119120000||OML^O21^OML_O21|MSG100|P|2.5.1\r" +
                "PID|1||PAT123||DOE^JOHN||19800101|M||2106-3^White^HL70005||||||S||||||2135-2^Non-Hispanic^HL70189\r" +
                "PV1|1|O|WARD1^ROOM2^BED3^FAC1||||123^SMITH^DR^ATND|456^JONES^DR^REFR|789^BROWN^DR^CON||||||||||V100|||||||||||||||||||20260119100000\r"
                +
                "ORC|NW|P1|F1||CM|||||20260119120000\r" +
                "OBR|1|P1|F1|8867-4^HEART RATE^LN|||20260119100000\r" +
                "OBX|1|NM|8867-4^HEART RATE^LN||75|bpm|||||F\r" +
                "OBX|2|NM|8868-2^BLOOD PRESSURE^LN||120/80|mmHg|||||F\r";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);
        assertNotNull(fhir);

        // Verification: Patient Demographics (Race/Ethnicity)
        assertTrue(fhir.contains("us-core-race"), "Should contain race extension");
        assertTrue(fhir.contains("ombCategory"), "Should contain ombCategory child extension");
        assertTrue(fhir.contains("2106-3"), "Should contain race code");
        assertTrue(fhir.contains("us-core-ethnicity"), "Should contain ethnicity extension");
        assertTrue(fhir.contains("2135-2"), "Should contain ethnicity code");
        assertTrue(fhir.contains("Non-Hispanic"), "Should contain ethnicity text");

        // Verification: Location & Encounter
        assertTrue(fhir.contains("Location"), "Should contain Location resource");
        assertTrue(fhir.contains("WARD1 ROOM2-BED3"), "Location name should be correct");
        assertTrue(fhir.contains("Encounter"), "Should contain Encounter resource");
        assertTrue(fhir.contains("Location/"), "Encounter should reference Location");

        // Verification: Encounter Participants
        assertTrue(fhir.contains("attender"), "Encounter should have attender");
        assertTrue(fhir.contains("referrer"), "Encounter should have referrer");
        assertTrue(fhir.contains("consultant"), "Encounter should have consultant");

        // Verification: ServiceRequest Status from ORC-5
        assertTrue(fhir.contains("\"status\": \"completed\""), "ServiceRequest status should be completed from ORC-5");

        // Verification: DiagnosticReport -> Observation Linking (CRITICAL)
        assertTrue(fhir.contains("DiagnosticReport"), "Should contain DiagnosticReport");
        assertTrue(fhir.contains("\"result\": ["), "DiagnosticReport should have results");

        // Count Observation references
        int resultRefs = 0;
        int lastIdx = 0;
        while ((lastIdx = fhir.indexOf("\"reference\": \"Observation/", lastIdx)) != -1) {
            resultRefs++;
            lastIdx++;
        }
        assertTrue(resultRefs >= 4, "Should have at least 4 Observation references (linked in Report and Provenance)");
    }

    @Test
    public void testAdtA08WithoutPv1() throws Exception {
        // HL7 ADT^A08 without PV1 segment
        // Should NOT create an Encounter
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A08|MSG-A08|P|2.5\r" +
                "PID|1||101||DOE^JANE||19800101|F";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        System.out.println("ADT^A08 No PV1 Result: " + fhir);
        assertNotNull(fhir);
        assertTrue(fhir.contains("Patient"), "Should contain Patient");
        assertTrue(!fhir.contains("Encounter"), "Should NOT contain Encounter when PV1 is missing");
    }
}
