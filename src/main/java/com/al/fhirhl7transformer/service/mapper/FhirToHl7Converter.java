package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Resource;

public interface FhirToHl7Converter<T extends Resource> {
    void convert(T resource, Message message, Terser terser) throws HL7Exception;

    boolean canConvert(Resource resource);
}
