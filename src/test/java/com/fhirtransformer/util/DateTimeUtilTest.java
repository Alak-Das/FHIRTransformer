package com.fhirtransformer.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DateTimeUtil - Core functionality only.
 * 
 * @author FHIR Transformer Team
 * @version 1.1.0
 */
public class DateTimeUtilTest {

    @Test
    public void testParseHl7DateTime_WithTimezone() {
        String hl7DateTime = "20260116120000-0500";
        ZonedDateTime result = DateTimeUtil.parseHl7DateTime(hl7DateTime);

        assertNotNull(result);
        assertEquals(2026, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(16, result.getDayOfMonth());
        assertEquals(12, result.getHour());
        assertEquals("-05:00", result.getOffset().getId());
    }

    @Test
    public void testParseHl7DateTime_WithPositiveTimezone() {
        String hl7DateTime = "20260116120000+0530";
        ZonedDateTime result = DateTimeUtil.parseHl7DateTime(hl7DateTime);

        assertNotNull(result);
        assertEquals("+05:30", result.getOffset().getId());
    }

    @Test
    public void testParseHl7DateTime_WithoutTimezone() {
        String hl7DateTime = "20260116120000";
        ZonedDateTime result = DateTimeUtil.parseHl7DateTime(hl7DateTime);

        assertNotNull(result);
        assertEquals(2026, result.getYear());
        assertNotNull(result.getZone());
    }

    @Test
    public void testParseHl7DateTime_Null() {
        ZonedDateTime result = DateTimeUtil.parseHl7DateTime(null);
        assertNull(result);
    }

    @Test
    public void testParseHl7Date() {
        String hl7Date = "20260116";
        LocalDate result = DateTimeUtil.parseHl7Date(hl7Date);

        assertNotNull(result);
        assertEquals(2026, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(16, result.getDayOfMonth());
    }

    @Test
    public void testFormatToHl7DateTime() {
        ZonedDateTime zdt = ZonedDateTime.of(2026, 1, 16, 12, 0, 0, 0,
                ZoneId.of("America/New_York"));

        String result = DateTimeUtil.formatToHl7DateTime(zdt);

        assertNotNull(result);
        assertTrue(result.startsWith("20260116120000"));
        assertTrue(result.contains("-") || result.contains("+"));
    }

    @Test
    public void testFormatToHl7Date() {
        LocalDate date = LocalDate.of(2026, 1, 16);
        String result = DateTimeUtil.formatToHl7Date(date);

        assertEquals("20260116", result);
    }

    @Test
    public void testGetCurrentHl7DateTime() {
        String result = DateTimeUtil.getCurrentHl7DateTime();

        assertNotNull(result);
        assertTrue(result.length() >= 14);
        assertTrue(result.matches("\\d{14}.*"));
    }

    @Test
    public void testGetCurrentHl7Date() {
        String result = DateTimeUtil.getCurrentHl7Date();

        assertNotNull(result);
        assertEquals(8, result.length());
        assertTrue(result.matches("\\d{8}"));
    }

    @Test
    public void testTimezonePreservation() {
        ZonedDateTime original = ZonedDateTime.of(2026, 1, 16, 12, 0, 0, 0,
                ZoneId.of("America/New_York"));

        String hl7 = DateTimeUtil.formatToHl7DateTime(original);

        assertTrue(hl7.contains("-05") || hl7.contains("-04"));
    }

    @Test
    public void testParseAndFormat_RoundTrip() {
        String original = "20260116120000-0500";

        ZonedDateTime parsed = DateTimeUtil.parseHl7DateTime(original);
        String formatted = DateTimeUtil.formatToHl7DateTime(parsed);

        assertTrue(formatted.startsWith("20260116120000"));
        assertTrue(formatted.contains("-05"));
    }
}
