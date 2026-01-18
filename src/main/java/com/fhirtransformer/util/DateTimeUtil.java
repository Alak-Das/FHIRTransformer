package com.fhirtransformer.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * Utility class for parsing and formatting HL7 v2.5 date/time values with
 * timezone support.
 * 
 * <p>
 * HL7 v2.5 datetime format: YYYYMMDDHHmmss[.SSSS][+/-ZZZZ]
 * Examples:
 * <ul>
 * <li>20260116120000 - No timezone (uses system default)</li>
 * <li>20260116120000-0500 - EST timezone</li>
 * <li>20260116120000.1234+0530 - With milliseconds and IST timezone</li>
 * </ul>
 * 
 * @author FHIR Transformer Team
 * @version 1.1.0
 * @since 1.1.0
 */
public final class DateTimeUtil {

    /**
     * Private constructor to prevent instantiation.
     */
    private DateTimeUtil() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    // HL7 v2.5 datetime format with optional timezone
    private static final DateTimeFormatter HL7_DATETIME_WITH_TZ = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendLiteral('.')
            .appendValue(ChronoField.MILLI_OF_SECOND, 4)
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HHmm", "Z")
            .optionalEnd()
            .toFormatter();

    // HL7 v2.5 date format (YYYYMMDD)
    private static final DateTimeFormatter HL7_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    // HL7 v2.5 datetime format without timezone
    private static final DateTimeFormatter HL7_DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Parse HL7 datetime string to ZonedDateTime, preserving timezone if present.
     * 
     * @param hl7DateTime HL7 datetime string (e.g., "20260116120000-0500")
     * @return ZonedDateTime with preserved timezone, or system default if no
     *         timezone specified
     * @throws DateTimeException if the string cannot be parsed
     */
    public static ZonedDateTime parseHl7DateTime(String hl7DateTime) {
        if (hl7DateTime == null || hl7DateTime.isEmpty()) {
            return null;
        }

        try {
            // Try parsing with timezone first
            if (hl7DateTime.length() >= 19 && (hl7DateTime.contains("+") || hl7DateTime.contains("-"))) {
                return ZonedDateTime.parse(hl7DateTime, HL7_DATETIME_WITH_TZ);
            }

            // No timezone - use system default
            String cleanDateTime = hl7DateTime;

            // Pad with zeros if less than 14 characters (YYYYMMDDHHmmss)
            if (cleanDateTime.length() < 14) {
                // Determine how many zeros to add
                // 8 chars (YYYYMMDD) -> add 6 zeros
                // 10 chars (YYYYMMDDHH) -> add 4 zeros
                // 12 chars (YYYYMMDDHHmm) -> add 2 zeros
                int padding = 14 - cleanDateTime.length();
                StringBuilder sb = new StringBuilder(cleanDateTime);
                for (int i = 0; i < padding; i++) {
                    sb.append('0');
                }
                cleanDateTime = sb.toString();
            }

            LocalDateTime localDateTime = LocalDateTime
                    .parse(cleanDateTime.substring(0, 14), HL7_DATETIME);
            return localDateTime.atZone(ZoneId.systemDefault());

        } catch (Exception e) {
            throw new DateTimeException("Failed to parse HL7 datetime: " + hl7DateTime, e);
        }
    }

    /**
     * Parse HL7 date string to LocalDate.
     * 
     * @param hl7Date HL7 date string (e.g., "20260116")
     * @return LocalDate
     * @throws DateTimeException if the string cannot be parsed
     */
    public static LocalDate parseHl7Date(String hl7Date) {
        if (hl7Date == null || hl7Date.isEmpty()) {
            return null;
        }

        try {
            return LocalDate.parse(hl7Date, HL7_DATE);
        } catch (Exception e) {
            throw new DateTimeException("Failed to parse HL7 date: " + hl7Date, e);
        }
    }

    /**
     * Format ZonedDateTime to HL7 datetime string with timezone.
     * 
     * @param zonedDateTime ZonedDateTime to format
     * @return HL7 datetime string with timezone (e.g., "20260116120000-0500")
     */
    public static String formatToHl7DateTime(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }
        return zonedDateTime.format(HL7_DATETIME_WITH_TZ);
    }

    /**
     * Format LocalDate to HL7 date string.
     * 
     * @param localDate LocalDate to format
     * @return HL7 date string (e.g., "20260116")
     */
    public static String formatToHl7Date(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.format(HL7_DATE);
    }

    /**
     * Convert FHIR DateTimeType to HL7 datetime string with timezone.
     * 
     * @param fhirDateTime FHIR DateTimeType
     * @return HL7 datetime string with timezone
     */
    public static String fhirDateTimeToHl7(org.hl7.fhir.r4.model.DateTimeType fhirDateTime) {
        if (fhirDateTime == null || !fhirDateTime.hasValue()) {
            return null;
        }

        // FHIR DateTimeType includes timezone information
        ZonedDateTime zdt = fhirDateTime.getValue().toInstant()
                .atZone(fhirDateTime.getTimeZone() != null
                        ? fhirDateTime.getTimeZone().toZoneId()
                        : ZoneId.systemDefault());

        return formatToHl7DateTime(zdt);
    }

    /**
     * Convert HL7 datetime string to FHIR DateTimeType with timezone.
     * 
     * @param hl7DateTime HL7 datetime string
     * @return FHIR DateTimeType with preserved timezone
     */
    public static org.hl7.fhir.r4.model.DateTimeType hl7DateTimeToFhir(String hl7DateTime) {
        if (hl7DateTime == null || hl7DateTime.isEmpty()) {
            return null;
        }

        ZonedDateTime zdt = parseHl7DateTime(hl7DateTime);
        org.hl7.fhir.r4.model.DateTimeType fhirDateTime = new org.hl7.fhir.r4.model.DateTimeType();
        fhirDateTime.setValue(java.util.Date.from(zdt.toInstant()));
        fhirDateTime.setTimeZone(java.util.TimeZone.getTimeZone(zdt.getZone()));

        return fhirDateTime;
    }

    /**
     * Get current datetime in HL7 format with system timezone.
     * 
     * @return Current datetime in HL7 format
     */
    public static String getCurrentHl7DateTime() {
        return formatToHl7DateTime(ZonedDateTime.now());
    }

    /**
     * Get current date in HL7 format.
     * 
     * @return Current date in HL7 format
     */
    public static String getCurrentHl7Date() {
        return formatToHl7Date(LocalDate.now());
    }
}
