package org.sunbird.workflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.config.RedisCacheMgr;
import org.sunbird.workflow.exception.ApplicationException;
import org.sunbird.workflow.models.SBApiResponse;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.service.StorageService;
import org.sunbird.workflow.service.UserBulkUploadService;
import org.sunbird.workflow.service.UserProfileWfService;
import org.sunbird.workflow.utils.CassandraOperation;
import org.sunbird.workflow.utils.UserUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserBulkUploadService4Test {

    @InjectMocks
    private UserBulkUploadService service;

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

    @Mock
    private UserUtil userUtil;

    @TempDir
    File tempDir;

    // ---------- reflection helpers ----------

    private Object invokePrivate(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = UserBulkUploadService.class.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(service, args);
    }

    private Object newNested(String simpleName) throws Exception {
        Class<?> cls = Class.forName("org.sunbird.workflow.service.UserBulkUploadService$" + simpleName);
        Constructor<?> ctor = cls.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private Object getField(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(obj);
    }

    private Object newCounters() throws Exception {
        return newNested("BulkUploadCounters");
    }

    private Object newRowValidation(String phoneNumber, boolean emailValid, boolean phoneValid, boolean existsCheck, Map<String, Object> userDetails) throws Exception {
        Object v = newNested("RowEmailPhoneValidation");
        setField(v, "phoneNumber", phoneNumber);
        setField(v, "isEmailValid", emailValid);
        setField(v, "isPhoneNumberValid", phoneValid);
        setField(v, "isEmailOrPhoneNumberExist", existsCheck);
        setField(v, "userDetails", userDetails);
        return v;
    }

    private Object newEmailPhoneValidation(String phone, boolean emailValid, boolean phoneValid, boolean existsCheck, Map<String, Object> userDetails) throws Exception {
        Object v = newNested("EmailPhoneValidation");
        setField(v, "phone", phone);
        setField(v, "isEmailValid", emailValid);
        setField(v, "isPhoneNumberValid", phoneValid);
        setField(v, "isEmailOrPhoneNumberExist", existsCheck);
        setField(v, "userDetails", userDetails);
        return v;
    }

    private Row rowWithCell(int index, Object value, CellType explicitType) {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        Row row = sheet.createRow(0);
        if (value != null) {
            Cell cell = row.createCell(index);
            if (explicitType == CellType.NUMERIC) {
                cell.setCellValue((Double) value);
            } else if (explicitType == CellType.BOOLEAN) {
                cell.setCellValue((Boolean) value);
            } else {
                cell.setCellValue((String) value);
            }
        }
        return row;
    }

    private CSVRecord csvRecordWithColumn(int index, String value) throws Exception {
        String[] fields = new String[14];
        java.util.Arrays.fill(fields, "");
        if (index >= 0) {
            fields[index] = value;
        }
        String line = String.join(",", fields);
        CSVParser parser = CSVParser.parse(line, CSVFormat.DEFAULT);
        return parser.getRecords().get(0);
    }

    // ---------- initiateUserBulkUploadProcess: CSV_FILE case (lines 85-86) ----------

    @Test
    void testInitiateUserBulkUploadProcess_csvFileCase_real() {
        String json = "{\"rootOrgId\":\"root-1\",\"identifier\":\"bulk-1\",\"fileName\":\"sample.csv\"}";
        // real (unmocked) ObjectMapper field parses this successfully; file won't exist on disk,
        // so processBulkUploadV1's own try/catch swallows the rest without side effects.
        when(configuration.getLocalBasePath()).thenReturn("/tmp/");
        assertDoesNotThrow(() -> service.initiateUserBulkUploadProcess(json));
        verify(storageService).downloadFile("sample.csv");
    }

    // ---------- updateUserBulkUploadStatus exception branch (126-127) ----------

    @Test
    void testUpdateUserBulkUploadStatus_cassandraThrows_logsAndSwallows() {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap()))
                .thenThrow(new RuntimeException("cassandra down"));
        assertDoesNotThrow(() -> service.updateUserBulkUploadStatus("root-1", "id-1", Constants.FAILED_UPPERCASE, 1, 0, 1));
    }

    // ---------- processBulkUpload exception branch (153-155) via corrupt (non-OOXML) file ----------

    @Test
    void testProcessBulkUpload_corruptFile_hitsCatchBlock() throws Exception {
        File badFile = new File(tempDir, "corrupt.xlsx");
        try (java.io.FileWriter fw = new java.io.FileWriter(badFile)) {
            fw.write("this is not a valid xlsx file");
        }
        when(configuration.getLocalBasePath()).thenReturn(tempDir.getAbsolutePath() + File.separator);
        Map<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.FILE_NAME, "corrupt.xlsx");
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-1");
        inputDataMap.put(Constants.IDENTIFIER, "id-1");
        assertDoesNotThrow(() -> invokePrivate("processBulkUpload", new Class[]{HashMap.class}, inputDataMap));
        verify(cassandraOperation, atLeastOnce()).updateRecord(anyString(), anyString(), anyMap(), anyMap());
    }

    // ---------- isProcessableRow (221-228) ----------

    @Test
    void testIsProcessableRow_nullRow_false() throws Exception {
        boolean result = (boolean) invokePrivate("isProcessableRow", new Class[]{Row.class}, (Object) null);
        assertFalse(result);
    }

    @Test
    void testIsProcessableRow_emailOnly_true() throws Exception {
        Row row = rowWithCell(1, "a@b.com", CellType.STRING);
        boolean result = (boolean) invokePrivate("isProcessableRow", new Class[]{Row.class}, row);
        assertTrue(result);
    }

    @Test
    void testIsProcessableRow_neitherEmailNorPhone_false() throws Exception {
        Row row = rowWithCell(-1, null, CellType.STRING);
        boolean result = (boolean) invokePrivate("isProcessableRow", new Class[]{Row.class}, row);
        assertFalse(result);
    }

    // ---------- writeHeaderRow early-return (204) ----------

    @Test
    void testWriteHeaderRow_emptyIterator_returnsEarly() throws Exception {
        List<Row> empty = new ArrayList<>();
        assertDoesNotThrow(() -> invokePrivate("writeHeaderRow", new Class[]{java.util.Iterator.class}, empty.iterator()));
    }

    // ---------- markSheetEmptyFailure (189 call-site covered via direct call) ----------

    @Test
    void testMarkSheetEmptyFailure_setsFailureCells() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        sheet.createRow(0);
        assertDoesNotThrow(() -> invokePrivate("markSheetEmptyFailure", new Class[]{XSSFSheet.class}, sheet));
        assertEquals(Constants.FAILED_UPPERCASE, sheet.getRow(1).getCell(14).getStringCellValue());
    }

    // ---------- Excel per-column validators: validateFullNameColumn (496, 499) ----------

    @Test
    void testValidateFullNameColumn_invalidName_addsError() throws Exception {
        Row row = rowWithCell(0, "123Invalid", CellType.STRING);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateFullNameColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid Full Name"));
    }

    @Test
    void testValidateFullNameColumn_nonString_addsError() throws Exception {
        Row row = rowWithCell(0, 5.0, CellType.NUMERIC);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateFullNameColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for Full Name type. Expecting string format"));
    }

    // ---------- validateGroupColumn (511) ----------

    @Test
    void testValidateGroupColumn_nonString_addsError() throws Exception {
        // Arrays.asList (not List.of) because validateGroupValue(null) below calls .contains(null),
        // and List.of-backed lists throw NPE on contains(null); Arrays.asList does not.
        when(configuration.getGroupValues()).thenReturn(java.util.Arrays.asList("Group1"));
        Row row = rowWithCell(3, 5.0, CellType.NUMERIC);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateGroupColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for Group type. Expecting string format"));
    }

    // ---------- validateDesignationColumn / validateDesignationValue (525-526, 535, 538, 541) ----------

    @Test
    void testValidateDesignationColumn_nonString_addsErrorAndReturns() throws Exception {
        Row row = rowWithCell(4, 5.0, CellType.NUMERIC);
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDesignationColumn", new Class[]{Row.class, Map.class, List.class}, row, values, errList);
        assertTrue(errList.contains("Invalid value for Designation type. Expecting string format"));
        assertFalse(values.containsKey(Constants.DESIGNATION));
    }

    @Test
    void testValidateDesignationValue_blank_returnsEarly() throws Exception {
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDesignationValue", new Class[]{String.class, List.class}, "", errList);
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateDesignationValue_specialCharacter_addsError() throws Exception {
        List<String> errList = new ArrayList<>();
        Map<String, Object> emptyResponse = new HashMap<>();
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(emptyResponse);
        when(configuration.getSearchDesignationResultSize()).thenReturn(10);
        when(configuration.getCbPoresServiceHost()).thenReturn("http://host/");
        when(configuration.getCbPoresMasterDesignationEndpoint()).thenReturn("/designations");
        invokePrivate("validateDesignationValue", new Class[]{String.class, List.class}, "Mgr@1", errList);
        assertTrue(errList.contains("Invalid Designation: Designation should be added from default list and cannot contain special character"));
    }

    @Test
    void testValidateDesignationValue_fieldValueInvalid_addsError() throws Exception {
        List<String> errList = new ArrayList<>();
        Map<String, Object> innerResult = new HashMap<>();
        innerResult.put(Constants.DATA, Collections.emptyList());
        Map<String, Object> outerResult = new HashMap<>();
        outerResult.put(Constants.RESULT, innerResult);
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, outerResult);
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(response);
        when(configuration.getSearchDesignationResultSize()).thenReturn(10);
        when(configuration.getCbPoresServiceHost()).thenReturn("http://host/");
        when(configuration.getCbPoresMasterDesignationEndpoint()).thenReturn("/designations");
        invokePrivate("validateDesignationValue", new Class[]{String.class, List.class}, "Manager", errList);
        assertTrue(errList.contains("Invalid Value of Designation, please choose a valid value from the default list"));
    }

    // ---------- validateGenderColumn (549, 555) ----------

    @Test
    void testValidateGenderColumn_valid_storesValue() throws Exception {
        when(configuration.getBulkUploadGenderValue()).thenReturn(List.of("male"));
        Row row = rowWithCell(5, "male", CellType.STRING);
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateGenderColumn", new Class[]{Row.class, Map.class, List.class}, row, values, errList);
        assertEquals("male", values.get(Constants.GENDER));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateGenderColumn_nonString_addsError() throws Exception {
        Row row = rowWithCell(5, 1.0, CellType.NUMERIC);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateGenderColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for Gender type. Expecting string format"));
    }

    // ---------- validateCategoryColumn (566, 569) ----------

    @Test
    void testValidateCategoryColumn_invalid_addsError() throws Exception {
        when(configuration.getBulkUploadCategoryValue()).thenReturn(List.of("OBC"));
        Row row = rowWithCell(6, "SC", CellType.STRING);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateCategoryColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid Category")));
    }

    @Test
    void testValidateCategoryColumn_nonString_addsError() throws Exception {
        Row row = rowWithCell(6, 1.0, CellType.NUMERIC);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateCategoryColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for Category type. Expecting string format"));
    }

    // ---------- validateDobColumn / validateDobStringCell / validateDobNumericCell (581-582,584,593,598-600,602-606,608,610) ----------

    @Test
    void testValidateDobColumn_invalidStringDate_addsError() throws Exception {
        Row row = rowWithCell(7, "99-99-9999", CellType.STRING);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDobColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid format for Date of Birth type. Expecting in format dd-mm-yyyy"));
    }

    @Test
    void testValidateDobColumn_booleanCell_addsError() throws Exception {
        Row row = rowWithCell(7, Boolean.TRUE, CellType.BOOLEAN);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDobColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for Date of Birth type. Expecting string format"));
    }

    @Test
    void testValidateDobColumn_numericNotDateFormatted_addsError() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        Row row = sheet.createRow(0);
        row.createCell(7).setCellValue(12345.0);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDobColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Cell is numeric but not a date."));
    }

    @Test
    void testValidateDobColumn_numericDateFormatted_validRecentDate_stores() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(7);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.YEAR, -20);
        cell.setCellValue(cal.getTime());
        org.apache.poi.ss.usermodel.CellStyle dateStyle = wb.createCellStyle();
        dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("m/d/yy"));
        cell.setCellStyle(dateStyle);
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDobColumn", new Class[]{Row.class, Map.class, List.class}, row, values, errList);
        assertTrue(values.containsKey(Constants.DOB));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateDobColumn_numericDateFormatted_tooOld_addsError() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(7);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.YEAR, -90);
        cell.setCellValue(cal.getTime());
        org.apache.poi.ss.usermodel.CellStyle dateStyle = wb.createCellStyle();
        dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("m/d/yy"));
        cell.setCellStyle(dateStyle);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDobColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid format for Date of Birth type. Expecting in format dd-mm-yyyy"));
    }

    // ---------- validateMotherTongueColumn (613-618, 620-621) ----------

    @Test
    void testValidateMotherTongueColumn_valid_stores() throws Exception {
        when(redisCacheMgr.keyExists("languages")).thenReturn(true);
        when(redisCacheMgr.valueExists("languages", "english")).thenReturn(true);
        Row row = rowWithCell(8, "English", CellType.STRING);
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateMotherTongueColumn", new Class[]{Row.class, Map.class, List.class}, row, values, errList);
        assertEquals("English", values.get(Constants.DOMICILE_MEDIUM));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateMotherTongueColumn_nonString_addsError() throws Exception {
        Row row = rowWithCell(8, 1.0, CellType.NUMERIC);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateMotherTongueColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for Mother Tongue type. Expecting string format"));
    }

    // ---------- validateEmployeeIdColumn (627-631, 633, 635-637) ----------

    @Test
    void testValidateEmployeeIdColumn_numericValid_stores() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Row row = wb.createSheet().createRow(0);
        row.createCell(9).setCellValue(12345.0);
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateEmployeeIdColumn", new Class[]{Row.class, Map.class, List.class}, row, values, errList);
        assertEquals("12345", values.get(Constants.EMPLOYEE_CODE));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateEmployeeIdColumn_stringInvalid_addsError() throws Exception {
        Row row = rowWithCell(9, "ID#Too#Long#Invalid#Chars#Here#1234567890", CellType.STRING);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateEmployeeIdColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid Employee ID")));
    }

    @Test
    void testValidateEmployeeIdColumn_nonStringNonNumeric_addsError() throws Exception {
        Row row = rowWithCell(9, Boolean.TRUE, CellType.BOOLEAN);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateEmployeeIdColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for Employee ID type. Expecting string/number format"));
    }

    // ---------- validatePinCodeColumn (643-650, 652, 654-655) ----------

    @Test
    void testValidatePinCodeColumn_numericValid_stores() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Row row = wb.createSheet().createRow(0);
        row.createCell(10).setCellValue(560001.0);
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validatePinCodeColumn", new Class[]{Row.class, Map.class, List.class}, row, values, errList);
        assertEquals("560001", values.get(Constants.PIN_CODE));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidatePinCodeColumn_stringInvalid_addsError() throws Exception {
        Row row = rowWithCell(10, "abc12", CellType.STRING);
        List<String> errList = new ArrayList<>();
        invokePrivate("validatePinCodeColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid Office Pin Code")));
    }

    @Test
    void testValidatePinCodeColumn_nonStringNonNumeric_addsError() throws Exception {
        Row row = rowWithCell(10, Boolean.TRUE, CellType.BOOLEAN);
        List<String> errList = new ArrayList<>();
        invokePrivate("validatePinCodeColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for Office Pin Code type. Expecting number/string format"));
    }

    // ---------- validateExternalSystemIdColumn (661-665, 667-670, 673) ----------

    @Test
    void testValidateExternalSystemIdColumn_numericInvalid_addsError() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Row row = wb.createSheet().createRow(0);
        row.createCell(11).setCellValue(-123.0);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateExternalSystemIdColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid External System ID")));
    }

    @Test
    void testValidateExternalSystemIdColumn_stringInvalid_addsError() throws Exception {
        Row row = rowWithCell(11, "bad id!", CellType.STRING);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateExternalSystemIdColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid External System ID")));
    }

    @Test
    void testValidateExternalSystemIdColumn_nonStringNonNumeric_addsError() throws Exception {
        Row row = rowWithCell(11, Boolean.TRUE, CellType.BOOLEAN);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateExternalSystemIdColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for External System ID type. Expecting string/number format"));
    }

    // ---------- validateExternalSystemColumn (679-683, 686) ----------

    @Test
    void testValidateExternalSystemColumn_invalid_addsError() throws Exception {
        Row row = rowWithCell(12, "123456", CellType.STRING);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateExternalSystemColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid External System Name")));
    }

    @Test
    void testValidateExternalSystemColumn_nonString_addsError() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Row row = wb.createSheet().createRow(0);
        row.createCell(12).setCellValue(5.0);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateExternalSystemColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for External System Name type. Expecting string format"));
    }

    // ---------- validateTagColumn (692-699, 702-704, 706-707) ----------

    @Test
    void testValidateTagColumn_invalidTag_addsError() throws Exception {
        Row row = rowWithCell(13, "Tag1,Tag2!", CellType.STRING);
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateTagColumn", new Class[]{Row.class, Map.class, List.class}, row, values, errList);
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid Tag")));
        assertNotNull(values.get(Constants.TAG));
    }

    @Test
    void testValidateTagColumn_nonString_addsError() throws Exception {
        Row row = rowWithCell(13, 1.0, CellType.NUMERIC);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateTagColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid value for Tags type. Expecting string format"));
    }

    // ---------- hasExistenceOrOrgError (299, 301) ----------

    @Test
    void testHasExistenceOrOrgError_emailInvalid() throws Exception {
        Object validation = newRowValidation(null, false, false, false, new HashMap<>());
        List<String> errList = new ArrayList<>();
        HashMap<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-1");
        boolean result = (boolean) invokePrivate("hasExistenceOrOrgError",
                new Class[]{Class.forName("org.sunbird.workflow.service.UserBulkUploadService$RowEmailPhoneValidation"), HashMap.class, List.class},
                validation, inputDataMap, errList);
        assertTrue(result);
        assertTrue(errList.contains("User record does not exist with given email and/or Mobile Number"));
    }

    @Test
    void testHasExistenceOrOrgError_anotherUserHasPhone() throws Exception {
        Object validation = newRowValidation("9876543210", true, true, false, new HashMap<>());
        List<String> errList = new ArrayList<>();
        HashMap<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-1");
        boolean result = (boolean) invokePrivate("hasExistenceOrOrgError",
                new Class[]{Class.forName("org.sunbird.workflow.service.UserBulkUploadService$RowEmailPhoneValidation"), HashMap.class, List.class},
                validation, inputDataMap, errList);
        assertTrue(result);
        assertTrue(errList.contains("Another user record exist with the mobile number"));
    }

    // ---------- applyPendingRequestForStatus / applyPendingEntryUpdate (320-323,328-337,342-363) ----------

    @Test
    void testApplyPendingRequestForStatus_keyNotInValuesToBeUpdate_skipsViaContinue() throws Exception {
        WfStatusEntity entity = new WfStatusEntity();
        String json = "[{\"toValue\":{\"someOtherKey\":\"v\"}}]";
        entity.setUpdateFieldValues(json);
        List<Map<String, Object>> parsed = List.of(Map.of(Constants.TO_VALUE, Map.of("someOtherKey", "v")));
        when(mapper.readValue(eq(json), eq(List.class))).thenReturn(parsed);

        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        StringBuilder str = new StringBuilder();
        Row row = rowWithCell(14, null, CellType.STRING);
        Cell statusCell = row.createCell(14);
        Cell errorDetails = row.createCell(15);

        boolean result = (boolean) invokePrivate("applyPendingRequestForStatus",
                new Class[]{WfStatusEntity.class, Map.class, StringBuilder.class, Cell.class, Cell.class, boolean.class},
                entity, valuesToBeUpdate, str, statusCell, errorDetails, true);
        assertTrue(result);
    }

    @Test
    void testApplyPendingEntryUpdate_notApproved_returnsUnchanged() throws Exception {
        WfStatusEntity entity = new WfStatusEntity();
        entity.setUpdateFieldValues("[]");
        Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>("firstname", "Expected");
        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        valuesToBeUpdate.put("firstname", "Different");
        StringBuilder str = new StringBuilder();
        Row row = rowWithCell(14, null, CellType.STRING);
        Cell statusCell = row.createCell(14);
        Cell errorDetails = row.createCell(15);

        boolean result = (boolean) invokePrivate("applyPendingEntryUpdate",
                new Class[]{WfStatusEntity.class, Map.Entry.class, Map.class, StringBuilder.class, Cell.class, Cell.class, boolean.class},
                entity, entry, valuesToBeUpdate, str, statusCell, errorDetails, true);
        assertTrue(result);
        assertEquals(Constants.REJECTED, entity.getCurrentStatus());
    }

    @Test
    void testApplyPendingEntryUpdate_approvedThenProfileUpdateFails_returnsFalse() throws Exception {
        WfStatusEntity entity = new WfStatusEntity();
        entity.setWfId("wf-1");
        entity.setUpdateFieldValues("[]");
        Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>("firstname", "Expected");
        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        valuesToBeUpdate.put("firstname", "Expected");
        StringBuilder str = new StringBuilder();
        Row row = rowWithCell(14, null, CellType.STRING);
        Cell statusCell = row.createCell(14);
        Cell errorDetails = row.createCell(15);

        WfStatusEntity failedLookup = new WfStatusEntity();
        failedLookup.setCurrentStatus(Constants.REJECTED);
        when(wfStatusRepo.findByWfId("wf-1")).thenReturn(failedLookup);

        boolean result = (boolean) invokePrivate("applyPendingEntryUpdate",
                new Class[]{WfStatusEntity.class, Map.Entry.class, Map.class, StringBuilder.class, Cell.class, Cell.class, boolean.class},
                entity, entry, valuesToBeUpdate, str, statusCell, errorDetails, true);
        assertFalse(result);
        assertFalse(valuesToBeUpdate.containsKey("firstname"));
        verify(userProfileWfService).updateUserProfile(any(WfRequest.class));
    }

    @Test
    void testApplyPendingEntryUpdate_approvedAndProfileUpdateSucceeds_returnsTrue() throws Exception {
        WfStatusEntity entity = new WfStatusEntity();
        entity.setWfId("wf-2");
        entity.setUpdateFieldValues("[]");
        Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>("firstname", "Expected");
        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        valuesToBeUpdate.put("firstname", "Expected");
        StringBuilder str = new StringBuilder();
        Row row = rowWithCell(14, null, CellType.STRING);
        Cell statusCell = row.createCell(14);
        Cell errorDetails = row.createCell(15);

        WfStatusEntity successLookup = new WfStatusEntity();
        successLookup.setCurrentStatus(Constants.APPROVED);
        when(wfStatusRepo.findByWfId("wf-2")).thenReturn(successLookup);

        boolean result = (boolean) invokePrivate("applyPendingEntryUpdate",
                new Class[]{WfStatusEntity.class, Map.Entry.class, Map.class, StringBuilder.class, Cell.class, Cell.class, boolean.class},
                entity, entry, valuesToBeUpdate, str, statusCell, errorDetails, true);
        assertTrue(result);
        assertFalse(valuesToBeUpdate.containsKey("firstname"));
        assertEquals(Constants.SUCCESS_UPPERCASE, statusCell.getStringCellValue());
    }

    // ---------- buildWfRequestForRow (365-407) field-key branching ----------

    @Test
    void testBuildWfRequestForRow_allFieldKeyBranches() throws Exception {
        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        valuesToBeUpdate.put(Constants.EMPLOYEE_CODE, "E1");
        valuesToBeUpdate.put(Constants.GROUP, "G1");
        valuesToBeUpdate.put(Constants.FIRSTNAME, "F1");
        valuesToBeUpdate.put("someUnknownKey", "X1");

        WfRequest result = (WfRequest) invokePrivate("buildWfRequestForRow", new Class[]{Map.class, String.class}, valuesToBeUpdate, "user-1");
        assertNotNull(result);
        assertNotNull(result.getUpdateFieldValues());
    }

    // ---------- applyProfileUpdateForRow / finalizeRowWithProfileUpdate (409-431) ----------

    @Test
    void testApplyProfileUpdateForRow_nullWfRequest_returnsInputUnchanged() throws Exception {
        boolean result = (boolean) invokePrivate("applyProfileUpdateForRow", new Class[]{WfRequest.class, boolean.class}, null, true);
        assertTrue(result);
        verify(userProfileWfService).updateUserProfileForBulkUpload(null);
    }

    @Test
    void testApplyProfileUpdateForRow_rejectedLookup_returnsFalse() throws Exception {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setWfId("wf-3");
        WfStatusEntity failed = new WfStatusEntity();
        failed.setCurrentStatus(Constants.REJECTED);
        when(wfStatusRepo.findByWfId("wf-3")).thenReturn(failed);
        boolean result = (boolean) invokePrivate("applyProfileUpdateForRow", new Class[]{WfRequest.class, boolean.class}, wfRequest, true);
        assertFalse(result);
    }

    @Test
    void testFinalizeRowWithProfileUpdate_failure_setsErrorDetails() throws Exception {
        Object counters = newCounters();
        StringBuilder str = new StringBuilder();
        Row row = rowWithCell(14, null, CellType.STRING);
        Cell statusCell = row.createCell(14);
        Cell errorDetails = row.createCell(15);
        invokePrivate("finalizeRowWithProfileUpdate",
                new Class[]{boolean.class, StringBuilder.class, Cell.class, Cell.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                false, str, statusCell, errorDetails, counters);
        assertEquals(Constants.FAILED_UPPERCASE, statusCell.getStringCellValue());
        assertEquals(1, getField(counters, "failedRecordsCount"));
    }

    // ---------- processBulkUploadRow empty-valuesToBeUpdate branches (471, 474-475, 477, 479-480) ----------

    @Test
    void testProcessBulkUploadRow_emptyUpdates_success() throws Exception {
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put(Constants.USER_ID, "user-1");
        userDetails.put(Constants.ROOT_ORG_ID, "root-1");
        Map<String, Object> userSearchContent = new HashMap<>();
        userSearchContent.put(Constants.USER_ID, "user-1");
        userSearchContent.put(Constants.ROOT_ORG_ID, "root-1");
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE, Map.of(Constants.CONTENT, List.of(userSearchContent)));
        Map<String, Object> userSearchResult = new HashMap<>();
        userSearchResult.put(Constants.RESPONSE_CODE, "OK");
        userSearchResult.put(Constants.RESULT, response);
        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(userSearchResult);
        when(wfStatusRepo.findByUserIdAndCurrentStatus(eq("user-1"), eq(true))).thenReturn(Collections.emptyList());

        XSSFWorkbook wb = new XSSFWorkbook();
        Row row = wb.createSheet().createRow(0);
        row.createCell(1).setCellValue("john@example.com");
        row.createCell(2).setCellValue("9876543210");
        row.createCell(14);
        row.createCell(15);

        Object counters = newCounters();
        HashMap<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-1");
        invokePrivate("processBulkUploadRow",
                new Class[]{Row.class, HashMap.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                row, inputDataMap, counters);
        assertEquals(1, getField(counters, "noOfSuccessfulRecords"));
        assertEquals(1, getField(counters, "totalRecordsCount"));
    }

    // ---------- getWFRequest (769-800) remaining branches ----------

    @Test
    void testGetWFRequest_hashMapBranch() throws Exception {
        Map<String, String> userDetails = new HashMap<>();
        userDetails.put(Constants.USER_ID, "user-9");
        HashMap<String, String> hashMapObj = new HashMap<>(userDetails);
        WfRequest result = (WfRequest) invokePrivate("getWFRequest", new Class[]{Object.class, String.class}, hashMapObj, "app-1");
        assertNotNull(result);
        assertEquals("user-9", result.getUserId());
        assertEquals("app-1", result.getApplicationId());
        assertEquals(Constants.PROFILE_SERVICE_NAME, result.getServiceName());
    }

    @Test
    void testGetWFRequest_unknownObjectType_returnsNull() throws Exception {
        WfRequest result = (WfRequest) invokePrivate("getWFRequest", new Class[]{Object.class, String.class}, "not a recognized type", null);
        assertNull(result);
    }

    // ---------- validateFieldValue extra branches (820 opposite outcome, 827 alt column name) ----------

    @Test
    void testValidateFieldValue_existsInRedis_valueNotPresent() throws Exception {
        when(redisCacheMgr.keyExists("languages")).thenReturn(true);
        when(redisCacheMgr.valueExists("languages", "spanish")).thenReturn(false);
        boolean result = (boolean) invokePrivate("validateFieldValue", new Class[]{String.class, String.class}, "languages", "Spanish");
        assertTrue(result);
    }

    @Test
    void testValidateFieldValue_cassandra_altColumnNameCasing() throws Exception {
        when(redisCacheMgr.keyExists("designation")).thenReturn(false);
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("contextName", "architect");
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), anyList())).thenReturn(List.of(recordMap));
        boolean result = (boolean) invokePrivate("validateFieldValue", new Class[]{String.class, String.class}, "designation", "architect");
        assertFalse(result);
    }

    // ---------- verifyUserRecordExists (public): final false-return and exception branches (763-766) ----------

    @Test
    void testVerifyUserRecordExists_nullResponse_returnsFalse() {
        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(null);
        boolean result = service.verifyUserRecordExists(new HashMap<>(), new HashMap<>());
        assertFalse(result);
    }

    @Test
    void testVerifyUserRecordExists_exception_wrappedAsApplicationException() {
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));
        assertThrows(ApplicationException.class, () -> service.verifyUserRecordExists(new HashMap<>(), new HashMap<>()));
    }

    // ---------- uploadTheUpdatedFile success branch (729) ----------

    @Test
    void testUploadTheUpdatedFile_success_returnsSuccessful() throws Exception {
        File outFile = new File(tempDir, "upload_success.xlsx");
        XSSFWorkbook wb = new XSSFWorkbook();
        wb.createSheet();
        when(configuration.getUserBulkUpdateFolderName()).thenReturn("bulkFolder");
        when(configuration.getWorkflowCloudContainerName()).thenReturn("container");
        SBApiResponse okResponse = mock(SBApiResponse.class);
        when(okResponse.getResponseCode()).thenReturn(HttpStatus.OK);
        when(storageService.uploadFile(any(File.class), anyString(), anyString())).thenReturn(okResponse);
        String result = (String) invokePrivate("uploadTheUpdatedFile", new Class[]{File.class, XSSFWorkbook.class}, outFile, wb);
        assertEquals("SUCCESSFUL", result);
    }

    // ---------- uploadTheUpdatedCSVFile both branches (1452-1457) ----------

    @Test
    void testUploadTheUpdatedCSVFile_success() throws Exception {
        File csvFile = new File(tempDir, "upload.csv");
        when(configuration.getUserBulkUpdateFolderName()).thenReturn("bulkFolder");
        when(configuration.getWorkflowCloudContainerName()).thenReturn("container");
        SBApiResponse okResponse = mock(SBApiResponse.class);
        when(okResponse.getResponseCode()).thenReturn(HttpStatus.OK);
        when(storageService.uploadFile(any(File.class), anyString(), anyString())).thenReturn(okResponse);
        String result = (String) invokePrivate("uploadTheUpdatedCSVFile", new Class[]{File.class}, csvFile);
        assertEquals(Constants.SUCCESS_UPPERCASE, result);
    }

    @Test
    void testUploadTheUpdatedCSVFile_failure() throws Exception {
        File csvFile = new File(tempDir, "upload_fail.csv");
        when(configuration.getUserBulkUpdateFolderName()).thenReturn("bulkFolder");
        when(configuration.getWorkflowCloudContainerName()).thenReturn("container");
        SBApiResponse badResponse = mock(SBApiResponse.class);
        when(badResponse.getResponseCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(badResponse.getParams()).thenReturn(mock(org.sunbird.workflow.models.SunbirdApiRespParam.class));
        when(storageService.uploadFile(any(File.class), anyString(), anyString())).thenReturn(badResponse);
        String result = (String) invokePrivate("uploadTheUpdatedCSVFile", new Class[]{File.class}, csvFile);
        assertEquals(Constants.FAILED_UPPERCASE, result);
    }

    // ---------- getFileExtension / stripSurroundingQuote (1462, 1466-1468, 1470-1471, 1473) ----------

    @Test
    void testGetFileExtension_withDot() throws Exception {
        String result = (String) invokePrivate("getFileExtension", new Class[]{String.class}, "file.csv");
        assertEquals(".csv", result);
    }

    @Test
    void testGetFileExtension_noDot() throws Exception {
        String result = (String) invokePrivate("getFileExtension", new Class[]{String.class}, "filename");
        assertEquals("", result);
    }

    @Test
    void testStripSurroundingQuote_bothSides() throws Exception {
        String result = (String) invokePrivate("stripSurroundingQuote", new Class[]{String.class}, "\"Full Name\"");
        assertEquals("Full Name", result);
    }

    @Test
    void testStripSurroundingQuote_leadingOnly() throws Exception {
        String result = (String) invokePrivate("stripSurroundingQuote", new Class[]{String.class}, "\"Leading");
        assertEquals("Leading", result);
    }

    @Test
    void testStripSurroundingQuote_noQuotes() throws Exception {
        String result = (String) invokePrivate("stripSurroundingQuote", new Class[]{String.class}, "Plain");
        assertEquals("Plain", result);
    }

    // ---------- validateDesignationFieldValue extra branches (1494, 1499) ----------

    @Test
    void testValidateDesignationFieldValue_nullResponse_returnsFalse() {
        when(configuration.getSearchDesignationResultSize()).thenReturn(10);
        when(configuration.getCbPoresServiceHost()).thenReturn("http://host/");
        when(configuration.getCbPoresMasterDesignationEndpoint()).thenReturn("/designations");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(null);
        assertFalse(service.validateDesignationFieldValue("Manager"));
    }

    @Test
    void testValidateDesignationFieldValue_emptyOuterResult_returnsFalse() {
        when(configuration.getSearchDesignationResultSize()).thenReturn(10);
        when(configuration.getCbPoresServiceHost()).thenReturn("http://host/");
        when(configuration.getCbPoresMasterDesignationEndpoint()).thenReturn("/designations");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(new HashMap<>());
        assertFalse(service.validateDesignationFieldValue("Manager"));
    }

    // ---------- getOrCreateMap / getOrCreateFirstProfessionalDetail / isXDetail (1408-1447) ----------

    @Test
    void testGetOrCreateMap_valueNotAMap_returnsNull() throws Exception {
        Map<String, Object> parent = new HashMap<>();
        parent.put("key", "not-a-map");
        Object result = invokePrivate("getOrCreateMap", new Class[]{Map.class, String.class}, parent, "key");
        assertNull(result);
    }

    @Test
    void testGetOrCreateFirstProfessionalDetail_valueNotAList_returnsNull() throws Exception {
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PROFESSIONAL_DETAILS, "not-a-list");
        Object result = invokePrivate("getOrCreateFirstProfessionalDetail", new Class[]{Map.class}, profileDetails);
        assertNull(result);
    }

    @Test
    void testGetOrCreateFirstProfessionalDetail_nonEmptyList_returnsFirstMap() throws Exception {
        Map<String, Object> existingDetail = new HashMap<>();
        existingDetail.put("group", "G1");
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(existingDetail);
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PROFESSIONAL_DETAILS, list);
        Object result = invokePrivate("getOrCreateFirstProfessionalDetail", new Class[]{Map.class}, profileDetails);
        assertSame(existingDetail, result);
    }

    @Test
    void testIsPersonalDetail_mobile_true() throws Exception {
        boolean result = (boolean) invokePrivate("isPersonalDetail", new Class[]{String.class}, Constants.MOBILE);
        assertTrue(result);
    }

    @Test
    void testIsProfessionalDetail_designation_true() throws Exception {
        boolean result = (boolean) invokePrivate("isProfessionalDetail", new Class[]{String.class}, Constants.DESIGNATION);
        assertTrue(result);
    }

    @Test
    void testIsEmploymentDetail_pinCode_true() throws Exception {
        boolean result = (boolean) invokePrivate("isEmploymentDetail", new Class[]{String.class}, Constants.PIN_CODE);
        assertTrue(result);
    }

    // ================= CSV (V1) pipeline helpers =================

    // ---------- validateEmailAndPhonePresenceV1 (976-987) ----------

    @Test
    void testValidateEmailAndPhonePresenceV1_bothMissing() throws Exception {
        List<String> errList = new ArrayList<>();
        invokePrivate("validateEmailAndPhonePresenceV1", new Class[]{boolean.class, boolean.class, List.class}, false, false, errList);
        assertTrue(errList.contains("Both Email and Mobile number are mandatory"));
        assertEquals(1, errList.size());
    }

    @Test
    void testValidateEmailAndPhonePresenceV1_emailMissingOnly() throws Exception {
        List<String> errList = new ArrayList<>();
        invokePrivate("validateEmailAndPhonePresenceV1", new Class[]{boolean.class, boolean.class, List.class}, false, true, errList);
        assertTrue(errList.contains("Email is missing"));
        assertFalse(errList.contains("Mobile number is missing"));
    }

    @Test
    void testValidateEmailAndPhonePresenceV1_phoneMissingOnly() throws Exception {
        List<String> errList = new ArrayList<>();
        invokePrivate("validateEmailAndPhonePresenceV1", new Class[]{boolean.class, boolean.class, List.class}, true, false, errList);
        assertTrue(errList.contains("Mobile number is missing"));
    }

    @Test
    void testValidateEmailAndPhonePresenceV1_bothPresent_noErrors() throws Exception {
        List<String> errList = new ArrayList<>();
        invokePrivate("validateEmailAndPhonePresenceV1", new Class[]{boolean.class, boolean.class, List.class}, true, true, errList);
        assertTrue(errList.isEmpty());
    }

    // ---------- validateEmailFieldV1 (990-1001) ----------

    @Test
    void testValidateEmailFieldV1_notExists_returnsEmptyMap() throws Exception {
        Object result = invokePrivate("validateEmailFieldV1",
                new Class[]{String.class, boolean.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation")},
                null, false, new ArrayList<String>(), newEmailPhoneValidation(null, false, false, false, null));
        assertTrue(((Map<?, ?>) result).isEmpty());
    }

    @Test
    void testValidateEmailFieldV1_invalidPattern_addsError() throws Exception {
        List<String> errList = new ArrayList<>();
        Object result = invokePrivate("validateEmailFieldV1",
                new Class[]{String.class, boolean.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation")},
                "not-an-email", true, errList, newEmailPhoneValidation(null, false, false, false, null));
        assertTrue(errList.contains("Invalid Email format"));
        assertTrue(((Map<?, ?>) result).isEmpty());
    }

    @Test
    void testValidateEmailFieldV1_valid_callsVerify() throws Exception {
        List<String> errList = new ArrayList<>();
        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(null);
        Object result = invokePrivate("validateEmailFieldV1",
                new Class[]{String.class, boolean.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation")},
                "john@example.com", true, errList, newEmailPhoneValidation(null, false, false, false, null));
        assertNotNull(result);
        verify(requestServiceImpl).fetchResultUsingPost(any(), any(), any(), any());
    }

    // ---------- validatePhoneFieldV1 (1005-1016) ----------

    @Test
    void testValidatePhoneFieldV1_notExists_returnsEarly() throws Exception {
        List<String> errList = new ArrayList<>();
        invokePrivate("validatePhoneFieldV1",
                new Class[]{String.class, boolean.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation")},
                null, false, errList, newEmailPhoneValidation(null, false, false, false, null));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidatePhoneFieldV1_invalidPattern_addsError() throws Exception {
        List<String> errList = new ArrayList<>();
        invokePrivate("validatePhoneFieldV1",
                new Class[]{String.class, boolean.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation")},
                "123", true, errList, newEmailPhoneValidation(null, false, false, false, null));
        assertTrue(errList.contains("Invalid Phone number format"));
    }

    @Test
    void testValidatePhoneFieldV1_valid_callsVerify() throws Exception {
        List<String> errList = new ArrayList<>();
        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(null);
        invokePrivate("validatePhoneFieldV1",
                new Class[]{String.class, boolean.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation")},
                "9876543210", true, errList, newEmailPhoneValidation(null, false, false, false, null));
        verify(requestServiceImpl).fetchResultUsingPost(any(), any(), any(), any());
    }

    // ---------- applyEmailOrPhoneExistenceCheckV1 (1019-1027) ----------

    @Test
    void testApplyEmailOrPhoneExistenceCheckV1_conditionFalse_returnsEarly() throws Exception {
        Object validation = newEmailPhoneValidation(null, false, false, false, null);
        invokePrivate("applyEmailOrPhoneExistenceCheckV1",
                new Class[]{String.class, String.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation")},
                "a@b.com", null, validation);
        assertFalse((boolean) getField(validation, "isEmailOrPhoneNumberExist"));
    }

    @Test
    void testApplyEmailOrPhoneExistenceCheckV1_conditionTrue_callsVerify() throws Exception {
        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(null);
        Object validation = newEmailPhoneValidation(null, true, false, false, null);
        invokePrivate("applyEmailOrPhoneExistenceCheckV1",
                new Class[]{String.class, String.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation")},
                "a@b.com", "9876543210", validation);
        verify(requestServiceImpl).fetchResultUsingPost(any(), any(), any(), any());
    }

    // ---------- checkExistenceOrOrgErrorV1 (1139-1164) ----------

    @Test
    void testCheckExistenceOrOrgErrorV1_emailInvalid_recordsFailure() throws Exception {
        Object validation = newEmailPhoneValidation(null, false, false, false, new HashMap<>());
        List<String> errList = new ArrayList<>();
        Map<String, Object> csvValues = new HashMap<>();
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        Object counters = newCounters();
        HashMap<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-1");

        boolean result = (boolean) invokePrivate("checkExistenceOrOrgErrorV1",
                new Class[]{Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation"), Map.class, List.class, Map.class, String.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                validation, inputDataMap, errList, csvValues, ";", updatedRecords, counters);
        assertTrue(result);
        assertEquals(1, updatedRecords.size());
    }

    @Test
    void testCheckExistenceOrOrgErrorV1_anotherUserHasPhone_recordsFailure() throws Exception {
        Object validation = newEmailPhoneValidation(null, true, true, false, new HashMap<>());
        List<String> errList = new ArrayList<>();
        Map<String, Object> csvValues = new HashMap<>();
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        Object counters = newCounters();
        HashMap<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-1");

        boolean result = (boolean) invokePrivate("checkExistenceOrOrgErrorV1",
                new Class[]{Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation"), Map.class, List.class, Map.class, String.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                validation, inputDataMap, errList, csvValues, ";", updatedRecords, counters);
        assertTrue(result);
        assertEquals(1, updatedRecords.size());
    }

    @Test
    void testCheckExistenceOrOrgErrorV1_orgMismatch_recordsFailure() throws Exception {
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put(Constants.ROOT_ORG_ID, "root-other");
        Object validation = newEmailPhoneValidation(null, true, false, true, userDetails);
        List<String> errList = new ArrayList<>();
        Map<String, Object> csvValues = new HashMap<>();
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        Object counters = newCounters();
        HashMap<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-1");

        boolean result = (boolean) invokePrivate("checkExistenceOrOrgErrorV1",
                new Class[]{Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation"), Map.class, List.class, Map.class, String.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                validation, inputDataMap, errList, csvValues, ";", updatedRecords, counters);
        assertTrue(result);
        assertEquals(1, updatedRecords.size());
        assertEquals(1, getField(counters, "failedRecordsCount"));
    }

    @Test
    void testCheckExistenceOrOrgErrorV1_orgMatches_noFailure() throws Exception {
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put(Constants.ROOT_ORG_ID, "root-1");
        Object validation = newEmailPhoneValidation(null, true, false, true, userDetails);
        List<String> errList = new ArrayList<>();
        Map<String, Object> csvValues = new HashMap<>();
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        Object counters = newCounters();
        HashMap<String, String> inputDataMap = new HashMap<>();
        inputDataMap.put(Constants.ROOT_ORG_ID, "root-1");

        boolean result = (boolean) invokePrivate("checkExistenceOrOrgErrorV1",
                new Class[]{Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation"), Map.class, List.class, Map.class, String.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                validation, inputDataMap, errList, csvValues, ";", updatedRecords, counters);
        assertFalse(result);
        assertTrue(updatedRecords.isEmpty());
    }

    // ---------- recordOversizedRowV1 (1130-1137) ----------

    @Test
    void testRecordOversizedRowV1_addsFailureRecord() throws Exception {
        CSVRecord record = csvRecordWithColumn(0, "value");
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        Object counters = newCounters();
        invokePrivate("recordOversizedRowV1",
                new Class[]{CSVRecord.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                record, updatedRecords, counters);
        assertEquals(1, updatedRecords.size());
        assertEquals(1, getField(counters, "totalRecordsCount"));
        assertEquals(1, getField(counters, "failedRecordsCount"));
    }

    // ---------- applyPendingRequestUpdatesV1 / applyPendingRequestForStatusV1 / applyPendingEntryUpdateV1 (1029-1078) ----------

    @Test
    void testApplyPendingRequestUpdatesV1_noPendingRequests_returnsTrue() throws Exception {
        when(wfStatusRepo.findByUserIdAndCurrentStatus("user-1", true)).thenReturn(Collections.emptyList());
        boolean result = (boolean) invokePrivate("applyPendingRequestUpdatesV1",
                new Class[]{String.class, Map.class, Map.class}, "user-1", new HashMap<>(), new HashMap<>());
        assertTrue(result);
    }

    @Test
    void testApplyPendingEntryUpdateV1_approvedAndSucceeds_returnsTrue() throws Exception {
        WfStatusEntity entity = new WfStatusEntity();
        entity.setWfId("wf-5");
        entity.setUpdateFieldValues("[]");
        Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>("firstname", "Expected");
        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        valuesToBeUpdate.put("firstname", "Expected");
        Map<String, Object> csvValues = new HashMap<>();

        WfStatusEntity successLookup = new WfStatusEntity();
        successLookup.setCurrentStatus(Constants.APPROVED);
        when(wfStatusRepo.findByWfId("wf-5")).thenReturn(successLookup);

        boolean result = (boolean) invokePrivate("applyPendingEntryUpdateV1",
                new Class[]{WfStatusEntity.class, Map.Entry.class, Map.class, Map.class, boolean.class},
                entity, entry, valuesToBeUpdate, csvValues, true);
        assertTrue(result);
        assertEquals(Constants.SUCCESSFUL_UPERCASE, csvValues.get(Constants.STATUS_BULK_UPLOAD));
    }

    @Test
    void testApplyPendingEntryUpdateV1_approvedButLookupRejected_returnsFalse() throws Exception {
        WfStatusEntity entity = new WfStatusEntity();
        entity.setWfId("wf-6");
        entity.setUpdateFieldValues("[]");
        Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>("firstname", "Expected");
        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        valuesToBeUpdate.put("firstname", "Expected");
        Map<String, Object> csvValues = new HashMap<>();

        WfStatusEntity failedLookup = new WfStatusEntity();
        failedLookup.setCurrentStatus(Constants.REJECTED);
        when(wfStatusRepo.findByWfId("wf-6")).thenReturn(failedLookup);

        boolean result = (boolean) invokePrivate("applyPendingEntryUpdateV1",
                new Class[]{WfStatusEntity.class, Map.Entry.class, Map.class, Map.class, boolean.class},
                entity, entry, valuesToBeUpdate, csvValues, true);
        assertFalse(result);
        assertEquals(Constants.FAILED_UPPERCASE, csvValues.get(Constants.STATUS_BULK_UPLOAD));
    }

    // ---------- populateValuesToBeUpdateV1 / finalizeRecordWithoutProfileUpdateV1 / finalizeRecordWithProfileUpdateV1 ----------

    @Test
    void testPopulateValuesToBeUpdateV1_setsMobileWhenNotExisting() throws Exception {
        CSVRecord record = csvRecordWithColumn(-1, null);
        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        List<String> errList = new ArrayList<>();
        Object epValidation = newEmailPhoneValidation("9876543210", true, true, false, new HashMap<>());
        invokePrivate("populateValuesToBeUpdateV1",
                new Class[]{CSVRecord.class, Map.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation"), String.class},
                record, valuesToBeUpdate, errList, epValidation, ";");
        assertEquals("9876543210", valuesToBeUpdate.get(Constants.MOBILE));
    }

    @Test
    void testFinalizeRecordWithoutProfileUpdateV1_success() throws Exception {
        Object counters = newCounters();
        Map<String, Object> csvValues = new HashMap<>();
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        invokePrivate("finalizeRecordWithoutProfileUpdateV1",
                new Class[]{boolean.class, Map.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                true, csvValues, updatedRecords, counters);
        assertEquals(1, getField(counters, "noOfSuccessfulRecords"));
        assertEquals(1, updatedRecords.size());
    }

    @Test
    void testFinalizeRecordWithProfileUpdateV1_failure() throws Exception {
        Object counters = newCounters();
        Map<String, Object> csvValues = new HashMap<>();
        invokePrivate("finalizeRecordWithProfileUpdateV1",
                new Class[]{boolean.class, Map.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                false, csvValues, counters);
        assertEquals(Constants.FAILED_UPPERCASE, csvValues.get(Constants.STATUS_BULK_UPLOAD));
        assertEquals(Constants.UPDATE_FAILED, csvValues.get(Constants.ERROR_DETAILS));
    }

    // ---------- processSingleRecordV1 orchestration (1081-1128) ----------

    @Test
    void testProcessSingleRecordV1_oversizedRow_recordsFailure() throws Exception {
        CSVRecord record = csvRecordWithColumn(0, "a");
        List<String> headers = List.of("a");
        Object counters = newCounters();
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        invokePrivate("processSingleRecordV1",
                new Class[]{CSVRecord.class, List.class, Map.class, String.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                record, headers, new HashMap<String, String>(), ";", updatedRecords, counters);
        assertEquals(1, updatedRecords.size());
        assertEquals(1, getField(counters, "failedRecordsCount"));
    }

    @Test
    void testProcessSingleRecordV1_validationErrors_recordsFailure() throws Exception {
        CSVRecord record = csvRecordWithColumn(-1, null); // all 14 fields blank -> both email+phone missing
        List<String> headers = new ArrayList<>(Collections.nCopies(20, "h"));
        Object counters = newCounters();
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        invokePrivate("processSingleRecordV1",
                new Class[]{CSVRecord.class, List.class, Map.class, String.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                record, headers, new HashMap<String, String>(), ";", updatedRecords, counters);
        assertEquals(1, updatedRecords.size());
        assertEquals(1, getField(counters, "failedRecordsCount"));
    }
}
