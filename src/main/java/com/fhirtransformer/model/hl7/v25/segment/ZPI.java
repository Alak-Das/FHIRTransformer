package com.fhirtransformer.model.hl7.v25.segment;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.AbstractSegment;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.v25.datatype.ST;
import ca.uhn.hl7v2.parser.ModelClassFactory;

/**
 * ZPI (Z-Segment Patient Information) - Custom Segment.
 * 
 * <p>
 * Fields:
 * <ol>
 * <li>Set ID - ZPI (SI) - Optional</li>
 * <li>Pet Name (ST) - Optional</li>
 * <li>VIP Level (ST) - Optional</li>
 * <li>Archive Status (ST) - Optional</li>
 * </ol>
 */
public class ZPI extends AbstractSegment {

    public ZPI(Group parent, ModelClassFactory factory) {
        super(parent, factory);
        init(factory);
    }

    private void init(ModelClassFactory factory) {
        try {
            // Field 1: Set ID
            add(ca.uhn.hl7v2.model.v25.datatype.SI.class, false, 1, 4, new Object[] { getMessage() }, "Set ID - ZPI");

            // Field 2: Pet Name
            add(ST.class, false, 1, 50, new Object[] { getMessage() }, "Pet Name");

            // Field 3: VIP Level
            add(ST.class, false, 1, 10, new Object[] { getMessage() }, "VIP Level");

            // Field 4: Archive Status
            add(ST.class, false, 1, 10, new Object[] { getMessage() }, "Archive Status");

        } catch (HL7Exception e) {
            log.error("Unexpected error creating ZPI - this is probably a bug in the source code generator.", e);
        }
    }

    /**
     * Get Set ID - ZPI (ZPI-1).
     */
    public ca.uhn.hl7v2.model.v25.datatype.SI getSetIDZPI() {
        return getTypedField(1, 0);
    }

    /**
     * Get Pet Name (ZPI-2).
     */
    public ST getPetName() {
        return getTypedField(2, 0);
    }

    /**
     * Get VIP Level (ZPI-3).
     */
    public ST getVipLevel() {
        return getTypedField(3, 0);
    }

    /**
     * Get Archive Status (ZPI-4).
     */
    public ST getArchiveStatus() {
        return getTypedField(4, 0);
    }
}
