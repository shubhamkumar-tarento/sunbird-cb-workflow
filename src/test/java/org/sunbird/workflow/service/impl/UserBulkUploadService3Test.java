package org.sunbird.workflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.config.RedisCacheMgr;
import org.sunbird.workflow.models.SBApiResponse;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.service.StorageService;
import org.sunbird.workflow.service.UserBulkUploadService;
import org.sunbird.workflow.service.UserProfileWfService;
import org.sunbird.workflow.utils.CassandraOperation;
import org.sunbird.workflow.utils.ValidationUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBulkUploadService3Test {

    @InjectMocks
    private UserBulkUploadService userBulkUploadService;

    @Mock
    private Configuration configuration;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private RequestServiceImpl requestServiceImpl;

    @Mock
    private WfStatusRepo wfStatusRepo;

    @Mock
    private UserProfileWfService userProfileWfService;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private StorageService storageService;

    @Mock
    private RedisCacheMgr redisCacheMgr;

    @TempDir
    File tempDir;

    @Test
    void testProcessBulkUpload() throws Exception {
        // Arrange
        String fileName = "test_bulk.xlsx";
        File excelFile = new File(tempDir, fileName);

        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/user/search");

        // Sample headers and request object
        Map<String, String> headersValue = new HashMap<>();
        headersValue.put("Authorization", "Bearer token");

        Map<String, Object> requestObject = new HashMap<>();
        requestObject.put("query", "test");
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("userId", "user-123");
        contentItem.put("channel", "mdo-dept");
        contentItem.put("rootOrgId", "root-org-1");

        List<Map<String, Object>> contents = List.of(contentItem);

        Map<String, Object> response = new HashMap<>();
        response.put("content", contents);

        Map<String, Object> result = new HashMap<>();
        result.put("response", response);

        Map<String, Object> userSearchResult = new HashMap<>();
        userSearchResult.put("responseCode", "OK");
        userSearchResult.put("result", result);

        // --- Step 2: Mock the method call inside private method ---
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(userSearchResult);


        when(configuration.getUserBulkUpdateFolderName()).thenReturn("bulkFolder");
        when(configuration.getWorkflowCloudContainerName()).thenReturn("container");
        when(configuration.getLocalBasePath()).thenReturn("/tmp/");
        when(configuration.getGroupValues()).thenReturn(List.of("Group1"));
        when(configuration.getBulkUploadCategoryValue()).thenReturn(List.of("OBC"));
        when(configuration.getBulkUploadGenderValue()).thenReturn(List.of("Male"));

// Mock response object and its nested fields
        SBApiResponse mockResponse = mock(SBApiResponse.class);

        when(mockResponse.getResponseCode()).thenReturn(HttpStatus.BAD_REQUEST);

        Map<String, Object> mockUpdateResponse = new HashMap<>();
        mockUpdateResponse.put("status", "success");

        when(cassandraOperation.updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_BULK_UPDATE), anyMap(), anyMap())).thenReturn(mockUpdateResponse);


// Mock the storage service call
        when(storageService.uploadFile(any(File.class), anyString(), anyString())).thenReturn(mockResponse);

        // Create XLSX file with required header row and one data row
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Users");
            Row header = sheet.createRow(0);
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Phone");
            header.createCell(3).setCellValue("group");
            header.createCell(4).setCellValue("designation");
            header.createCell(5).setCellValue("gender");
            header.createCell(6).setCellValue("category");
            header.createCell(7).setCellValue("dob");
            header.createCell(9).setCellValue("employeeCode");
            header.createCell(5).setCellValue("gender");
            header.createCell(14).setCellValue("Status");
            header.createCell(15).setCellValue("Error");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("John Doe");
            row.createCell(1).setCellValue("john.doe@example.com");
            row.createCell(2).setCellValue("9876543210");
            row.createCell(3).setCellValue("A");
            row.createCell(4).setCellValue("Junior ENG");
            row.createCell(5).setCellValue("male");
            row.createCell(6).setCellValue("OBC");
            row.createCell(7).setCellValue("27-07-1996");

            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                workbook.write(fos);
            }
        }

        // Mock constants
        Map<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.FILE_NAME, fileName);
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-org-1");
        inputDataMap.put(Constants.IDENTIFIER, "upload123");

        // Put the file in expected local path
        File localFile = new File("/tmp/" + fileName);
        localFile.getParentFile().mkdirs();
        Files.copy(excelFile.toPath(), localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Mock static methods or utilities used if needed (e.g., ValidationUtil)
        MockedStatic<ValidationUtil> validationUtilMock = mockStatic(ValidationUtil.class);
        validationUtilMock.when(() -> ValidationUtil.validateEmailPattern(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateContactPattern(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateFullName(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validatePinCode(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateDate(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateTag(anyList())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateEmployeeId(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateExternalSystem(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateExternalSystemId(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateRegexPatternWithNoSpecialCharacter(anyString())).thenReturn(true);

        // Act using reflection
        Method method = UserBulkUploadService.class.getDeclaredMethod("processBulkUpload", HashMap.class);
        method.setAccessible(true);
        method.invoke(userBulkUploadService, inputDataMap);

        // Assert
        verify(wfStatusRepo, atLeast(0)).findByUserIdAndCurrentStatus(anyString(), anyBoolean());
        verify(userProfileWfService, atLeast(0)).updateUserProfileForBulkUpload(any());

        // Clean up
        localFile.delete();
        validationUtilMock.close();
    }

    @Test
    void testProcessBulkUpload_1() throws Exception {
        // Arrange
        String fileName = "test_bulk.xlsx";
        File excelFile = new File(tempDir, fileName);

        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/user/search");

        // Sample headers and request object
        Map<String, String> headersValue = new HashMap<>();
        headersValue.put("Authorization", "Bearer token");

        Map<String, Object> requestObject = new HashMap<>();
        requestObject.put("query", "test");
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("userId", "user-123");
        contentItem.put("channel", "mdo-dept");
        contentItem.put("rootOrgId", "rootORG_1");

        List<Map<String, Object>> contents = List.of(contentItem);

        Map<String, Object> response = new HashMap<>();
        response.put("content", contents);

        Map<String, Object> result = new HashMap<>();
        result.put("response", response);

        Map<String, Object> userSearchResult = new HashMap<>();
        userSearchResult.put("responseCode", "OK");
        userSearchResult.put("result", result);

        // --- Step 2: Mock the method call inside private method ---
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(userSearchResult);


        when(configuration.getUserBulkUpdateFolderName()).thenReturn("bulkFolder");
        when(configuration.getWorkflowCloudContainerName()).thenReturn("container");
        when(configuration.getLocalBasePath()).thenReturn("/tmp/");

// Mock response object and its nested fields
        SBApiResponse mockResponse = mock(SBApiResponse.class);

        when(mockResponse.getResponseCode()).thenReturn(HttpStatus.BAD_REQUEST);

        Map<String, Object> mockUpdateResponse = new HashMap<>();
        mockUpdateResponse.put("status", "success");

        when(cassandraOperation.updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_BULK_UPDATE), anyMap(), anyMap())).thenReturn(mockUpdateResponse);


// Mock the storage service call
        when(storageService.uploadFile(any(File.class), anyString(), anyString())).thenReturn(mockResponse);


        // Create XLSX file with required header row and one data row
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Users");
            Row header = sheet.createRow(0);
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Phone");
            header.createCell(14).setCellValue("Status");
            header.createCell(15).setCellValue("Error");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("John Doe");
            row.createCell(1).setCellValue("john.doe@example.com");
            row.createCell(2).setCellValue("9876543210");

            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                workbook.write(fos);
            }
        }

        // Mock constants
        Map<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.FILE_NAME, fileName);
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-org-1");
        inputDataMap.put(Constants.IDENTIFIER, "upload123");

        // Put the file in expected local path
        File localFile = new File("/tmp/" + fileName);
        localFile.getParentFile().mkdirs();
        Files.copy(excelFile.toPath(), localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Mock static methods or utilities used if needed (e.g., ValidationUtil)
        MockedStatic<ValidationUtil> validationUtilMock = mockStatic(ValidationUtil.class);
        validationUtilMock.when(() -> ValidationUtil.validateEmailPattern(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateContactPattern(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateFullName(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validatePinCode(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateDate(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateTag(anyList())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateEmployeeId(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateExternalSystem(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateExternalSystemId(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateRegexPatternWithNoSpecialCharacter(anyString())).thenReturn(true);


        // Act using reflection
        Method method = UserBulkUploadService.class.getDeclaredMethod("processBulkUpload", HashMap.class);
        method.setAccessible(true);
        method.invoke(userBulkUploadService, inputDataMap);

        // Assert
        verify(wfStatusRepo, atLeast(0)).findByUserIdAndCurrentStatus(anyString(), anyBoolean());
        verify(userProfileWfService, atLeast(0)).updateUserProfileForBulkUpload(any());

        // Clean up
        localFile.delete();
        validationUtilMock.close();
    }

    @Test
    void testProcessBulkUpload_EmptyPhone() throws Exception {
        // Arrange
        String fileName = "test_bulk.xlsx";
        File excelFile = new File(tempDir, fileName);

        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/user/search");

        // Sample headers and request object
        Map<String, String> headersValue = new HashMap<>();
        headersValue.put("Authorization", "Bearer token");

        Map<String, Object> requestObject = new HashMap<>();
        requestObject.put("query", "test");
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("userId", "user-123");
        contentItem.put("channel", "mdo-dept");
        contentItem.put("rootOrgId", "root-org-1");

        List<Map<String, Object>> contents = List.of(contentItem);

        Map<String, Object> response = new HashMap<>();
        response.put("content", contents);

        Map<String, Object> result = new HashMap<>();
        result.put("response", response);

        Map<String, Object> userSearchResult = new HashMap<>();
        userSearchResult.put("responseCode", "OK");
        userSearchResult.put("result", result);

        // --- Step 2: Mock the method call inside private method ---
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(userSearchResult);


        when(configuration.getUserBulkUpdateFolderName()).thenReturn("bulkFolder");
        when(configuration.getWorkflowCloudContainerName()).thenReturn("container");
        when(configuration.getLocalBasePath()).thenReturn("/tmp/");

// Mock response object and its nested fields
        SBApiResponse mockResponse = mock(SBApiResponse.class);

        when(mockResponse.getResponseCode()).thenReturn(HttpStatus.BAD_REQUEST);

        Map<String, Object> mockUpdateResponse = new HashMap<>();
        mockUpdateResponse.put("status", "success");

        when(cassandraOperation.updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_BULK_UPDATE), anyMap(), anyMap())).thenReturn(mockUpdateResponse);


// Mock the storage service call
        when(storageService.uploadFile(any(File.class), anyString(), anyString())).thenReturn(mockResponse);

        // Create XLSX file with required header row and one data row
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Users");
            Row header = sheet.createRow(0);
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Phone");
            header.createCell(14).setCellValue("Status");
            header.createCell(15).setCellValue("Error");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("John Doe");
            row.createCell(1).setCellValue("");
            row.createCell(2).setCellValue("");

            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                workbook.write(fos);
            }
        }

        // Mock constants
        Map<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.FILE_NAME, fileName);
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-org-1");
        inputDataMap.put(Constants.IDENTIFIER, "upload123");

        // Put the file in expected local path
        File localFile = new File("/tmp/" + fileName);
        localFile.getParentFile().mkdirs();
        Files.copy(excelFile.toPath(), localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Mock static methods or utilities used if needed (e.g., ValidationUtil)
        MockedStatic<ValidationUtil> validationUtilMock = mockStatic(ValidationUtil.class);
        validationUtilMock.when(() -> ValidationUtil.validateEmailPattern(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateContactPattern(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateFullName(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validatePinCode(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateDate(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateTag(anyList())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateEmployeeId(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateExternalSystem(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateExternalSystemId(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateRegexPatternWithNoSpecialCharacter(anyString())).thenReturn(true);

        // Act using reflection
        Method method = UserBulkUploadService.class.getDeclaredMethod("processBulkUpload", HashMap.class);
        method.setAccessible(true);
        method.invoke(userBulkUploadService, inputDataMap);

        // Assert
        verify(wfStatusRepo, atLeast(0)).findByUserIdAndCurrentStatus(anyString(), anyBoolean());
        verify(userProfileWfService, atLeast(0)).updateUserProfileForBulkUpload(any());

        // Clean up
        localFile.delete();
        validationUtilMock.close();
    }

    @Test
    void testProcessBulkUpload_WithoutPhone() throws Exception {
        // Arrange
        String fileName = "test_bulk.xlsx";
        File excelFile = new File(tempDir, fileName);

        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("/user/search");

        // Sample headers and request object
        Map<String, String> headersValue = new HashMap<>();
        headersValue.put("Authorization", "Bearer token");

        Map<String, Object> requestObject = new HashMap<>();
        requestObject.put("query", "test");
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("userId", "user-123");
        contentItem.put("channel", "mdo-dept");
        contentItem.put("rootOrgId", "root-org-1");

        List<Map<String, Object>> contents = List.of(contentItem);

        Map<String, Object> response = new HashMap<>();
        response.put("content", contents);

        Map<String, Object> result = new HashMap<>();
        result.put("response", response);

        Map<String, Object> userSearchResult = new HashMap<>();
        userSearchResult.put("responseCode", "OK");
        userSearchResult.put("result", result);

        // --- Step 2: Mock the method call inside private method ---
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(userSearchResult);


        when(configuration.getUserBulkUpdateFolderName()).thenReturn("bulkFolder");
        when(configuration.getWorkflowCloudContainerName()).thenReturn("container");
        when(configuration.getLocalBasePath()).thenReturn("/tmp/");


// Mock response object and its nested fields
        SBApiResponse mockResponse = mock(SBApiResponse.class);

        when(mockResponse.getResponseCode()).thenReturn(HttpStatus.BAD_REQUEST);

        Map<String, Object> mockUpdateResponse = new HashMap<>();
        mockUpdateResponse.put("status", "success");

        when(cassandraOperation.updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_BULK_UPDATE), anyMap(), anyMap())).thenReturn(mockUpdateResponse);


// Mock the storage service call
        when(storageService.uploadFile(any(File.class), anyString(), anyString())).thenReturn(mockResponse);

        // Create XLSX file with required header row and one data row
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Users");
            Row header = sheet.createRow(0);
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Phone");
            header.createCell(14).setCellValue("Status");
            header.createCell(15).setCellValue("Error");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("John Doe");
            row.createCell(1).setCellValue("john.doe@example.com");

            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                workbook.write(fos);
            }
        }

        // Mock constants
        Map<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.FILE_NAME, fileName);
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-org-1");
        inputDataMap.put(Constants.IDENTIFIER, "upload123");

        // Put the file in expected local path
        File localFile = new File("/tmp/" + fileName);
        localFile.getParentFile().mkdirs();
        Files.copy(excelFile.toPath(), localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Mock static methods or utilities used if needed (e.g., ValidationUtil)
        MockedStatic<ValidationUtil> validationUtilMock = mockStatic(ValidationUtil.class);
        validationUtilMock.when(() -> ValidationUtil.validateEmailPattern(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateContactPattern(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateFullName(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validatePinCode(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateDate(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateTag(anyList())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateEmployeeId(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateExternalSystem(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateExternalSystemId(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateRegexPatternWithNoSpecialCharacter(anyString())).thenReturn(true);

        // Act using reflection
        Method method = UserBulkUploadService.class.getDeclaredMethod("processBulkUpload", HashMap.class);
        method.setAccessible(true);
        method.invoke(userBulkUploadService, inputDataMap);

        // Assert
        verify(wfStatusRepo, atLeast(0)).findByUserIdAndCurrentStatus(anyString(), anyBoolean());
        verify(userProfileWfService, atLeast(0)).updateUserProfileForBulkUpload(any());

        // Clean up
        localFile.delete();
        validationUtilMock.close();
    }


    @Test
    void testGetWFRequest_WhenObjectIsWfStatusEntity() throws Exception {
        // Arrange
        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setWfId("wf-123");
        wfStatusEntity.setComment("comment");
        wfStatusEntity.setDeptName("dept");
        wfStatusEntity.setRootOrg("org-1");
        wfStatusEntity.setCurrentStatus("status");
        wfStatusEntity.setActorUUID("actor-1");
        wfStatusEntity.setServiceName("service");
        wfStatusEntity.setApplicationId("app-1");

        String json = "[{\"key\": \"value\"}]";
        wfStatusEntity.setUpdateFieldValues(json);

        List<Map<String, String>> expectedList = List.of(Map.of("key", "value"));
        when(mapper.readValue(json, List.class)).thenReturn(expectedList);

        // Use reflection to access private method
        Method method = UserBulkUploadService.class.getDeclaredMethod("getWFRequest", Object.class, String.class);
        method.setAccessible(true);

        // Act
        WfRequest result = (WfRequest) method.invoke(userBulkUploadService, wfStatusEntity, null);

        // Assert
        assertNotNull(result);
        assertEquals("wf-123", result.getWfId());
        assertEquals("comment", result.getComment());
        assertEquals("dept", result.getDeptName());
        assertEquals("org-1", result.getRootOrgId());
        assertEquals("status", result.getState());
        assertEquals("actor-1", result.getActorUserId());
        assertEquals("service", result.getServiceName());
        assertEquals("app-1", result.getApplicationId());
        assertEquals(expectedList, result.getUpdateFieldValues());

        // Verify mapper was called
        verify(mapper).readValue(json, List.class);
    }

    @Test
    void testProcessBulkUpload_RowCount() throws Exception {
        // Arrange
        String fileName = "test_bulk.xlsx";
        File excelFile = new File(tempDir, fileName);

        when(configuration.getUserBulkUpdateFolderName()).thenReturn("bulkFolder");
        when(configuration.getWorkflowCloudContainerName()).thenReturn("container");
        when(configuration.getLocalBasePath()).thenReturn("/tmp/");

// Mock response object and its nested fields
        SBApiResponse mockResponse = mock(SBApiResponse.class);

        when(mockResponse.getResponseCode()).thenReturn(HttpStatus.BAD_REQUEST);

        Map<String, Object> mockUpdateResponse = new HashMap<>();
        mockUpdateResponse.put("status", "success");

        when(cassandraOperation.updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_BULK_UPDATE), anyMap(), anyMap())).thenReturn(mockUpdateResponse);


// Mock the storage service call
        when(storageService.uploadFile(any(File.class), anyString(), anyString())).thenReturn(mockResponse);

        // Create XLSX file with required header row and one data row
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Users");
            Row header = sheet.createRow(0);
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Phone");
            header.createCell(3).setCellValue("group");
            header.createCell(4).setCellValue("designation");
            header.createCell(5).setCellValue("gender");
            header.createCell(6).setCellValue("category");
            header.createCell(7).setCellValue("dob");
            header.createCell(9).setCellValue("employeeCode");
            header.createCell(5).setCellValue("gender");
            header.createCell(14).setCellValue("Status");
            header.createCell(15).setCellValue("Error");

            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                workbook.write(fos);
            }
        }

        // Mock constants
        Map<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.FILE_NAME, fileName);
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-org-1");
        inputDataMap.put(Constants.IDENTIFIER, "upload123");

        // Put the file in expected local path
        File localFile = new File("/tmp/" + fileName);
        localFile.getParentFile().mkdirs();
        Files.copy(excelFile.toPath(), localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Mock static methods or utilities used if needed (e.g., ValidationUtil)
        MockedStatic<ValidationUtil> validationUtilMock = mockStatic(ValidationUtil.class);
        validationUtilMock.when(() -> ValidationUtil.validateEmailPattern(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateContactPattern(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateFullName(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validatePinCode(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateDate(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateTag(anyList())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateEmployeeId(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateExternalSystem(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateExternalSystemId(anyString())).thenReturn(true);
        validationUtilMock.when(() -> ValidationUtil.validateRegexPatternWithNoSpecialCharacter(anyString())).thenReturn(true);

        // Act using reflection
        Method method = UserBulkUploadService.class.getDeclaredMethod("processBulkUpload", HashMap.class);
        method.setAccessible(true);
        method.invoke(userBulkUploadService, inputDataMap);

        // Assert
        verify(wfStatusRepo, atLeast(0)).findByUserIdAndCurrentStatus(anyString(), anyBoolean());
        verify(userProfileWfService, atLeast(0)).updateUserProfileForBulkUpload(any());

        // Clean up
        localFile.delete();
        validationUtilMock.close();
    }

    @Test
    void testValidateGender_valid() {
        List<String> genders = List.of("male", "female");
        when(configuration.getBulkUploadGenderValue()).thenReturn(genders);

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(userBulkUploadService, "validateGender", "male");
        assertTrue(result);
    }

    @Test
    void testValidateGender_invalid() {
        List<String> genders = List.of("male", "female");
        when(configuration.getBulkUploadGenderValue()).thenReturn(genders);

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(userBulkUploadService, "validateGender", "other");
        assertFalse(result);
    }

    @Test
    void testValidateCategory_valid() {
        List<String> categories = List.of("sc", "st");
        when(configuration.getBulkUploadCategoryValue()).thenReturn(categories);

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(userBulkUploadService, "validateCategory", "sc");
        assertTrue(result);
    }

    @Test
    void testValidateCategory_invalid() {
        when(configuration.getBulkUploadCategoryValue()).thenReturn(List.of("sc", "st"));

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(userBulkUploadService, "validateCategory", "obc");
        assertFalse(result);
    }

    @Test
    void testValidateFieldValue_fromCassandra_invalid() {
        String key = "designation";
        String value = "manager";

        when(redisCacheMgr.keyExists(key)).thenReturn(false);

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("contextname", "developer");

        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_MASTER_DATA), anyMap(), anyList())).thenReturn(List.of(recordMap));

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(userBulkUploadService, "validateFieldValue", key, value);
        assertTrue(result);
    }

    @Test
    void testValidateFieldValue_fromCassandra_valid() {
        String key = "designation";
        String value = "developer";

        when(redisCacheMgr.keyExists(key)).thenReturn(false);

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("contextname", "developer");

        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_MASTER_DATA), anyMap(), anyList())).thenReturn(List.of(recordMap));

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(userBulkUploadService, "validateFieldValue", key, value);
        assertFalse(result); // because developer exists in DB
    }


    @Test
    void testValidateGroupValue_valid() {
        List<String> groupValues = List.of("group1", "group2");
        when(configuration.getGroupValues()).thenReturn(groupValues);

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(userBulkUploadService, "validateGroupValue", "group1");
        assertTrue(result);
    }

    @Test
    void testValidateGroupValue_invalid() {
        when(configuration.getGroupValues()).thenReturn(List.of("group1", "group2"));

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(userBulkUploadService, "validateGroupValue", "groupX");
        assertFalse(result);
    }

    @Test
    void testValidateFieldValue_existsInRedis() {
        String key = "designation";
        String value = "engineer";

        when(redisCacheMgr.keyExists(key)).thenReturn(true);
        when(redisCacheMgr.valueExists(key, value.toLowerCase())).thenReturn(true);

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(userBulkUploadService, "validateFieldValue", key, value);
        assertFalse(result);
    }

}

