package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import java.util.List;

public interface SegmentConverter<T extends Resource> {
    /**
     * Converts an HL7 segment to a list of FHIR resources.
     *
     * @param terser  HAPI Terser for parsing HL7 message
     * @param bundle  The FHIR Bundle being constructed (context reference)
     * @param context Shared conversion context (e.g. Patient ID)
     * @return List of generated FHIR resources
     */
    List<T> convert(Terser terser, Bundle bundle, ConversionContext context);
}
