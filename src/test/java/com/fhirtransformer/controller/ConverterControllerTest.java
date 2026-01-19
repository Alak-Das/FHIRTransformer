package com.fhirtransformer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhirtransformer.dto.EnrichedMessage;
import com.fhirtransformer.model.enums.MessageType;
import com.fhirtransformer.model.enums.TransactionStatus;
import com.fhirtransformer.service.AuditService;
import com.fhirtransformer.service.FhirToHl7Service;
import com.fhirtransformer.service.Hl7ToFhirService;
import com.fhirtransformer.service.MessageEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fhirtransformer.service.BatchConversionService;

public class ConverterControllerTest {

        private MockMvc mockMvc;

        @Mock
        private Hl7ToFhirService hl7ToFhirService;

        @Mock
        private FhirToHl7Service fhirToHl7Service;

        @Mock
        private BatchConversionService batchConversionService;

        @Mock
        private RabbitTemplate rabbitTemplate;

        @Mock
        private MessageEnrichmentService messageEnrichmentService;

        @Mock
        private AuditService auditService;

        @Mock
        private com.fhirtransformer.service.IdempotencyService idempotencyService;

        private ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        public void setup() {
                MockitoAnnotations.openMocks(this);
                ConverterController controller = new ConverterController(
                                hl7ToFhirService, fhirToHl7Service, batchConversionService, rabbitTemplate,
                                messageEnrichmentService, auditService, idempotencyService, objectMapper);

                ReflectionTestUtils.setField(controller, "exchange", "test-exchange");
                ReflectionTestUtils.setField(controller, "routingKey", "test-routing-key");
                ReflectionTestUtils.setField(controller, "fhirExchange", "test-fhir-exchange");
                ReflectionTestUtils.setField(controller, "fhirRoutingKey", "test-fhir-routing-key");

                this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        }

        @Test
        public void testConvertToFhir_Async_Success() throws Exception {
                String hl7Message = "MSH|^~\\&|...";
                String transactionId = "tx-123";
                EnrichedMessage enriched = new EnrichedMessage(hl7Message, transactionId);

                when(messageEnrichmentService.ensureHl7TransactionId(anyString())).thenReturn(enriched);

                mockMvc.perform(post("/api/convert/v2-to-fhir")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(hl7Message)
                                .principal(() -> "tenant1"))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.status").value("Accepted"))
                                .andExpect(jsonPath("$.transactionId").value(transactionId))
                                .andExpect(header().string("transformerId", transactionId));

                verify(auditService).logTransaction(eq("tenant1"), eq(transactionId), eq(MessageType.V2_TO_FHIR_ASYNC),
                                eq(TransactionStatus.ACCEPTED), isNull());
                verify(rabbitTemplate).convertAndSend(eq("test-exchange"), eq("test-routing-key"), eq(hl7Message),
                                org.mockito.ArgumentMatchers
                                                .any(org.springframework.amqp.core.MessagePostProcessor.class));
        }

        @Test
        public void testConvertToFhir_Sync_Success() throws Exception {
                String hl7Message = "MSH|^~\\&|...";
                String transactionId = "tx-123";
                String fhirJson = "{\"resourceType\":\"Bundle\"}";
                EnrichedMessage enriched = new EnrichedMessage(hl7Message, transactionId);

                when(messageEnrichmentService.ensureHl7TransactionId(anyString())).thenReturn(enriched);
                when(hl7ToFhirService.convertHl7ToFhir(anyString())).thenReturn(fhirJson);

                mockMvc.perform(post("/api/convert/v2-to-fhir-sync")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(hl7Message)
                                .principal(() -> "tenant1"))
                                .andExpect(status().isOk())
                                .andExpect(content().json(fhirJson))
                                .andExpect(header().string("transformerId", transactionId));

                verify(auditService).logTransaction(eq("tenant1"), eq(transactionId), eq(MessageType.V2_TO_FHIR_SYNC),
                                eq(TransactionStatus.COMPLETED));
        }

        @Test
        public void testConvertToHl7_Async_Success() throws Exception {
                String fhirJson = "{\"resourceType\":\"Bundle\"}";
                String transactionId = "tx-456";
                EnrichedMessage enriched = new EnrichedMessage(fhirJson, transactionId);

                when(messageEnrichmentService.ensureFhirTransactionId(anyString())).thenReturn(enriched);

                mockMvc.perform(post("/api/convert/fhir-to-v2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fhirJson)
                                .principal(() -> "tenant1"))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.status").value("Accepted"))
                                .andExpect(jsonPath("$.transactionId").value(transactionId))
                                .andExpect(header().string("transformerId", transactionId));

                verify(auditService).logTransaction(eq("tenant1"), eq(transactionId), eq(MessageType.FHIR_TO_V2_ASYNC),
                                eq(TransactionStatus.QUEUED), isNull());
                verify(rabbitTemplate).convertAndSend(eq("test-fhir-exchange"), eq("test-fhir-routing-key"),
                                eq(fhirJson));
        }

        @Test
        public void testConvertToHl7_Sync_Success() throws Exception {
                String fhirJson = "{\"resourceType\":\"Bundle\"}";
                String transactionId = "tx-456";
                String hl7Message = "MSH|^~\\&|...";
                EnrichedMessage enriched = new EnrichedMessage(fhirJson, transactionId);

                when(messageEnrichmentService.ensureFhirTransactionId(anyString())).thenReturn(enriched);
                when(fhirToHl7Service.convertFhirToHl7(anyString())).thenReturn(hl7Message);

                mockMvc.perform(post("/api/convert/fhir-to-v2-sync")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fhirJson)
                                .principal(() -> "tenant1"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(hl7Message))
                                .andExpect(header().string("transformerId", transactionId));

                verify(auditService).logTransaction(eq("tenant1"), eq(transactionId), eq(MessageType.FHIR_TO_V2_SYNC),
                                eq(TransactionStatus.COMPLETED));
        }
}
