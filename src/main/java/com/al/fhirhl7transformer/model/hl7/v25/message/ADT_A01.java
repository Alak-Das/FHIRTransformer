package com.al.fhirhl7transformer.model.hl7.v25.message;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.parser.ModelClassFactory;
import ca.uhn.hl7v2.parser.DefaultModelClassFactory;
import com.al.fhirhl7transformer.model.hl7.v25.segment.ZPI;

/**
 * Custom ADT_A01 message structure including ZPI segment.
 * Extends the standard HAPI ADT_A01 but adds ZPI at the end.
 */
public class ADT_A01 extends ca.uhn.hl7v2.model.v25.message.ADT_A01 {

    public ADT_A01() {
        super(new DefaultModelClassFactory());
        init();
    }

    public ADT_A01(ModelClassFactory factory) {
        super(factory);
        init();
    }

    private void init() {
        try {
            // Add ZPI segment at the end of the message
            this.add(ZPI.class, false, false);
        } catch (HL7Exception e) {
            log.error("Unexpected error creating Custom ADT_A01 - this is probably a bug in the source code generator.",
                    e);
        }
    }

    /**
     * Get ZPI Segment.
     */
    public ZPI getZPI() {
        return getTyped("ZPI", ZPI.class);
    }
}
