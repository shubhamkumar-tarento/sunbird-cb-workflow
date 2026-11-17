package org.sunbird.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowApplicationTest {


    @Test
    void testObjectMapperBean() {
        WorkflowApplication app = new WorkflowApplication();
        ObjectMapper objectMapper = app.objectMapper();

        assertNotNull(objectMapper);
        assertTrue(objectMapper.canSerialize(java.time.Instant.class));
    }

    @Test
    void testRestTemplateBean() {
        WorkflowApplication app = new WorkflowApplication();
        RestTemplate restTemplate = app.restTemplate();

        assertNotNull(restTemplate);
        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        assertNotNull(factory);
    }

    @Test
    void testPrivateClientHttpRequestFactory() throws Exception {
        WorkflowApplication app = new WorkflowApplication();

        // Access and invoke private method getClientHttpRequestFactory()
        Method method = WorkflowApplication.class.getDeclaredMethod("getClientHttpRequestFactory");
        method.setAccessible(true);
        ClientHttpRequestFactory factory = (ClientHttpRequestFactory) method.invoke(app);

        assertNotNull(factory);
    }
}
