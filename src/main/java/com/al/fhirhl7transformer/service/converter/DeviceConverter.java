package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Converter for creating FHIR Device resources from HL7 OBX-18 (Equipment
 * Instance Identifier)
 * and other device-related fields.
 * 
 * Device information can come from:
 * - OBX-18: Equipment Instance Identifier
 * - OBX-17: Observation Method (may contain device info)
 */
@Component
@Slf4j
public class DeviceConverter implements SegmentConverter<Device> {

    private final Set<String> processedDevices = new HashSet<>();

    @Override
    public List<Device> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Device> devices = new ArrayList<>();
        processedDevices.clear();

        int obxIndex = 0;
        while (obxIndex < 100) { // Safety limit
            try {
                String obxPath = "/.OBX(" + obxIndex + ")";
                boolean found = false;

                try {
                    if (terser.getSegment(obxPath) != null) {
                        found = true;
                    }
                } catch (Exception e) {
                    // Try ORDER_OBSERVATION group paths
                    String orderPath = "/.ORDER_OBSERVATION/OBSERVATION(" + obxIndex + ")/OBX";
                    String r01Path = "/.PATIENT_RESULT/ORDER_OBSERVATION/OBSERVATION(" + obxIndex + ")/OBX";
                    String r01PathIndexed = "/.PATIENT_RESULT(0)/ORDER_OBSERVATION/OBSERVATION(" + obxIndex + ")/OBX";
                    try {
                        if (terser.getSegment(orderPath) != null) {
                            obxPath = orderPath;
                            found = true;
                        } else if (terser.getSegment(r01Path) != null) {
                            obxPath = r01Path;
                            found = true;
                        } else if (terser.getSegment(r01PathIndexed) != null) {
                            obxPath = r01PathIndexed;
                            found = true;
                        }
                    } catch (Exception ex) {
                        // Not found
                    }
                }

                if (!found)
                    break;

                // OBX-18: Equipment Instance Identifier
                String equipmentId = terser.get(obxPath + "-18-1");

                if (!isEmpty(equipmentId) && !processedDevices.contains(equipmentId)) {
                    processedDevices.add(equipmentId);

                    log.debug("Processing Device from OBX({}) OBX-18: {}", obxIndex, equipmentId);

                    Device device = new Device();
                    device.setId(UUID.randomUUID().toString());
                    device.setStatus(Device.FHIRDeviceStatus.ACTIVE);

                    // Equipment Instance Identifier components
                    // OBX-18-1: Entity Identifier (serial number or unique ID)
                    device.setSerialNumber(equipmentId);
                    device.addIdentifier()
                            .setSystem("urn:oid:equipment-id")
                            .setValue(equipmentId);

                    // OBX-18-2: Namespace ID (often manufacturer or device type)
                    String namespace = terser.get(obxPath + "-18-2");
                    if (!isEmpty(namespace)) {
                        device.setManufacturer(namespace);
                    }

                    // OBX-18-3: Universal ID (often model number)
                    String universalId = terser.get(obxPath + "-18-3");
                    if (!isEmpty(universalId)) {
                        device.addDeviceName()
                                .setName(universalId)
                                .setType(Device.DeviceNameType.MODELNAME);
                    }

                    // OBX-18-4: Universal ID Type
                    String idType = terser.get(obxPath + "-18-4");
                    if (!isEmpty(idType)) {
                        device.addIdentifier()
                                .setType(new CodeableConcept()
                                        .addCoding(new Coding()
                                                .setCode(idType)
                                                .setDisplay("ID Type: " + idType)))
                                .setValue(equipmentId);
                    }

                    // Try to get additional info from OBX-17 (Observation Method)
                    String methodCode = terser.get(obxPath + "-17-1");
                    String methodDisplay = terser.get(obxPath + "-17-2");
                    if (!isEmpty(methodCode) || !isEmpty(methodDisplay)) {
                        CodeableConcept method = new CodeableConcept();
                        if (!isEmpty(methodCode)) {
                            method.addCoding().setCode(methodCode);
                        }
                        if (!isEmpty(methodDisplay)) {
                            method.setText(methodDisplay);
                        }
                        // Store method as device property
                        Device.DevicePropertyComponent prop = device.addProperty();
                        prop.setType(new CodeableConcept()
                                .addCoding(new Coding()
                                        .setSystem("http://terminology.hl7.org/CodeSystem/device-property-type")
                                        .setCode("method")
                                        .setDisplay("Observation Method")));
                        prop.addValueCode(method);
                    }

                    // Link to patient as owner
                    if (context != null && context.getPatientId() != null) {
                        device.setPatient(new Reference("Patient/" + context.getPatientId()));
                    }

                    devices.add(device);
                }

                obxIndex++;

            } catch (Exception e) {
                log.error("Error processing OBX segment at index {} for device info", obxIndex, e);
                obxIndex++;
            }
        }

        log.info("Created {} Device resources from OBX-18 equipment identifiers", devices.size());
        return devices;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
