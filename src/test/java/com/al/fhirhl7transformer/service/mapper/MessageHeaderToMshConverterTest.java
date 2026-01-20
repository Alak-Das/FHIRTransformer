package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MessageHeader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageHeaderToMshConverterTest {

    @Test
    public void testConvert() throws Exception {
        MessageHeaderToMshConverter converter = new MessageHeaderToMshConverter();
        ADT_A01 adt = new ADT_A01();
        adt.initQuickstart("ADT", "A01", "P");
        Terser terser = new Terser(adt);

        MessageHeader mh = new MessageHeader();
        MessageHeader.MessageSourceComponent source = new MessageHeader.MessageSourceComponent();
        source.setName("TestApp");
        source.setSoftware("TestFacility");
        mh.setSource(source);

        mh.setEvent(new Coding().setCode("ADT^A01"));

        converter.convert(mh, adt, terser);

        assertEquals("TestApp", terser.get("MSH-3-1"));
        assertEquals("TestFacility", terser.get("MSH-4-1"));
        assertEquals("ADT", terser.get("MSH-9-1"));
        assertEquals("A01", terser.get("MSH-9-2"));
    }
}
