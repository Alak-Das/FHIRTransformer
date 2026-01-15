package com.fhirtransformer.util;

public class MappingConstants {
    // Systems
    public static final String SYSTEM_LOINC = "http://loinc.org";
    public static final String SYSTEM_SNOMED = "http://snomed.info/sct";
    public static final String SYSTEM_ICD10 = "http://hl7.org/fhir/sid/icd-10";
    public static final String SYSTEM_RXNORM = "http://www.nlm.nih.gov/research/umls/rxnorm";
    public static final String SYSTEM_UCUM = "http://unitsofmeasure.org";

    // HL7 Terminology Systems
    public static final String SYSTEM_V2_MARITAL_STATUS = "http://terminology.hl7.org/CodeSystem/v3-MaritalStatus";
    public static final String SYSTEM_V2_ACT_CODE = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    public static final String SYSTEM_V2_PARTICIPATION_TYPE = "http://terminology.hl7.org/CodeSystem/v3-ParticipationType";
    public static final String SYSTEM_CONDITION_VER_STATUS = "http://terminology.hl7.org/CodeSystem/condition-ver-status";
    public static final String SYSTEM_CONDITION_CLINICAL = "http://terminology.hl7.org/CodeSystem/condition-clinical";
    public static final String SYSTEM_CONDITION_CATEGORY = "http://terminology.hl7.org/CodeSystem/condition-category";
    public static final String SYSTEM_ALLERGY_VER_STATUS = "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification";
    public static final String SYSTEM_ALLERGY_CLINICAL = "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical";
    public static final String SYSTEM_PATIENT_IDENTIFIER = "urn:oid:2.16.840.1.113883.2.1.4.1";

    // Codes
    public static final String CODE_CONFIRMED = "confirmed";
    public static final String CODE_ACTIVE = "active";
    public static final String CODE_FINAL = "final";
    public static final String CODE_PRELIMINARY = "preliminary";

    // Allergy Types (HL7 v2 -> FHIR)
    public static final String ALLERGY_TYPE_DRUG = "DA";
    public static final String ALLERGY_TYPE_FOOD = "FA";
    public static final String ALLERGY_TYPE_ENV = "EA";
    public static final String ALLERGY_TYPE_MISC = "MA";
    // Procedure & Coverage
    public static final String SYSTEM_PROCEDURE_CATEGORY = "http://terminology.hl7.org/CodeSystem/procedure-category";
    public static final String SYSTEM_COVERAGE_TYPE = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    public static final String SYSTEM_CPT = "http://www.ama-assn.org/go/cpt";

    public static final String STATUS_COMPLETED = "completed";
}
