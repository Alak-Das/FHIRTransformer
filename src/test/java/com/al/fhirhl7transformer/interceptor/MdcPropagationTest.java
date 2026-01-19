package com.al.fhirhl7transformer.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(MdcPropagationTest.TestConfig.class)
public class MdcPropagationTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private AsyncTestService asyncTestService;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }

    @Test
    public void testMdcGenerationAndPropagation() throws Exception {
        // 1. Test MDC Generation for HTTP Request
        mockMvc.perform(get("/api/test-mdc-endpoint"))
                .andExpect(status().isOk())
                .andExpect(status().isOk())
                .andExpect(header().exists("transformerId")); // Verify header was set by interceptor
    }

    @Test
    public void testAsyncPropagation() throws InterruptedException, ExecutionException, TimeoutException {
        String transformerId = "test-uuid-12345";
        MDC.put("transformerId", transformerId);

        try {
            CompletableFuture<String> future = asyncTestService.getMdcValue();
            String asyncTransformerId = future.get(5, TimeUnit.SECONDS);
            assertEquals(transformerId, asyncTransformerId, "MDC transformerId should be propagated to async thread");
        } finally {
            MDC.clear();
        }
    }

    @TestConfiguration
    @EnableAsync // Ensure async is enabled for test
    public static class TestConfig {
        @Bean
        public AsyncTestService asyncTestService() {
            return new AsyncTestService();
        }

        @Bean
        public TestController testController() {
            return new TestController();
        }
    }

    public static class AsyncTestService {
        @Async
        public CompletableFuture<String> getMdcValue() {
            return CompletableFuture.completedFuture(MDC.get("transformerId"));
        }
    }

    @RestController
    public static class TestController {
        @GetMapping("/api/test-mdc-endpoint")
        public String check() {
            return "OK";
        }
    }
}
