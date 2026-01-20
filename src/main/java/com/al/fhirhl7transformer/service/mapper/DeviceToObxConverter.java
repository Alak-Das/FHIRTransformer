package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

/**
 * Converts FHIR Device to HL7 OBX-18 (Equipment Instance Identifier) within an
 * OBX segment.
 */
@Component
public class DeviceToObxConverter implements FhirToHl7Converter<Device> {

    private int obxIndex = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Device;
    }

    @Override
    public void convert(Device device, Message message, Terser terser) throws HL7Exception {
        // Device is typically embedded in OBX-18 of an existing observation
        // But we can also create a standalone OBX for device information
        String obxPath = "/.OBX(" + obxIndex + ")";

        // OBX-1 Set ID
        terser.set(obxPath + "-1", String.valueOf(obxIndex + 1));

        // OBX-2 Value Type (ST for string device info)
        terser.set(obxPath + "-2", "ST");

        // OBX-3 Observation Identifier (Device type)
        if (device.hasType()) {
            CodeableConcept type = device.getType();
            if (type.hasCoding()) {
                Coding coding = type.getCodingFirstRep();
                terser.set(obxPath + "-3-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(obxPath + "-3-2", coding.getDisplay());
                }
            }
        } else {
            // Default identifier for device observation
            terser.set(obxPath + "-3-1", "DEVICE_INFO");
            terser.set(obxPath + "-3-2", "Device Information");
        }

        // OBX-5 Observation Value (Device model/description)
        StringBuilder deviceInfo = new StringBuilder();
        if (device.hasManufacturer()) {
            deviceInfo.append(device.getManufacturer());
        }
        if (device.hasModelNumber()) {
            if (deviceInfo.length() > 0)
                deviceInfo.append(" ");
            deviceInfo.append(device.getModelNumber());
        }
        if (device.hasDeviceName()) {
            for (Device.DeviceDeviceNameComponent name : device.getDeviceName()) {
                if (name.hasName()) {
                    if (deviceInfo.length() > 0)
                        deviceInfo.append(" - ");
                    deviceInfo.append(name.getName());
                    break;
                }
            }
        }
        if (deviceInfo.length() > 0) {
            terser.set(obxPath + "-5", deviceInfo.toString());
        }

        // OBX-11 Observation Result Status
        if (device.hasStatus()) {
            String status = device.getStatus().toCode();
            if ("active".equals(status)) {
                terser.set(obxPath + "-11", "F"); // Final
            } else if ("inactive".equals(status)) {
                terser.set(obxPath + "-11", "X"); // Cannot obtain
            } else if ("entered-in-error".equals(status)) {
                terser.set(obxPath + "-11", "D"); // Delete
            }
        } else {
            terser.set(obxPath + "-11", "F");
        }

        // OBX-18 Equipment Instance Identifier (Primary device identifier location)
        if (device.hasIdentifier()) {
            Identifier id = device.getIdentifierFirstRep();
            if (id.hasValue()) {
                terser.set(obxPath + "-18-1", id.getValue());
            }
            if (id.hasSystem()) {
                terser.set(obxPath + "-18-2", id.getSystem());
            }
        } else if (device.hasId()) {
            terser.set(obxPath + "-18-1", device.getIdElement().getIdPart());
        }

        // OBX-18-3 Universal ID Type
        if (device.hasUdiCarrier()) {
            Device.DeviceUdiCarrierComponent udi = device.getUdiCarrierFirstRep();
            if (udi.hasDeviceIdentifier()) {
                terser.set(obxPath + "-18-1", udi.getDeviceIdentifier());
                terser.set(obxPath + "-18-3", "UDI");
            }
            if (udi.hasCarrierHRF()) {
                // Human readable form in OBX-18-4
                terser.set(obxPath + "-18-4", udi.getCarrierHRF());
            }
        }

        // Additional device properties in separate OBX segments could be added here
        // For now, we keep essential device info in a single OBX

        // OBX-23 Performing Organization Name (Manufacturer as org)
        if (device.hasManufacturer()) {
            terser.set(obxPath + "-23-1", device.getManufacturer());
        }

        // OBX-24 Performing Organization Address
        if (device.hasOwner()) {
            Reference owner = device.getOwner();
            if (owner.hasDisplay()) {
                terser.set(obxPath + "-24-1", owner.getDisplay());
            }
        }

        obxIndex++;
    }
}
