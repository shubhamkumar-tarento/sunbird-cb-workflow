package org.sunbird.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.QrSelfEnrolRequest;
import org.sunbird.workflow.models.Response;
import org.sunbird.workflow.service.BPWorkFlowService;
import org.sunbird.workflow.service.QrCodeSelfEnrolmentService;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BPWorkFlowControllerQrEnrolTest {

    @Mock
    private BPWorkFlowService bPWorkFlowService;

    @Mock
    private QrCodeSelfEnrolmentService qrCodeSelfEnrolmentService;

    @InjectMocks
    private BPWorkFlowController bPWorkFlowController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bPWorkFlowController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testBlendedProgramQrEnrol_Success() throws Exception {
        String rootOrg = "igot";
        String org = "dopt";
        String authToken = "auth-token-123";
        String courseId = "course123";
        String batchId = "batch123";

        QrSelfEnrolRequest qrRequest = new QrSelfEnrolRequest(courseId, batchId);

        Response serviceResponse = new Response();
        serviceResponse.put(Constants.STATUS, HttpStatus.OK);
        serviceResponse.put(Constants.MESSAGE, String.format(Constants.QR_ENROLLMENT_SUCCESS_MESSAGE));
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.STATUS, Constants.APPROVED);
        data.put(Constants.WF_IDS_CONSTANT, "wf-id-123");
        serviceResponse.put(Constants.DATA, data);

        when(qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(eq(rootOrg), eq(org), eq(authToken), any(QrSelfEnrolRequest.class)))
                .thenReturn(serviceResponse);

        mockMvc.perform(post("/v1/blendedprogram/workflow/qr/enrolments")
                        .header("rootOrg", rootOrg)
                        .header("org", org)
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.data.status").value(Constants.APPROVED));

        verify(qrCodeSelfEnrolmentService).enrolQrCodeBPWorkFlow(eq(rootOrg), eq(org), eq(authToken), any(QrSelfEnrolRequest.class));
    }

    @Test
    void testBlendedProgramQrEnrol_BadRequest_MissingHeaders() throws Exception {
        QrSelfEnrolRequest qrRequest = new QrSelfEnrolRequest("course123", "batch123");

        mockMvc.perform(post("/v1/blendedprogram/workflow/qr/enrolments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testBlendedProgramQrEnrol_BadRequest_InvalidRequest() throws Exception {
        String rootOrg = "igot";
        String org = "dopt";
        String authToken = "auth-token-123";

        Response serviceResponse = new Response();
        serviceResponse.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
        serviceResponse.put(Constants.ERROR_MESSAGE, "Course ID is required for QR enrolment");

        when(qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(anyString(), anyString(), anyString(), any()))
                .thenReturn(serviceResponse);

        QrSelfEnrolRequest invalidRequest = new QrSelfEnrolRequest("", "batch123");

        mockMvc.perform(post("/v1/blendedprogram/workflow/qr/enrolments")
                        .header("rootOrg", rootOrg)
                        .header("org", org)
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.errmsg").value("Course ID is required for QR enrolment"));
    }

    @Test
    void testBlendedProgramQrEnrol_UserIdFromHeader() throws Exception {
        String rootOrg = "igot";
        String org = "dopt";
        String authToken = "auth-token-123";
        String courseId = "course123";
        String batchId = "batch123";

        QrSelfEnrolRequest qrRequest = new QrSelfEnrolRequest(courseId, batchId);

        Response serviceResponse = new Response();
        serviceResponse.put(Constants.STATUS, HttpStatus.OK);
        serviceResponse.put(Constants.MESSAGE, String.format(Constants.QR_ENROLLMENT_SUCCESS_MESSAGE));
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.STATUS, Constants.APPROVED);
        data.put(Constants.WF_IDS_CONSTANT, "wf-id-456");
        serviceResponse.put(Constants.DATA, data);

        when(qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(eq(rootOrg), eq(org), eq(authToken), any(QrSelfEnrolRequest.class)))
                .thenReturn(serviceResponse);

        mockMvc.perform(post("/v1/blendedprogram/workflow/qr/enrolments")
                        .header("rootOrg", rootOrg)
                        .header("org", org)
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrRequest)))
                .andExpect(status().isOk());

        verify(qrCodeSelfEnrolmentService).enrolQrCodeBPWorkFlow(eq(rootOrg), eq(org), eq(authToken), any(QrSelfEnrolRequest.class));
    }
}
