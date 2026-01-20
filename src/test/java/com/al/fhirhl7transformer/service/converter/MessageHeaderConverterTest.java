package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MessageHeader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MessageHeaderConverterTest {

    @Test
    public void testConvert() throws Exception {
        MessageHeaderConverter converter = new MessageHeaderConverter();
        ADT_A01 adt = new ADT_A01();
        adt.initQuickstart("ADT", "A01", "P");
        Terser terser = new Terser(adt);

        // Default Init sets MSH fields clearly?
        // Let's set explicitly to be sure
        terser.set("MSH-3", "SENDING_APP");
        terser.set("MSH-5", "RECEIVING_APP");
        terser.set("MSH-7", "20230101120000");

        Bundle bundle = new Bundle();
        ConversionContext context = ConversionContext.builder().build();

        List<MessageHeader> results = converter.convert(terser, bundle, context);

        assertFalse(results.isEmpty());
        MessageHeader mh = results.get(0);

        assertEquals("SENDING_APP", mh.getSource().getName());
        assertEquals("RECEIVING_APP", mh.getDestinationFirstRep().getName());
        assertEquals("ADT", mh.getEventCoding().getCode().substring(0, 3));
    }
}
