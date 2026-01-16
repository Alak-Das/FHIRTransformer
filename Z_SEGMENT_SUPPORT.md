# Custom Z-Segment Support - Implementation Summary

**Date**: 2026-01-16  
**Status**: ✅ **COMPLETED**

---

## 🚀 **Overview**

This feature enables the parsing and mapping of custom HL7 Z-segments (specifically `ZPI`) into FHIR resources. This allows hospitals to preserve custom data fields like **Pet Name**, **VIP Level**, and **Archive Status** during conversion.

## ✅ **What Was Implemented**

### 1. Custom HL7 Model Classes
- **ZPI Segment** (`com.fhirtransformer.model.hl7.v25.segment.ZPI`): 
  - Defined fields:
    - **ZPI-1**: Set ID (SI)
    - **ZPI-2**: Pet Name (ST)
    - **ZPI-3**: VIP Level (ST)
    - **ZPI-4**: Archive Status (ST)
- **Custom Message** (`com.fhirtransformer.model.hl7.v25.message.ADT_A01`):
  - Extends standard `ADT^A01`.
  - Includes `ZPI` segment at the end of the structure.

### 2. HAPI Parser Configuration
- Updated `PerformanceConfig.java` to use `CustomModelClassFactory`.
- Configured parser to look in `com.fhirtransformer.model.hl7.v25` package for model classes.
- Enables automatic typed parsing of `ADT^A01` messages into the custom structure.

### 3. Conversion Logic (Hl7ToFhirService)
- Added `processZSegments` method.
- **Typed Extraction**: specifically extracts `ZPI` fields using Terser (e.g., `/.ZPI-2`).
- **FHIR Mapping**: Maps data to FHIR Extensions on the `Patient` resource.
  - `http://example.org/fhir/StructureDefinition/pet-name`
  - `http://example.org/fhir/StructureDefinition/vip-level`
  - `http://example.org/fhir/StructureDefinition/archive-status`
- **Fallback**: Preserves other unknown Z-segments as generic extensions (legacy behavior preserved).

---

## 📊 **Verification**

### Unit Test (`Hl7ToFhirServiceTest`)
A new test case `testZSegmentConversion` confirms:
- Input: HL7 `ADT^A01` with `ZPI|1|Fluffy|VIP-Gold|Active`.
- Output: FHIR Bundle containing Patient resource.
- Validated:
  - Patient contains extension for **Pet Name** ("Fluffy").
  - Patient contains extension for **VIP Level** ("VIP-Gold").

### How to Test Manually

```bash
# Send a message with ZPI data
curl -X POST http://localhost:9091/api/convert/v2-to-fhir-sync \
  -H "Content-Type: text/plain" \
  -d "MSH|^~\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|1002|P|2.5
PID|1||100||DOE^JANE||19700101|F
ZPI|1|Fluffy|VIP-Gold|Active"
```

**Functionality Assured**:
- Bidirectional support foundation laid (Z-segment data preserved in FHIR).
- Legacy system data preservation achieved.

---

**Next Steps**:
- Define standard StructureDefinitions (profiles) for the extensions if needed for external validation.
- Add support for other Z-segments as needed by defining new model classes.
