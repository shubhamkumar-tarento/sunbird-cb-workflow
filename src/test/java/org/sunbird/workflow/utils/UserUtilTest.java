package org.sunbird.workflow.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.service.impl.RequestServiceImpl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserUtilTest {

    @InjectMocks
    private UserUtil userUtil;

    @Mock
    private Configuration configuration;

    @Mock
    private RequestServiceImpl requestServiceImpl;

    @Spy
    private ObjectMapper mapper = new ObjectMapper();

    @Test
    void userProfileRead_returnsUserMap_whenResponseValid() {
        String userId = "user-123";
        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/v1/read/userId");

        Map<String, Object> userResp = new HashMap<>();
        userResp.put("firstName", "Alice");

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.RESPONSE, userResp);

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, resultMap);

        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(response);

        Map<String, Object> out = userUtil.userProfileRead(userId);
        assertNotNull(out);
        assertEquals("Alice", out.get("firstName"));
    }

    @Test
    void userProfileRead_returnsNull_whenFetchReturnsNull() {
        String userId = "user-null";
        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/v1/read/userId");
        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(null);

        Map<String, Object> out = userUtil.userProfileRead(userId);
        assertTrue(out.isEmpty());
    }

    @Test
    void userProfileRead_returnsNull_whenResultNotMap() {
        String userId = "user-2";
        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/v1/read/userId");

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, "not-a-map");

        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(response);

        Map<String, Object> out = userUtil.userProfileRead(userId);
        assertTrue(out.isEmpty());
    }

    @Test
    void userProfileRead_returnsNull_whenResultMapEmpty() {
        String userId = "user-3";
        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/v1/read/userId");

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, Collections.emptyMap());

        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(response);

        Map<String, Object> out = userUtil.userProfileRead(userId);
        assertTrue(out.isEmpty());
    }

    @Test
    void userProfileRead_returnsNull_whenResponseNotMap() {
        String userId = "user-4";
        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/v1/read/userId");

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.RESPONSE, "not-a-map");
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, resultMap);

        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(response);

        Map<String, Object> out = userUtil.userProfileRead(userId);
        assertTrue(out.isEmpty());
    }
}

