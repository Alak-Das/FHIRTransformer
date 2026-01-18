package com.fhirtransformer.util;

/**
 * Centralized mapping constants for HL7 v2.5 ↔ FHIR R4 transformations.
 * 
 * <p>
 * This utility class provides standardized system URLs, codes, and identifiers
 * used throughout the bidirectional conversion process. All constants are
 * organized
 * by category for easy reference and maintenance.
 * 
 * <p>
 * <b>Usage:</b> Reference constants directly via static imports or class name.
 * 
 * <pre>
 * import static com.fhirtransformer.util.MappingConstants.*;
 * 
 * String system = SYSTEM_LOINC;
 * String code = CODE_CONFIRMED;
 * </pre>
 * 
 * @author FHIR Transformer Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class MappingConstants {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private MappingConstants() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
    // ========================================================================
    // Standard Medical Terminology Systems
    // ========================================================================

    /** LOINC (Logical Observation Identifiers Names and Codes) system URL */
    public static final String SYSTEM_LOINC = "http://loinc.org";

    /**
     * SNOMED CT (Systematized Nomenclature of Medicine - Clinical Terms) system URL
     */
    public static final String SYSTEM_SNOMED = "http://snomed.info/sct";

    /**
     * ICD-10 (International Classification of Diseases, 10th Revision) system URL
     */
    public static final String SYSTEM_ICD10 = "http://hl7.org/fhir/sid/icd-10";

    /** RxNorm (Normalized Naming System for Drugs) system URL */
    public static final String SYSTEM_RXNORM = "http://www.nlm.nih.gov/research/umls/rxnorm";

    /** UCUM (Unified Code for Units of Measure) system URL */
    public static final String SYSTEM_UCUM = "http://unitsofmeasure.org";

    /** CVX (Vaccine Codes) system URL */
    public static final String SYSTEM_CVX = "http://hl7.org/fhir/sid/cvx";

    /** NDC (National Drug Code) system URL */
    public static final String SYSTEM_NDC = "http://hl7.org/fhir/sid/ndc";

    // ========================================================================
    // HL7 Terminology Systems
    // ========================================================================

    /** HL7 v3 Marital Status codes */
    public static final String SYSTEM_V2_MARITAL_STATUS = "http://terminology.hl7.org/CodeSystem/v3-MaritalStatus";

    /** HL7 v3 Act Code system */
    public static final String SYSTEM_V2_ACT_CODE = "http://terminology.hl7.org/CodeSystem/v3-ActCode";

    /** HL7 v3 Participation Type codes */
    public static final String SYSTEM_V2_PARTICIPATION_TYPE = "http://terminology.hl7.org/CodeSystem/v3-ParticipationType";
    public static final String SYSTEM_V2_DISCHARGE_DISPOSITION = "http://terminology.hl7.org/CodeSystem/v2-0112";

    /** FHIR Condition Verification Status codes */
    public static final String SYSTEM_CONDITION_VER_STATUS = "http://terminology.hl7.org/CodeSystem/condition-ver-status";

    /** FHIR Condition Clinical Status codes */
    public static final String SYSTEM_CONDITION_CLINICAL = "http://terminology.hl7.org/CodeSystem/condition-clinical";

    /** FHIR Condition Category codes */
    public static final String SYSTEM_CONDITION_CATEGORY = "http://terminology.hl7.org/CodeSystem/condition-category";

    /** FHIR AllergyIntolerance Verification Status codes */
    public static final String SYSTEM_ALLERGY_VER_STATUS = "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification";

    /** FHIR AllergyIntolerance Clinical Status codes */
    public static final String SYSTEM_ALLERGY_CLINICAL = "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical";

    /** Default patient identifier system (UK NHS Number OID) */
    public static final String SYSTEM_PATIENT_IDENTIFIER = "urn:oid:2.16.840.1.113883.2.1.4.1";

    /** HL7 v3 Race codes */
    public static final String SYSTEM_RACE = "http://terminology.hl7.org/CodeSystem/v3-Race";

    /** HL7 v3 Ethnicity codes */
    public static final String SYSTEM_ETHNICITY = "http://terminology.hl7.org/CodeSystem/v3-Ethnicity";

    /** HL7 v3 Religious Affiliation codes */
    public static final String SYSTEM_RELIGION = "http://terminology.hl7.org/CodeSystem/v3-ReligiousAffiliation";

    /** HL7 v2 Table 0127 - Allergen Type codes */
    public static final String SYSTEM_V2_0127 = "http://terminology.hl7.org/CodeSystem/v2-0127";

    // ========================================================================
    // Common Status and Verification Codes
    // ========================================================================

    /** Confirmed status code */
    public static final String CODE_CONFIRMED = "confirmed";

    /** Active status code */
    public static final String CODE_ACTIVE = "active";

    /** Final status code for observations */
    public static final String CODE_FINAL = "final";

    /** Preliminary status code for observations */
    public static final String CODE_PRELIMINARY = "preliminary";

    /** Completed status code for procedures */
    public static final String STATUS_COMPLETED = "completed";

    // ========================================================================
    // HL7 v2 Allergy Type Codes
    // ========================================================================

    /** Drug Allergy (DA) */
    public static final String ALLERGY_TYPE_DRUG = "DA";

    /** Food Allergy (FA) */
    public static final String ALLERGY_TYPE_FOOD = "FA";

    /** Environmental Allergy (EA) */
    public static final String ALLERGY_TYPE_ENV = "EA";

    /** Miscellaneous Allergy (MA) */
    public static final String ALLERGY_TYPE_MISC = "MA";

    // ========================================================================
    // Additional System URIs
    // ========================================================================

    /** FHIR Observation Interpretation system */
    public static final String SYSTEM_OBSERVATION_INTERPRETATION = "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";

    /**
     * ICD-10 (International Classification of Diseases, 10th Revision) system URL
     */
    // Note: SYSTEM_ICD10 is already defined, adding alias if needed or using
    // existing one.
    // However, existing is http://hl7.org/fhir/sid/icd-10.
    // Let's rely on SYSTEM_ICD10 if it exists. Note line 49 defines SYSTEM_ICD10.

    // ========================================================================
    // Procedure and Coverage Systems
    // ========================================================================

    /** FHIR Procedure Category system */
    public static final String SYSTEM_PROCEDURE_CATEGORY = "http://terminology.hl7.org/CodeSystem/procedure-category";

    /** FHIR Coverage Type system */
    public static final String SYSTEM_COVERAGE_TYPE = "http://terminology.hl7.org/CodeSystem/v3-ActCode";

    /** CPT (Current Procedural Terminology) system */
    public static final String SYSTEM_CPT = "http://www.ama-assn.org/go/cpt";

    // ========================================================================
    // FHIR Extensions
    // ========================================================================

    /** Custom extension for preserving HL7 Z-segments */
    public static final String EXT_HL7_Z_SEGMENT = "http://fhirtransformer.com/fhir/StructureDefinition/hl7-z-segment";

    /** Custom extension for HL7 equipment type codes */
    public static final String EXT_HL7_EQUIPMENT_TYPE = "http://fhirtransformer.com/fhir/StructureDefinition/hl7-equipment-type";

    // ========================================================================
    // HL7 v2.5 Equipment Type Codes (PID-13.3, PID-14.3)
    // ========================================================================

    /** Cell Phone / Mobile (CP) */
    public static final String EQUIP_CELL = "CP";

    /** Telephone / Landline (PH) */
    public static final String EQUIP_PHONE = "PH";

    /** Fax (FX) */
    public static final String EQUIP_FAX = "FX";

    /** Internet / Email (Internet) */
    public static final String EQUIP_INTERNET = "Internet";

    // ========================================================================
    // HL7 v2 Identifier Type Codes
    // ========================================================================

    /** Medical Record Number (MR) */
    public static final String IDENT_MR = "MR";

    /** Social Security Number (SS) */
    public static final String IDENT_SS = "SS";

    /** Practitioner Identifier system */
    public static final String SYSTEM_PRACTITIONER_ID = "http://terminology.hl7.org/CodeSystem/v2-0203";

    /** Organization Identifier system */
    public static final String SYSTEM_ORGANIZATION_ID = "http://terminology.hl7.org/CodeSystem/v2-0203";
}
