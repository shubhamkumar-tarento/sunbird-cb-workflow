package org.sunbird.workflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.*;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.config.RedisCacheMgr;
import org.sunbird.workflow.exception.ApplicationException;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;

import java.lang.reflect.Method;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class UserProfileWfServiceImplTest {

    @Spy
    @InjectMocks
    private UserProfileWfServiceImpl userProfileWfServiceImpl;

    @Mock
    private RequestServiceImpl requestServiceImpl;
    @Mock
    private Configuration configuration;
    @Mock
    private ObjectMapper mapper;
    @Mock
    private WfStatusRepo wfStatusRepo;
    @Mock
    private WorkflowServiceImpl workflowService;

    @Mock
    private RedisCacheMgr redisCacheMgr;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void updateUserProfile_shouldUpdateProfileForApprovedState() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);

        HashMap<String, Object> updateFieldValues;

        updateFieldValues = new HashMap<>();
        updateFieldValues.put("fieldKey", "professionalDetails");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        toValue.put("name", "mdo");
        updateFieldValues.put("toValue", toValue);
        updateFieldValues.put("fromValue", toValue);

        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.APPROVED_STATE);
        Map<String, Object> response = new HashMap<>();
        response.put("responseCode", "OK");

        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);
        when(requestServiceImpl.fetchResultUsingPatch(anyString(), any(), any())).thenReturn(response);

        String userId = "user123";

        // mock Configuration
        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host/");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/read/" + Constants.USER_ID_VALUE);

        // mock RequestServiceImpl
        Map<String, Object> mockedResponse = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> profileDetails = new HashMap<>();
        Map<String, Object> responseMap = new HashMap<>();
        Map<String, Object> rootOrgs = new HashMap<>();
        Map<String, Object> employmentDetails = new HashMap<>();
        employmentDetails.put(Constants.DEPARTMENT_NAME, "MDO");
        profileDetails.put(Constants.EMPLOYMENT_DETAILS, employmentDetails);
        rootOrgs.put(Constants.ROOT_ORG_ID, "id");
        responseMap.put(Constants.PROFILE_DETAILS,profileDetails);
        responseMap.put(Constants.ROOT_ORG_CONSTANT, rootOrgs);
        result.put("response", responseMap);


        mockedResponse.put("id", userId);
        mockedResponse.put("responseCode", "OK");
        mockedResponse.put("result", result);

        Map<String, Object> mockedRes = new HashMap<>();
        mockedRes.put(Constants.RESPONSE_CODE, Constants.OK);

        when(requestServiceImpl.fetchResultUsingPost(
                any(StringBuilder.class),
                any(),
                eq(Map.class),
                any(HashMap.class)
        )).thenReturn(mockedRes);

        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(mockedResponse);
        when(mapper.convertValue(mockedResponse, Map.class)).thenReturn(mockedResponse);

        // mock ObjectMapper

        userProfileWfServiceImpl.updateUserProfile(wfRequest);

        verify(requestServiceImpl, atLeast(0)).fetchResultUsingPatch(anyString(), any(), any());
    }

    @Test
    void updateUserProfile_shouldUpdateProfileForApprovedState_1() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.USER_PROFILE_FLAG_SERVICE);

        HashMap<String, Object> updateFieldValues;

        updateFieldValues = new HashMap<>();
        updateFieldValues.put("fieldKey", "professionalDetails");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        toValue.put("name", "mdo");
        updateFieldValues.put("toValue", toValue);
        updateFieldValues.put("fromValue", toValue);

        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.PROCESSED_STATE);
        Map<String, Object> response = new HashMap<>();
        response.put("responseCode", "OK");

        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);
        when(requestServiceImpl.fetchResultUsingPatch(anyString(), any(), any())).thenReturn(response);

        String userId = "user123";

        // mock Configuration
        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host/");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/read/" + Constants.USER_ID_VALUE);

        // mock RequestServiceImpl
        Map<String, Object> mockedResponse = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> profileDetails = new HashMap<>();
        Map<String, Object> responseMap = new HashMap<>();
        Map<String, Object> rootOrgs = new HashMap<>();
        Map<String, Object> employmentDetails = new HashMap<>();
        employmentDetails.put(Constants.DEPARTMENT_NAME, "MDO");
        profileDetails.put(Constants.EMPLOYMENT_DETAILS, employmentDetails);
        rootOrgs.put(Constants.ROOT_ORG_ID, "id");
        responseMap.put(Constants.PROFILE_DETAILS,profileDetails);
        responseMap.put(Constants.ROOT_ORG_CONSTANT, rootOrgs);
        result.put("response", responseMap);


        mockedResponse.put("id", userId);
        mockedResponse.put("responseCode", "OK");
        mockedResponse.put("result", result);

        Map<String, Object> mockedRes = new HashMap<>();
        mockedRes.put(Constants.RESPONSE_CODE, Constants.FAILED);

        when(requestServiceImpl.fetchResultUsingPost(
                any(StringBuilder.class),
                any(),
                eq(Map.class),
                any(HashMap.class)
        )).thenReturn(mockedRes);

        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(mockedResponse);
        when(mapper.convertValue(mockedResponse, Map.class)).thenReturn(mockedResponse);

        // mock ObjectMapper

        userProfileWfServiceImpl.updateUserProfile(wfRequest);

        verify(requestServiceImpl, atLeast(0)).fetchResultUsingPatch(anyString(), any(), any());
    }

    @Test
    void updateUserProfile_shouldNotUpdateProfileForNonApprovedState() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("REJECTED");

        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);

        userProfileWfServiceImpl.updateUserProfile(wfRequest);

        verify(requestServiceImpl, never()).fetchResultUsingPatch(anyString(), any(), any());
    }

    @Test
    void updateRequestWithWF_shouldReturnNullForInvalidProfileElement() {
        Map<String, Object> profileDetails = new HashMap<>();
        List<HashMap<String, Object>> wfRequestParamList = new ArrayList<>();
        HashMap<String, Object> param = new HashMap<>();
        param.put(Constants.FIELD_KEY, "invalidKey");
        param.put(Constants.TO_VALUE, new HashMap<>());
        wfRequestParamList.add(param);

        Map<String, Object> result = userProfileWfServiceImpl.updateRequestWithWF(wfRequestParamList, profileDetails);

        assertNull(result);
    }

    @Test
    void mergeLeaf_shouldMergeArrayListLeafKey() {
        Map<String, Object> left = new HashMap<>();
        List<Map<String, Object>> arr = new ArrayList<>();
        Map<String, Object> child = new HashMap<>();
        arr.add(child);
        left.put("leaf", arr);

        Map<String, Object> right = new HashMap<>();
        right.put("key", "value");

        UserProfileWfServiceImpl.mergeLeaf(left, right, "leaf");

        assertEquals("value", ((Map<String, Object>)((List<?>)left.get("leaf")).get(0)).get("key"));
    }

    @Test
    void mergeLeaf_shouldAddProfessionalDetailsIfLeafKeyNotPresent() {
        Map<String, Object> left = new HashMap<>();
        Map<String, Object> right = new HashMap<>();
        right.put("name", "dept");

        UserProfileWfServiceImpl.mergeLeaf(left, right, Constants.PROFESSIONAL_DETAILS);

        assertTrue(left.containsKey(Constants.PROFESSIONAL_DETAILS));
    }

    @Test
    void enrichUserData_shouldReturnWfDetailsWithUserInfo() {
        WfStatusEntity entity = new WfStatusEntity();
        Map<String, List<WfStatusEntity>> wfInfos = new HashMap<>();
        wfInfos.put("userId", List.of(entity));

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.FIRST_NAME, "Ajay");
        HashMap<String, Object> usersResult = new HashMap<>();
        usersResult.put("userId", userInfo);

        doReturn(usersResult).when(userProfileWfServiceImpl).getUsersResult(any());

        List<Map<String, Object>> result = userProfileWfServiceImpl.enrichUserData(wfInfos, "rootOrg");

        assertEquals(1, result.size());
        assertEquals(userInfo, result.get(0).get("userInfo"));
    }

    @Test
    void getUsersResult_shouldReturnUserResultForValidResponse() {
        Set<String> userIds = Set.of("u1");
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> profileDetails = new HashMap<>();
        Map<String, Object> personalDetails = new HashMap<>();
        personalDetails.put(Constants.FIRSTNAME, "Ajay");
        personalDetails.put(Constants.PRIMARY_EMAIL, "ajay@example.com");
        profileDetails.put(Constants.PERSONAL_DETAILS, personalDetails);
        profileDetails.put(Constants.ADDITIONAL_PROPERTIES, Map.of(Constants.TAG, "tag1"));
        content.put(Constants.PROFILE_DETAILS, profileDetails);
        content.put(Constants.USER_ID, "u1");
        content.put(Constants.ROOT_ORG_ID, "rootOrgId");

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CONTENT, List.of(content));
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.RESPONSE, response);
        Map<String, Object> apiResp = new HashMap<>();
        apiResp.put(Constants.RESPONSE_CODE, "OK");
        apiResp.put(Constants.RESULT, result);

        when(configuration.getLmsServiceHost()).thenReturn("host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(apiResp);

        Map<String, Object> userResult = userProfileWfServiceImpl.getUsersResult(userIds);

        assertTrue(userResult.containsKey("u1"));
    }

    @Test
    void getUsersResult_shouldThrowExceptionOnError() {
        Set<String> userIds = Set.of("u1");
        when(configuration.getLmsServiceHost()).thenReturn("host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenThrow(new RuntimeException("fail"));

        assertThrows(ApplicationException.class, () -> userProfileWfServiceImpl.getUsersResult(userIds));
    }

    @Test
    void getMdoAdminAndPCDetails_shouldReturnEmailList() {
        Map<String, Object> personalDetails = new HashMap<>();
        personalDetails.put(Constants.PRIMARY_EMAIL, "mdo@example.com");
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PERSONAL_DETAILS, personalDetails);
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.PROFILE_DETAILS, profileDetails);

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CONTENT, List.of(content));
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.RESPONSE, response);
        Map<String, Object> apiResp = new HashMap<>();
        apiResp.put(Constants.RESPONSE_CODE, "OK");
        apiResp.put(Constants.RESULT, result);

        when(configuration.getLmsServiceHost()).thenReturn("host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(configuration.getMdoAdminSearchFields()).thenReturn(List.of("field"));
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(apiResp);

        List<String> emails = userProfileWfServiceImpl.getMdoAdminAndPCDetails("rootOrgId", List.of("role"));

        assertEquals(List.of("mdo@example.com"), emails);
    }

    @Test
    void getMdoAdminAndPCDetails_shouldThrowExceptionOnError() {
        // Arrange
        String rootOrgId = "rootOrgId";
        List<String> roles = List.of("role");

        when(configuration.getLmsServiceHost()).thenReturn("host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(configuration.getMdoAdminSearchFields()).thenReturn(List.of("field"));
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("fail"));

        // Act
        Executable executable = () -> userProfileWfServiceImpl.getMdoAdminAndPCDetails(rootOrgId, roles);

        // Assert
        assertThrows(ApplicationException.class, executable);
    }


    @Test
    void testGetMdoAdminAndPCDetails_nullResponse() {

        when(configuration.getLmsServiceHost()).thenReturn("host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(configuration.getMdoAdminSearchFields()).thenReturn(List.of("field"));        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any(HashMap.class))).thenReturn(null);

        List<String> result = userProfileWfServiceImpl.getMdoAdminAndPCDetails("rootOrgId", List.of("role"));
        assertTrue(result.isEmpty());
    }


    @Test
    void getVerifiedProfileSchema_shouldReturnNullIfDataIsNull() {
        when(requestServiceImpl.fetchResultUsingGet(any())).thenReturn(null);
        when(configuration.getLmsServiceHost()).thenReturn("host");
        when(configuration.getVerifiedProfileFieldsPath()).thenReturn("/schema");

        String schema = userProfileWfServiceImpl.getVerifiedProfileSchema();

        assertNull(schema);
    }

    @Test
    void updateUserProfileV2_shouldUpdateProfileForValidRequest() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(Map.of(Constants.TO_VALUE, Map.of(Constants.NAME, "dept")))));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.APPROVED_STATE);

        Map<String, Object> profileDetails = new HashMap<>();
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put(Constants.PROFILE_DETAILS, profileDetails);
        Map<String, Object> userResults = new HashMap<>();
        userResults.put(Constants.RESPONSE, userResponse);
        Map<String, Object> readData = new HashMap<>();
        readData.put(Constants.RESULT, userResults);

        doReturn(wfStatusEntity).when(wfStatusRepo).findByApplicationIdAndWfId(any(), any());

        assertDoesNotThrow(()-> userProfileWfServiceImpl.updateUserProfileV2(List.of(wfRequest), "userId", null));
    }

    @Test
    void updateUserProfileV2_shouldUpdateUserProfileDataOnUpdateRequired() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(Map.of(Constants.TO_VALUE, Map.of("other", "value")))));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.APPROVED_STATE);

        Map<String, Object> profileDetails = new HashMap<>();
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put(Constants.PROFILE_DETAILS, profileDetails);
        Map<String, Object> userResults = new HashMap<>();
        userResults.put(Constants.RESPONSE, userResponse);
        Map<String, Object> readData = new HashMap<>();
        readData.put(Constants.RESULT, userResults);

        doReturn(wfStatusEntity).when(wfStatusRepo).findByApplicationIdAndWfId(any(), any());
        doReturn(new HashMap<>()).when(userProfileWfServiceImpl).updateRequestWithWF(any(), any());

        assertDoesNotThrow(()-> userProfileWfServiceImpl.updateUserProfileV2(List.of(wfRequest), "userId", null));
    }

    @Test
    void validateJsonAgainstSchema_validJson_returnsTrue() {
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put("field1", "value1");

        // provide a valid schema & valid data
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "field1": { "type": "string" }
                  },
                  "required": ["field1"]
                }
                """;

        doReturn(schema).when(userProfileWfServiceImpl).getVerifiedProfileSchema();

        boolean result = userProfileWfServiceImpl.validateJsonAgainstSchema(profileDetails);
        assertTrue(result);
    }

    @Test
    void validateJsonAgainstSchema_invalidJson_returnsFalse() {
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put("field1", 123); // invalid because schema will expect string

        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "field1": { "type": "string" }
                  },
                  "required": ["field1"]
                }
                """;

        doReturn(schema).when(userProfileWfServiceImpl).getVerifiedProfileSchema();

        boolean result = userProfileWfServiceImpl.validateJsonAgainstSchema(profileDetails);
        assertFalse(result);
    }

    @Test
    void validateJsonAgainstSchema_jsonException_throws() {
        Map<String, Object> profileDetails = new HashMap<>();

        doReturn("invalid schema").when(userProfileWfServiceImpl).getVerifiedProfileSchema();

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                userProfileWfServiceImpl.validateJsonAgainstSchema(profileDetails));

        assertTrue(ex.getMessage().contains("Can't parse json schema"));
    }

    @Test
    void getVerifiedProfileSchema_returnsSchema() {
        String expectedSchema = "{\"type\":\"object\"}";

        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getVerifiedProfileFieldsPath()).thenReturn("path");

        Map<String, Object> responseMap = new HashMap<>();
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, Object> innerResponse = new HashMap<>();
        innerResponse.put(Constants.VALUE, expectedSchema);

        resultMap.put(Constants.RESPONSE, innerResponse);
        responseMap.put(Constants.RESULT, resultMap);

        when(requestServiceImpl.fetchResultUsingGet(any())).thenReturn(responseMap);
        when(mapper.convertValue(any(), eq(Map.class))).thenReturn(responseMap);

        String schema = userProfileWfServiceImpl.getVerifiedProfileSchema();

        assertEquals(expectedSchema, schema);
    }

    @Test
    void getVerifiedProfileSchema_nullData_returnsNull() {
        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getVerifiedProfileFieldsPath()).thenReturn("path");

        when(requestServiceImpl.fetchResultUsingGet(any())).thenReturn(null);

        String schema = userProfileWfServiceImpl.getVerifiedProfileSchema();

        assertNull(schema);
    }

    @Test
    void updateUserProfileV2_userNotFound() {
        WfRequest wfRequest = mock(WfRequest.class);
        List<WfRequest> wfRequests = List.of(wfRequest);

        Map<String, Object> readData = new HashMap<>();
        readData.put(Constants.RESPONSE_CODE, "FAIL");
        readData.put(Constants.PARAMS, Map.of(Constants.ERROR_MESSAGE, "Not Found"));

        assertDoesNotThrow(()-> userProfileWfServiceImpl.updateUserProfileV2(wfRequests, "user1", (String) null));
    }

    @Test
    void failedCase_shouldUpdateWfStatusAndSave() throws Exception {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");

        WfStatusEntity wfStatusEntity = new WfStatusEntity();

        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);

        String errorMsg = "Test error";

        // Use reflection
        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod(
                "failedCase", WfRequest.class, String.class);
        method.setAccessible(true);

        method.invoke(userProfileWfServiceImpl, wfRequest, errorMsg);

        verify(wfStatusRepo).findByApplicationIdAndWfId("appId", "wfId");
        verify(wfStatusRepo).save(wfStatusEntity);

        assert "FAILED".equals(wfRequest.getState());
        assert "FAILED".equals(wfRequest.getAction());
        assert "FAILED".equals(wfStatusEntity.getCurrentStatus());
        assert !wfStatusEntity.getInWorkflow();
        assert errorMsg.equals(wfStatusEntity.getComment());
        assert wfStatusEntity.getLastUpdatedOn() != null;
    }

    @Test
    void failedCaseProfileUpdate_shouldUpdateWhenNameNotPresent() throws Exception {
        // Prepare test data
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");

        HashMap<String, Object> toValueMap = new HashMap<>();
        toValueMap.put("otherField", "value");  // intentionally NOT "name"

        HashMap<String, Object> updatedFieldElement = new HashMap<>();
        updatedFieldElement.put("toValue", toValueMap);

        List<HashMap<String, Object>> updatedFieldValues = new ArrayList<>();
        updatedFieldValues.add(updatedFieldElement);

        wfRequest.setUpdateFieldValues(updatedFieldValues);

        List<WfRequest> wfRequests = Collections.singletonList(wfRequest);

        WfStatusEntity wfStatusEntity = new WfStatusEntity();

        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);

        String updateError = "Test update error";

        // Invoke private method using reflection
        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod(
                "failedCaseProfileUpdate", List.class, String.class);
        method.setAccessible(true);

        method.invoke(userProfileWfServiceImpl, wfRequests, updateError);

        // Verify
        verify(wfStatusRepo).findByApplicationIdAndWfId("appId", "wfId");
        verify(wfStatusRepo).save(wfStatusEntity);

        // Assertions
        assert "FAILED".equals(wfRequest.getState());
        assert "FAILED".equals(wfRequest.getAction());
        assert "FAILED".equals(wfStatusEntity.getCurrentStatus());
        assert !wfStatusEntity.getInWorkflow();
        assert updateError.equals(wfStatusEntity.getComment());
        assert wfStatusEntity.getLastUpdatedOn() != null;
    }

    @Test
    void testUpdateUserProfileData_successAndFailure() throws Exception {
        String userId = "userId";
        Map<String, Object> profileDetails = new HashMap<>();

        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");

        List<WfRequest> wfRequests = Collections.singletonList(wfRequest);

        // Mock config & patch call
        when(configuration.getLmsServiceHost()).thenReturn("http://lms/");
        when(configuration.getUserProfileUpdateEndPoint()).thenReturn("update");

        Map<String, Object> successfulResponse = Map.of("responseCode", "OK");
        Map<String, Object> failedResponse = Map.of("responseCode", "FAILED", "params", Map.of("err", "Some error"));

        // On first call - OK, on second call - failure
        when(requestServiceImpl.fetchResultUsingPatch(anyString(), any(), any()))
                .thenReturn(successfulResponse)
                .thenReturn(failedResponse);

        when(mapper.writeValueAsString(any())).thenReturn("{}");

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod(
                "updateUserProfileData", String.class, Map.class, List.class);
        method.setAccessible(true);

        // SUCCESS CASE
        method.invoke(userProfileWfServiceImpl, userId, profileDetails, wfRequests);
        verify(redisCacheMgr).deleteCache("user:basicProfile:userId");

        // FAILURE CASE
        method.invoke(userProfileWfServiceImpl, userId, profileDetails, wfRequests);
    }

    @Test
    void testGetMdoAdminAndPCDetails_successWithEmptyContent() {
        String rootOrgId = "org2";
        List<String> roles = List.of("Admin");

        Map<String, Object> response = new HashMap<>();
        response.put("content", Collections.emptyList());

        Map<String, Object> result = new HashMap<>();
        result.put("response", response);

        Map<String, Object> mdoAdminSearchResult = new HashMap<>();
        mdoAdminSearchResult.put("responseCode", "OK");
        mdoAdminSearchResult.put("result", result);

        when(configuration.getLmsServiceHost()).thenReturn("http://dummy-host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(configuration.getMdoAdminSearchFields()).thenReturn(List.of("email"));

        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any()))
                .thenReturn(mdoAdminSearchResult);

        List<String> resultEmails = userProfileWfServiceImpl.getMdoAdminAndPCDetails(rootOrgId, roles);

        assertTrue(resultEmails.isEmpty());
    }

    @Test
    void testGetMdoAdminAndPCDetails_successWithNullSearchResult() {
        String rootOrgId = "org3";
        List<String> roles = List.of("Admin");

        when(configuration.getLmsServiceHost()).thenReturn("http://dummy-host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(configuration.getMdoAdminSearchFields()).thenReturn(List.of("email"));

        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any()))
                .thenReturn(null);

        List<String> resultEmails = userProfileWfServiceImpl.getMdoAdminAndPCDetails(rootOrgId, roles);

        assertTrue(resultEmails.isEmpty());
    }

    @Test
    void testGetMdoAdminAndPCDetails_invalidResponseCode() {
        String rootOrgId = "org4";
        List<String> roles = List.of("PC");

        Map<String, Object> mdoAdminSearchResult = new HashMap<>();
        mdoAdminSearchResult.put("responseCode", "FAILURE");

        when(configuration.getLmsServiceHost()).thenReturn("http://dummy-host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(configuration.getMdoAdminSearchFields()).thenReturn(List.of("email"));

        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any()))
                .thenReturn(mdoAdminSearchResult);

        List<String> resultEmails = userProfileWfServiceImpl.getMdoAdminAndPCDetails(rootOrgId, roles);

        assertTrue(resultEmails.isEmpty());
    }

    @Test
    void testGetMdoAdminAndPCDetails_missingNestedFields() {
        String rootOrgId = "org5";
        List<String> roles = List.of("MDO");

        Map<String, Object> content = new HashMap<>();
        // No profileDetails present

        List<Map<String, Object>> contents = List.of(content);

        Map<String, Object> response = new HashMap<>();
        response.put("content", contents);

        Map<String, Object> result = new HashMap<>();
        result.put("response", response);

        Map<String, Object> mdoAdminSearchResult = new HashMap<>();
        mdoAdminSearchResult.put("responseCode", "OK");
        mdoAdminSearchResult.put("result", result);

        when(configuration.getLmsServiceHost()).thenReturn("http://dummy-host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(configuration.getMdoAdminSearchFields()).thenReturn(List.of("email"));

        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any()))
                .thenReturn(mdoAdminSearchResult);

        List<String> resultEmails = userProfileWfServiceImpl.getMdoAdminAndPCDetails(rootOrgId, roles);

        assertTrue(resultEmails.isEmpty());
    }

    @Test
    void testUpdateRequestWithWF_ArrayListMatch() {
        Map<String, Object> existingProfileDetail = new HashMap<>();
        List<Map<String, Object>> fieldData = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("osid", "osid-1");
        item.put("key1", "value1");
        fieldData.add(item);

        existingProfileDetail.put("fieldKey1", fieldData);

        HashMap<String, Object> wfObj = new HashMap<>();
        wfObj.put("osid", "osid-1");
        wfObj.put("fieldKey", "fieldKey1");
        wfObj.put("toValue", Map.of("key2", "value2"));

        List<HashMap<String, Object>> wfList = List.of(wfObj);

        when(mapper.convertValue(fieldData, ArrayList.class)).thenReturn((ArrayList) fieldData);

        Map<String, Object> result = userProfileWfServiceImpl.updateRequestWithWF(wfList, existingProfileDetail);

        assertNotNull(result);
        assertTrue(((List<Map<String, Object>>) result.get("fieldKey1")).get(0).containsKey("key1"));
        assertTrue(((List<Map<String, Object>>) result.get("fieldKey1")).get(0).containsKey("key2"));
    }

    @Test
    void testUpdateRequestWithWF_HashMap() {
        Map<String, Object> existingProfileDetail = new HashMap<>();
        Map<String, Object> mapData = new HashMap<>();
        mapData.put("existingKey", "existingValue");
        existingProfileDetail.put("fieldKey2", mapData);

        HashMap<String, Object> wfObj = new HashMap<>();
        wfObj.put("fieldKey", "fieldKey2");
        wfObj.put("toValue", Map.of("keyX", "valX"));

        List<HashMap<String, Object>> wfList = List.of(wfObj);

        // ✅ Directly pass values (no need for eq(...) when using real instances)
        when(mapper.convertValue(mapData, Map.class)).thenReturn(mapData);

        Map<String, Object> result = userProfileWfServiceImpl.updateRequestWithWF(wfList, existingProfileDetail);

        assertNotNull(result);
        assertTrue(((Map<String, Object>) result.get("fieldKey2")).containsKey("existingKey"));
        assertTrue(((Map<String, Object>) result.get("fieldKey2")).containsKey("keyX"));
    }


    @Test
    void testUpdateRequestWithWF_BooleanField() {
        Map<String, Object> existingProfileDetail = new HashMap<>();
        existingProfileDetail.put("verifiedKarmayogi", Boolean.FALSE);

        Map<String, Object> toValue = Map.of("verifiedKarmayogi", Boolean.TRUE);
        HashMap<String, Object> wfObj = new HashMap<>();
        wfObj.put("fieldKey", "verifiedKarmayogi");
        wfObj.put("toValue", toValue);

        List<HashMap<String, Object>> wfList = List.of(wfObj);

        Map<String, Object> result = userProfileWfServiceImpl.updateRequestWithWF(wfList, existingProfileDetail);

        assertEquals(Boolean.TRUE, result.get("verifiedKarmayogi"));
    }

    @Test
    void testUpdateRequestWithWF_NullField_VerifiedKarmayogi() {
        Map<String, Object> existingProfileDetail = new HashMap<>();

        Map<String, Object> toValue = Map.of("verifiedKarmayogi", Boolean.TRUE);
        HashMap<String, Object> wfObj = new HashMap<>();
        wfObj.put("fieldKey", "verifiedKarmayogi");
        wfObj.put("toValue", toValue);

        List<HashMap<String, Object>> wfList = List.of(wfObj);

        Map<String, Object> result = userProfileWfServiceImpl.updateRequestWithWF(wfList, existingProfileDetail);

        assertEquals(Boolean.TRUE, result.get("verifiedKarmayogi"));
    }

    @Test
    void testUpdateRequestWithWF_NullField_ProfessionalDetails() {
        Map<String, Object> existingProfileDetail = new HashMap<>();

        Map<String, Object> profDetail = Map.of("designation", "Engineer");
        HashMap<String, Object> wfObj = new HashMap<>();
        wfObj.put("fieldKey", "professionalDetails");
        wfObj.put("toValue", profDetail);

        List<HashMap<String, Object>> wfList = List.of(wfObj);

        Map<String, Object> result = userProfileWfServiceImpl.updateRequestWithWF(wfList, existingProfileDetail);

        List<Map<String, Object>> profList = (List<Map<String, Object>>) result.get("professionalDetails");
        assertNotNull(profList);
        assertEquals("Engineer", profList.get(0).get("designation"));
    }

    @Test
    void testUpdateRequestWithWF_NullField_UnknownKey() {
        Map<String, Object> existingProfileDetail = new HashMap<>();

        HashMap<String, Object> wfObj = new HashMap<>();
        wfObj.put("fieldKey", "someUnknownField");
        wfObj.put("toValue", Map.of("val", 1));

        List<HashMap<String, Object>> wfList = List.of(wfObj);

        Map<String, Object> result = userProfileWfServiceImpl.updateRequestWithWF(wfList, existingProfileDetail);

        assertNull(result); // logs error and returns null
    }

    @Test
    void testGetUpdateRequest_withStateType_usingReflection() throws Exception {
        // Arrange
        WfRequest wfRequest = new WfRequest();
        wfRequest.setDeptName("EducationDept");
        wfRequest.setApplicationId("appId");

        Map<String, Object> dummyToValue = new HashMap<>();
        dummyToValue.put(Constants.FIRSTNAME, "John");
        HashMap<String, Object> dummyField = new HashMap<>();
        dummyField.put(Constants.TO_VALUE, dummyToValue);
        wfRequest.setUpdateFieldValues(List.of(dummyField));

        Map<String, Object> updateRequest = new HashMap<>();

        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("/org/search");

        // ✅ keys expected by code for state/ministry types
        Map<String, Object> orgDetail = Map.of(
                "organisationType", "16",
                Constants.ROOT_ORG_ID, "STATE01",
                Constants.ORG_NAME, "Education Department"
        );

        Map<String, Object> response = Map.of(Constants.CONTENT, List.of(orgDetail));
        Map<String, Object> result = Map.of(Constants.RESPONSE, response);
        Map<String, Object> apiResp = Map.of(Constants.RESPONSE_CODE, "OK", Constants.RESULT, result);

        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(apiResp);

        Method privateMethod = UserProfileWfServiceImpl.class
                .getDeclaredMethod("getUpdateRequest", WfRequest.class, Map.class);
        privateMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap =
                (Map<String, Object>) privateMethod.invoke(userProfileWfServiceImpl, wfRequest, updateRequest);

        Map<String, Object> requestWrapper = (Map<String, Object>) resultMap.get(Constants.REQUEST);
        Map<String, Object> profileDetails = (Map<String, Object>) requestWrapper.get(Constants.PROFILE_DETAILS);

        assertEquals("STATE01", profileDetails.get(Constants.MINISTRYORSTATEID));
        assertEquals("Education Department", profileDetails.get(Constants.MINISTRYORSTATEORGNAME));
    }


    @Test
    void testGetUpdateRequest_withSPVType_usingReflection() throws Exception {
        // Arrange
        WfRequest wfRequest = new WfRequest();
        wfRequest.setDeptName("SPVDept");
        wfRequest.setApplicationId("appId");

        // Dummy update field values to avoid NPE
        Map<String, Object> dummyToValue = new HashMap<>();
        dummyToValue.put(Constants.FIRSTNAME, "John");
        HashMap<String, Object> dummyField = new HashMap<>();
        dummyField.put(Constants.TO_VALUE, dummyToValue);
        wfRequest.setUpdateFieldValues(List.of(dummyField));

        Map<String, Object> updateRequest = new HashMap<>();

        // Mock configuration & API response for fetchOrgDetails
        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("/org/search");

        // ✅ Use keys expected in the "other" branch
        Map<String, Object> orgDetail = Map.of(
                "organisationType", "999",
                Constants.MINISTRYORSTATEID, "ROOT01",
                Constants.MINISTRYORSTATENAME, "Smart Projects Venture"
        );

        Map<String, Object> response = Map.of(Constants.CONTENT, List.of(orgDetail));
        Map<String, Object> result = Map.of(Constants.RESPONSE, response);
        Map<String, Object> apiResp = Map.of(Constants.RESPONSE_CODE, "OK", Constants.RESULT, result);

        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(apiResp);

        // Reflection call to private getUpdateRequest
        Method privateMethod = UserProfileWfServiceImpl.class
                .getDeclaredMethod("getUpdateRequest", WfRequest.class, Map.class);
        privateMethod.setAccessible(true);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap =
                (Map<String, Object>) privateMethod.invoke(userProfileWfServiceImpl, wfRequest, updateRequest);

        // Assert
        Map<String, Object> requestWrapper = (Map<String, Object>) resultMap.get(Constants.REQUEST);
        Map<String, Object> profileDetails = (Map<String, Object>) requestWrapper.get(Constants.PROFILE_DETAILS);

        assertEquals("ROOT01", profileDetails.get(Constants.MINISTRYORSTATEID));
        assertEquals("Smart Projects Venture", profileDetails.get(Constants.MINISTRYORSTATEORGNAME));
    }

    // ---------- updateProfile: profile-read failure branches (79-85) ----------

    @Test
    void updateProfile_profileReadReturnsNull_marksFailed() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(Map.of(Constants.TO_VALUE, new HashMap<>(Map.of("other", "v"))))));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.APPROVED_STATE);
        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);

        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host/");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/read/" + Constants.USER_ID_VALUE);
        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(null);

        userProfileWfServiceImpl.updateUserProfile(wfRequest);

        assertEquals(Constants.FAILED, wfStatusEntity.getCurrentStatus());
        verify(wfStatusRepo).save(wfStatusEntity);
    }

    @Test
    void updateProfile_profileReadResponseCodeNotOk_marksFailed() throws Exception {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(Map.of(Constants.TO_VALUE, new HashMap<>(Map.of("other", "v"))))));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.APPROVED_STATE);
        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);

        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host/");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/read/" + Constants.USER_ID_VALUE);

        Map<String, Object> mockedResponse = new HashMap<>();
        mockedResponse.put(Constants.RESPONSE_CODE, "CLIENT_ERROR");
        mockedResponse.put(Constants.PARAMS, Map.of(Constants.ERROR_MESSAGE, "user not found"));
        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(mockedResponse);
        when(mapper.convertValue(mockedResponse, Map.class)).thenReturn(mockedResponse);

        userProfileWfServiceImpl.updateUserProfile(wfRequest);

        assertEquals(Constants.FAILED, wfStatusEntity.getCurrentStatus());
        verify(wfStatusRepo).save(wfStatusEntity);
    }

    // ---------- updateProfile: updateRequestWithWF returns null (106-108) ----------

    @Test
    void updateProfile_updateRequestWithWFReturnsNull_marksFailed() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        HashMap<String, Object> field = new HashMap<>();
        field.put(Constants.FIELD_KEY, "unknownKey");
        field.put(Constants.TO_VALUE, new HashMap<>(Map.of("x", "y")));
        wfRequest.setUpdateFieldValues(List.of(field));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.APPROVED_STATE);
        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);

        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host/");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/read/" + Constants.USER_ID_VALUE);

        Map<String, Object> mockedResponse = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> responseMap = new HashMap<>();
        Map<String, Object> rootOrgs = new HashMap<>();
        rootOrgs.put(Constants.ROOT_ORG_ID, "id");
        responseMap.put(Constants.PROFILE_DETAILS, new HashMap<>());
        responseMap.put(Constants.ROOT_ORG_CONSTANT, rootOrgs);
        result.put("response", responseMap);
        mockedResponse.put("responseCode", "OK");
        mockedResponse.put("result", result);
        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(mockedResponse);
        when(mapper.convertValue(mockedResponse, Map.class)).thenReturn(mockedResponse);

        userProfileWfServiceImpl.updateUserProfile(wfRequest);

        assertEquals(Constants.FAILED, wfStatusEntity.getCurrentStatus());
        verify(wfStatusRepo).save(wfStatusEntity);
    }

    // ---------- updateProfile: update API responds with a non-OK code (114-115) ----------

    @Test
    void updateProfile_updateApiRespNotOk_marksFailed() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        HashMap<String, Object> field = new HashMap<>();
        field.put(Constants.FIELD_KEY, "verifiedKarmayogi");
        field.put(Constants.TO_VALUE, new HashMap<>(Map.of(Constants.VERIFIED_KARMAYOGI, Boolean.TRUE)));
        wfRequest.setUpdateFieldValues(List.of(field));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.APPROVED_STATE);
        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);

        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host/");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/read/" + Constants.USER_ID_VALUE);
        when(configuration.getUserProfileUpdateEndPoint()).thenReturn("/user/update");

        Map<String, Object> mockedResponse = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> responseMap = new HashMap<>();
        Map<String, Object> rootOrgs = new HashMap<>();
        rootOrgs.put(Constants.ROOT_ORG_ID, "id");
        responseMap.put(Constants.PROFILE_DETAILS, new HashMap<>(Map.of("verifiedKarmayogi", Boolean.FALSE)));
        responseMap.put(Constants.ROOT_ORG_CONSTANT, rootOrgs);
        result.put("response", responseMap);
        mockedResponse.put("responseCode", "OK");
        mockedResponse.put("result", result);
        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(mockedResponse);
        when(mapper.convertValue(mockedResponse, Map.class)).thenReturn(mockedResponse);

        Map<String, Object> failedUpdateResp = new HashMap<>();
        failedUpdateResp.put(Constants.RESPONSE_CODE, "CLIENT_ERROR");
        failedUpdateResp.put(Constants.PARAMS, Map.of(Constants.ERROR_MESSAGE, "update rejected"));
        when(requestServiceImpl.fetchResultUsingPatch(anyString(), any(), any())).thenReturn(failedUpdateResp);

        userProfileWfServiceImpl.updateUserProfile(wfRequest);

        assertEquals(Constants.FAILED, wfStatusEntity.getCurrentStatus());
        verify(wfStatusRepo).save(wfStatusEntity);
    }

    // ---------- updateProfileVerificationStatus branches (202, 213-214) ----------

    @Test
    void updateProfileVerificationStatus_groupOnly_marksGroupVerified() throws Exception {
        Map<String, Object> profDetail = new HashMap<>();
        profDetail.put(Constants.GROUP, "Engineering");
        Map<String, Object> existingProfileDetail = new HashMap<>();
        existingProfileDetail.put(Constants.PROFESSIONAL_DETAILS, new ArrayList<>(List.of(profDetail)));

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("updateProfileVerificationStatus", Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, existingProfileDetail);

        assertEquals(Constants.VERIFIED, existingProfileDetail.get(Constants.PROFILE_GROUP_STATUS));
        assertEquals(Constants.NOT_VERIFIED, existingProfileDetail.get(Constants.PROFILE_DESIGNATION_STATUS));
        assertEquals(Constants.NOT_VERIFIED, existingProfileDetail.get(Constants.PROFILE_STATUS));
    }

    @Test
    void updateProfileVerificationStatus_groupAndDesignation_marksFullyVerified() throws Exception {
        Map<String, Object> profDetail = new HashMap<>();
        profDetail.put(Constants.GROUP, "Engineering");
        profDetail.put(Constants.DESIGNATION, "Manager");
        Map<String, Object> existingProfileDetail = new HashMap<>();
        existingProfileDetail.put(Constants.PROFESSIONAL_DETAILS, new ArrayList<>(List.of(profDetail)));

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("updateProfileVerificationStatus", Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, existingProfileDetail);

        assertEquals(Constants.VERIFIED, existingProfileDetail.get(Constants.PROFILE_STATUS));
    }

    // ---------- populateUserRecord via getUsersResult (331, 343-344) ----------

    @Test
    void getUsersResult_emptyProfileDetails_skipsRecord() {
        Set<String> userIds = Set.of("u1");
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.PROFILE_DETAILS, new HashMap<>());
        content.put(Constants.USER_ID, "u1");

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CONTENT, List.of(content));
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.RESPONSE, response);
        Map<String, Object> apiResp = new HashMap<>();
        apiResp.put(Constants.RESPONSE_CODE, "OK");
        apiResp.put(Constants.RESULT, result);

        when(configuration.getLmsServiceHost()).thenReturn("host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(apiResp);

        Map<String, Object> userResult = userProfileWfServiceImpl.getUsersResult(userIds);

        assertTrue(userResult.isEmpty());
    }

    @Test
    void getUsersResult_withDepartmentName_addsDepartmentToRecord() {
        Set<String> userIds = Set.of("u1");
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> profileDetails = new HashMap<>();
        Map<String, Object> personalDetails = new HashMap<>();
        personalDetails.put(Constants.FIRSTNAME, "Ajay");
        profileDetails.put(Constants.PERSONAL_DETAILS, personalDetails);
        Map<String, Object> employmentDetails = new HashMap<>();
        employmentDetails.put(Constants.DEPARTMENT_NAME, "Engineering Dept");
        profileDetails.put(Constants.EMPLOYMENT_DETAILS, employmentDetails);
        content.put(Constants.PROFILE_DETAILS, profileDetails);
        content.put(Constants.USER_ID, "u1");

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CONTENT, List.of(content));
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.RESPONSE, response);
        Map<String, Object> apiResp = new HashMap<>();
        apiResp.put(Constants.RESPONSE_CODE, "OK");
        apiResp.put(Constants.RESULT, result);

        when(configuration.getLmsServiceHost()).thenReturn("host");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/search");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(apiResp);

        Map<String, Object> userResult = userProfileWfServiceImpl.getUsersResult(userIds);

        Map<String, Object> userRecord = (Map<String, Object>) userResult.get("u1");
        assertEquals("Engineering Dept", userRecord.get(Constants.DEPARTMENT_NAME));
    }

    // ---------- migrationUpdate via updateProfile: migrateUser failure (394-396) ----------

    @Test
    void updateProfile_migrateUserFails_marksFailed() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setDeptName("oldDept");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        HashMap<String, Object> field = new HashMap<>();
        field.put(Constants.FIELD_KEY, Constants.PROFESSIONAL_DETAILS);
        field.put(Constants.TO_VALUE, new HashMap<>(Map.of(Constants.NAME, "NewDept")));
        wfRequest.setUpdateFieldValues(List.of(field));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.APPROVED_STATE);
        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);

        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host/");
        when(configuration.getUserProfileMigrateEndPoint()).thenReturn("/migrate");

        Map<String, Object> migrateFailResp = new HashMap<>();
        migrateFailResp.put(Constants.RESPONSE_CODE, "CLIENT_ERROR");
        migrateFailResp.put(Constants.PARAMS, Map.of(Constants.ERROR_MESSAGE, "migration rejected"));
        when(requestServiceImpl.fetchResultUsingPatch(anyString(), any(), any())).thenReturn(migrateFailResp);

        userProfileWfServiceImpl.updateUserProfile(wfRequest);

        assertEquals(Constants.FAILED, wfStatusEntity.getCurrentStatus());
        assertEquals("NewDept", wfRequest.getDeptName());
    }

    // ---------- applyNameOrPhoneUpdate via reflection (457, 462, 469) ----------

    @Test
    void applyNameOrPhoneUpdate_missingToValue_returnsEarly() throws Exception {
        Map<String, Object> valueToUpdate = new HashMap<>();
        Map<String, Object> requestWrapper = new HashMap<>();

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("applyNameOrPhoneUpdate", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, valueToUpdate, requestWrapper);

        assertTrue(requestWrapper.isEmpty());
    }

    @Test
    void applyNameOrPhoneUpdate_emptyToValue_returnsEarly() throws Exception {
        Map<String, Object> valueToUpdate = new HashMap<>();
        valueToUpdate.put(Constants.TO_VALUE, new HashMap<>());
        Map<String, Object> requestWrapper = new HashMap<>();

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("applyNameOrPhoneUpdate", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, valueToUpdate, requestWrapper);

        assertTrue(requestWrapper.isEmpty());
    }

    @Test
    void applyNameOrPhoneUpdate_mobileKey_setsPhone() throws Exception {
        Map<String, Object> valueToUpdate = new HashMap<>();
        valueToUpdate.put(Constants.TO_VALUE, new HashMap<>(Map.of(Constants.MOBILE, "9876543210")));
        Map<String, Object> requestWrapper = new HashMap<>();

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("applyNameOrPhoneUpdate", Map.class, Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, valueToUpdate, requestWrapper);

        assertEquals("9876543210", requestWrapper.get(Constants.PHONE));
    }

    // ---------- applyMinistryOrStateDetails / fetchOrgDetails / extractFirstOrgContent (481, 487-488, 721, 732-735, 748, 756, 759) ----------

    @Test
    void applyMinistryOrStateDetails_blankDeptName_doesNotUpdate() throws Exception {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setDeptName("");
        Map<String, Object> updateRequest = new HashMap<>();

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("applyMinistryOrStateDetails", WfRequest.class, Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, wfRequest, updateRequest);

        assertTrue(updateRequest.isEmpty());
    }

    @Test
    void applyMinistryOrStateDetails_orgSearchThrows_doesNotUpdate() throws Exception {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setDeptName("someDept");
        Map<String, Object> updateRequest = new HashMap<>();

        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("/org/search");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any()))
                .thenThrow(new RuntimeException("search failed"));

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("applyMinistryOrStateDetails", WfRequest.class, Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, wfRequest, updateRequest);

        assertTrue(updateRequest.isEmpty());
    }

    @Test
    void applyMinistryOrStateDetails_missingOrganisationType_doesNotUpdate() throws Exception {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setDeptName("someDept");
        Map<String, Object> updateRequest = new HashMap<>();

        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("/org/search");

        Map<String, Object> orgDetail = new HashMap<>(Map.of(Constants.ROOT_ORG_ID, "ROOT01"));
        Map<String, Object> response = Map.of(Constants.CONTENT, List.of(orgDetail));
        Map<String, Object> result = Map.of(Constants.RESPONSE, response);
        Map<String, Object> apiResp = Map.of(Constants.RESPONSE_CODE, "OK", Constants.RESULT, result);
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(apiResp);

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("applyMinistryOrStateDetails", WfRequest.class, Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, wfRequest, updateRequest);

        assertTrue(updateRequest.isEmpty());
    }

    @Test
    void applyMinistryOrStateDetails_spvOrgType_doesNotUpdate() throws Exception {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setDeptName("spvDept");
        Map<String, Object> updateRequest = new HashMap<>();

        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("/org/search");

        Map<String, Object> orgDetail = Map.of(Constants.ORGANISATION_TYPE, Constants.SPV_ORGTYPEVALUE);
        Map<String, Object> response = Map.of(Constants.CONTENT, List.of(orgDetail));
        Map<String, Object> result = Map.of(Constants.RESPONSE, response);
        Map<String, Object> apiResp = Map.of(Constants.RESPONSE_CODE, "OK", Constants.RESULT, result);
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(apiResp);

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("applyMinistryOrStateDetails", WfRequest.class, Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, wfRequest, updateRequest);

        assertTrue(updateRequest.isEmpty());
    }

    @Test
    void applyMinistryOrStateDetails_responseMissingContentList_doesNotUpdate() throws Exception {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setDeptName("someDept");
        Map<String, Object> updateRequest = new HashMap<>();

        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("/org/search");

        Map<String, Object> response = Map.of("notContent", "x");
        Map<String, Object> result = Map.of(Constants.RESPONSE, response);
        Map<String, Object> apiResp = Map.of(Constants.RESPONSE_CODE, "OK", Constants.RESULT, result);
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(apiResp);

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("applyMinistryOrStateDetails", WfRequest.class, Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, wfRequest, updateRequest);

        assertTrue(updateRequest.isEmpty());
    }

    @Test
    void applyMinistryOrStateDetails_responseNotOk_doesNotUpdate() throws Exception {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setDeptName("someDept");
        Map<String, Object> updateRequest = new HashMap<>();

        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("/org/search");

        Map<String, Object> apiResp = Map.of(Constants.RESPONSE_CODE, "CLIENT_ERROR");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(apiResp);

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("applyMinistryOrStateDetails", WfRequest.class, Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, wfRequest, updateRequest);

        assertTrue(updateRequest.isEmpty());
    }

    @Test
    void applyMinistryOrStateDetails_emptyContentList_doesNotUpdate() throws Exception {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setDeptName("someDept");
        Map<String, Object> updateRequest = new HashMap<>();

        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("/org/search");

        Map<String, Object> response = Map.of(Constants.CONTENT, Collections.emptyList());
        Map<String, Object> result = Map.of(Constants.RESPONSE, response);
        Map<String, Object> apiResp = Map.of(Constants.RESPONSE_CODE, "OK", Constants.RESULT, result);
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(apiResp);

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod("applyMinistryOrStateDetails", WfRequest.class, Map.class);
        method.setAccessible(true);
        method.invoke(userProfileWfServiceImpl, wfRequest, updateRequest);

        assertTrue(updateRequest.isEmpty());
    }

    // ---------- getHeadersWithXAuth via assignRole (504) ----------

    @Test
    void assignRole_withUserToken_includesXAuthHeader() {
        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsAssignRoleEndPoint()).thenReturn("/assign");

        ArgumentCaptor<HashMap> headersCaptor = ArgumentCaptor.forClass(HashMap.class);
        Map<String, Object> readData = Map.of(Constants.RESPONSE_CODE, "OK");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), headersCaptor.capture()))
                .thenReturn(readData);

        boolean result = userProfileWfServiceImpl.assignRole("org1", "user1", "token123");

        assertTrue(result);
        assertEquals("token123", headersCaptor.getValue().get(Constants.X_AUTH_TOKEN));
    }

    // ---------- updateUserProfileForBulkUpload (596-597) ----------

    @Test
    void updateUserProfileForBulkUpload_delegatesToUpdateProfile() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(Map.of(Constants.TO_VALUE, new HashMap<>(Map.of("other", "v"))))));

        when(configuration.getLmsServiceHost()).thenReturn("http://lms-host/");
        when(configuration.getUserProfileReadEndPoint()).thenReturn("/user/read/" + Constants.USER_ID_VALUE);
        when(requestServiceImpl.fetchResultUsingGet(any(StringBuilder.class))).thenReturn(null);

        assertDoesNotThrow(() -> userProfileWfServiceImpl.updateUserProfileForBulkUpload(wfRequest));

        verify(requestServiceImpl).fetchResultUsingGet(any(StringBuilder.class));
    }

    // ---------- isEligibleForProfileUpdate: USER_PROFILE_FLAG_SERVICE branch (632) ----------

    @Test
    void updateUserProfileV2_userProfileFlagServiceProcessedState_isEligible() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.USER_PROFILE_FLAG_SERVICE);
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(Map.of(Constants.TO_VALUE, Map.of("other", "value")))));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.PROCESSED_STATE);
        doReturn(wfStatusEntity).when(wfStatusRepo).findByApplicationIdAndWfId(any(), any());
        doReturn(new HashMap<>()).when(userProfileWfServiceImpl).updateRequestWithWF(any(), any());

        assertDoesNotThrow(() -> userProfileWfServiceImpl.updateUserProfileV2(List.of(wfRequest), "userId", null));
    }

    // ---------- updateUserProfileData: null API response branch (668-670) ----------

    @Test
    void updateUserProfileData_nullApiResponse_marksAllFailed() throws Exception {
        String userId = "userId";
        Map<String, Object> profileDetails = new HashMap<>();

        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.getUpdateFieldValues();
        HashMap<String, Object> field = new HashMap<>();
        field.put(Constants.TO_VALUE, new HashMap<>(Map.of("otherField", "value")));
        wfRequest.setUpdateFieldValues(new ArrayList<>(List.of(field)));

        List<WfRequest> wfRequests = Collections.singletonList(wfRequest);

        when(configuration.getLmsServiceHost()).thenReturn("http://lms/");
        when(configuration.getUserProfileUpdateEndPoint()).thenReturn("update");
        when(requestServiceImpl.fetchResultUsingPatch(anyString(), any(), any())).thenReturn(null);

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(wfStatusEntity);
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);

        Method method = UserProfileWfServiceImpl.class.getDeclaredMethod(
                "updateUserProfileData", String.class, Map.class, List.class);
        method.setAccessible(true);

        method.invoke(userProfileWfServiceImpl, userId, profileDetails, wfRequests);

        assertEquals(Constants.FAILED, wfStatusEntity.getCurrentStatus());
    }

}