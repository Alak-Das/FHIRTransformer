package com.fhirtransformer.service.converter;

import ca.uhn.hl7v2.model.Message;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversionContext {
    private String patientId;
    private String transactionId;
    private Message hapiMessage;
}
