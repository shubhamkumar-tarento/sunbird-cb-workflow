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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
    void testWriteHeaderRow_emptyIterator_returnsEarly() {
        List<Row> empty = new ArrayList<>();
        assertDoesNotThrow(() -> invokePrivate("writeHeaderRow", new Class[]{java.util.Iterator.class}, empty.iterator()));
    }

    // ---------- markSheetEmptyFailure (189 call-site covered via direct call) ----------

    @Test
    void testMarkSheetEmptyFailure_setsFailureCells() {
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

    @ParameterizedTest(name = "{3} rejects non-string cell at column {0}")
    @MethodSource("nonStringColumnRejectionCases")
    void testValidateColumn_nonStringCell_addsError(int columnIndex, Object cellValue, CellType cellType,
            String methodName, String expectedMessage) throws Exception {
        Row row = rowWithCell(columnIndex, cellValue, cellType);
        List<String> errList = new ArrayList<>();
        invokePrivate(methodName, new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.contains(expectedMessage));
    }

    static Stream<Arguments> nonStringColumnRejectionCases() {
        return Stream.of(
                Arguments.of(0, 5.0, CellType.NUMERIC, "validateFullNameColumn", "Invalid value for Full Name type. Expecting string format"),
                Arguments.of(5, 1.0, CellType.NUMERIC, "validateGenderColumn", "Invalid value for Gender type. Expecting string format"),
                Arguments.of(6, 1.0, CellType.NUMERIC, "validateCategoryColumn", "Invalid value for Category type. Expecting string format"),
                Arguments.of(7, "99-99-9999", CellType.STRING, "validateDobColumn", "Invalid format for Date of Birth type. Expecting in format dd-mm-yyyy"),
                Arguments.of(7, Boolean.TRUE, CellType.BOOLEAN, "validateDobColumn", "Invalid value for Date of Birth type. Expecting string format"),
                Arguments.of(8, 1.0, CellType.NUMERIC, "validateMotherTongueColumn", "Invalid value for Mother Tongue type. Expecting string format"),
                Arguments.of(9, Boolean.TRUE, CellType.BOOLEAN, "validateEmployeeIdColumn", "Invalid value for Employee ID type. Expecting string/number format"),
                Arguments.of(10, Boolean.TRUE, CellType.BOOLEAN, "validatePinCodeColumn", "Invalid value for Office Pin Code type. Expecting number/string format")
        );
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

    // ---------- validateCategoryColumn (566, 569) ----------

    @Test
    void testValidateCategoryColumn_invalid_addsError() throws Exception {
        when(configuration.getBulkUploadCategoryValue()).thenReturn(List.of("OBC"));
        Row row = rowWithCell(6, "SC", CellType.STRING);
        List<String> errList = new ArrayList<>();
        invokePrivate("validateCategoryColumn", new Class[]{Row.class, Map.class, List.class}, row, new HashMap<>(), errList);
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid Category")));
    }

    // ---------- validateDobColumn / validateDobStringCell / validateDobNumericCell (581-582,584,593,598-600,602-606,608,610) ----------

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
        when(mapper.readValue(json, List.class)).thenReturn(parsed);

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
        when(wfStatusRepo.findByUserIdAndCurrentStatus("user-1", true)).thenReturn(Collections.emptyList());

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
        Map<String, Object> filters = new HashMap<>();
        Map<String, Object> userRecordDetails = new HashMap<>();
        assertThrows(ApplicationException.class, () -> service.verifyUserRecordExists(filters, userRecordDetails));
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
        CSVRecord csvRecord = csvRecordWithColumn(0, "value");
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        Object counters = newCounters();
        invokePrivate("recordOversizedRowV1",
                new Class[]{CSVRecord.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                csvRecord, updatedRecords, counters);
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
        CSVRecord csvRecord = csvRecordWithColumn(-1, null);
        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        List<String> errList = new ArrayList<>();
        Object epValidation = newEmailPhoneValidation("9876543210", true, true, false, new HashMap<>());
        invokePrivate("populateValuesToBeUpdateV1",
                new Class[]{CSVRecord.class, Map.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$EmailPhoneValidation"), String.class},
                csvRecord, valuesToBeUpdate, errList, epValidation, ";");
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
        CSVRecord csvRecord = csvRecordWithColumn(0, "a");
        List<String> headers = List.of("a");
        Object counters = newCounters();
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        invokePrivate("processSingleRecordV1",
                new Class[]{CSVRecord.class, List.class, Map.class, String.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                csvRecord, headers, new HashMap<String, String>(), ";", updatedRecords, counters);
        assertEquals(1, updatedRecords.size());
        assertEquals(1, getField(counters, "failedRecordsCount"));
    }

    @Test
    void testProcessSingleRecordV1_validationErrors_recordsFailure() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(-1, null); // all 14 fields blank -> both email+phone missing
        List<String> headers = new ArrayList<>(Collections.nCopies(20, "h"));
        Object counters = newCounters();
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        invokePrivate("processSingleRecordV1",
                new Class[]{CSVRecord.class, List.class, Map.class, String.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                csvRecord, headers, new HashMap<String, String>(), ";", updatedRecords, counters);
        assertEquals(1, updatedRecords.size());
        assertEquals(1, getField(counters, "failedRecordsCount"));
    }

    // ===================== Third coverage wave: branch-coverage gaps =====================

    // ---------- updateUserBulkUploadStatus: all-omitted-fields branches (111,114,117,120) ----------

    @Test
    void testUpdateUserBulkUploadStatus_allOptionalFieldsOmitted_onlyDateFieldAdded() {
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        service.updateUserBulkUploadStatus("root-1", "id-1", "", -1, -1, -1);
        verify(cassandraOperation).updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_BULK_UPDATE), captor.capture(), anyMap());
        Map<?, ?> fields = captor.getValue();
        assertFalse(fields.containsKey(Constants.STATUS));
        assertFalse(fields.containsKey(Constants.TOTAL_RECORDS));
        assertFalse(fields.containsKey(Constants.SUCCESSFUL_RECORDS_COUNT));
        assertFalse(fields.containsKey(Constants.FAILED_RECORDS_COUNT));
    }

    // ---------- validateEmailForRow (244-259) ----------

    @Test
    void testValidateEmailForRow_blankCell_returnsEmptyEarly() throws Exception {
        Row row = rowWithCell(-1, null, CellType.STRING);
        List<String> errList = new ArrayList<>();
        Object result = newRowValidation(null, false, false, false, null);
        String email = (String) invokePrivate("validateEmailForRow",
                new Class[]{Row.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$RowEmailPhoneValidation")},
                row, errList, result);
        assertEquals("", email);
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateEmailForRow_invalidPattern_addsErrorAndReturnsEmail() throws Exception {
        Row row = rowWithCell(1, "not-an-email", CellType.STRING);
        List<String> errList = new ArrayList<>();
        Object result = newRowValidation(null, false, false, false, null);
        String email = (String) invokePrivate("validateEmailForRow",
                new Class[]{Row.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$RowEmailPhoneValidation")},
                row, errList, result);
        assertEquals("not-an-email", email);
        assertTrue(errList.contains("The Email provided in Invalid"));
    }

    // ---------- validatePhoneForRow (261-285) ----------

    @Test
    void testValidatePhoneForRow_numericCell_convertsAndValidates() throws Exception {
        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(null);
        Row row = rowWithCell(2, 9876543210.0, CellType.NUMERIC);
        List<String> errList = new ArrayList<>();
        Object result = newRowValidation(null, false, false, false, null);
        invokePrivate("validatePhoneForRow",
                new Class[]{Row.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$RowEmailPhoneValidation")},
                row, errList, result);
        assertEquals("9876543210", getField(result, "phoneNumber"));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidatePhoneForRow_nonStringNonNumeric_addsError() throws Exception {
        Row row = rowWithCell(2, Boolean.TRUE, CellType.BOOLEAN);
        List<String> errList = new ArrayList<>();
        Object result = newRowValidation(null, false, false, false, null);
        invokePrivate("validatePhoneForRow",
                new Class[]{Row.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$RowEmailPhoneValidation")},
                row, errList, result);
        assertTrue(errList.contains("Invalid Value of Mobile Number. Expecting number/string format"));
    }

    @Test
    void testValidatePhoneForRow_invalidPattern_addsError() throws Exception {
        Row row = rowWithCell(2, "123", CellType.STRING);
        List<String> errList = new ArrayList<>();
        Object result = newRowValidation(null, false, false, false, null);
        invokePrivate("validatePhoneForRow",
                new Class[]{Row.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$RowEmailPhoneValidation")},
                row, errList, result);
        assertTrue(errList.contains("The Mobile Number provided is Invalid"));
    }

    // ---------- applyEmailOrPhoneExistenceCheck true-branch (287-295) ----------

    @Test
    void testApplyEmailOrPhoneExistenceCheck_conditionTrue_callsVerify() throws Exception {
        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com/");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(null);
        Object result = newRowValidation("9876543210", true, false, false, new HashMap<>());
        invokePrivate("applyEmailOrPhoneExistenceCheck",
                new Class[]{String.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$RowEmailPhoneValidation")},
                "a@b.com", result);
        verify(requestServiceImpl).fetchResultUsingPost(any(), any(), any(), any());
    }

    // ---------- applyPendingRequestUpdatesForRow + applyPendingRequestForStatus matching-key path (313-338) ----------

    @Test
    void testApplyPendingRequestUpdatesForRow_withPendingEntity_appliesUpdate() throws Exception {
        WfStatusEntity entity = new WfStatusEntity();
        entity.setWfId("wf-10");
        String json = "[{\"toValue\":{\"firstname\":\"Expected\"}}]";
        entity.setUpdateFieldValues(json);
        List<Map<String, Object>> parsed = List.of(Map.of(Constants.TO_VALUE, Map.of("firstname", "Expected")));
        when(mapper.readValue(json, List.class)).thenReturn(parsed);
        when(wfStatusRepo.findByUserIdAndCurrentStatus("user-1", true)).thenReturn(List.of(entity));
        when(wfStatusRepo.findByWfId("wf-10")).thenReturn(entity);

        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        valuesToBeUpdate.put("firstname", "Expected");
        StringBuilder str = new StringBuilder();
        Row row = rowWithCell(14, null, CellType.STRING);
        Cell statusCell = row.createCell(14);
        Cell errorDetails = row.createCell(15);

        boolean result = (boolean) invokePrivate("applyPendingRequestUpdatesForRow",
                new Class[]{String.class, Map.class, StringBuilder.class, Cell.class, Cell.class},
                "user-1", valuesToBeUpdate, str, statusCell, errorDetails);
        assertTrue(result);
        assertFalse(valuesToBeUpdate.containsKey("firstname"));
    }

    // ---------- validateGroupColumn valid path (504-517) ----------

    @Test
    void testValidateGroupColumn_valid_noError() throws Exception {
        when(configuration.getGroupValues()).thenReturn(java.util.Arrays.asList("Group1"));
        Row row = rowWithCell(3, "Group1", CellType.STRING);
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateGroupColumn", new Class[]{Row.class, Map.class, List.class}, row, values, errList);
        assertEquals("Group1", values.get(Constants.GROUP));
        assertTrue(errList.isEmpty());
    }

    // ---------- validateMotherTongueColumn invalid-value path (612-624) ----------

    @Test
    void testValidateMotherTongueColumn_invalidFieldValue_addsError() throws Exception {
        when(redisCacheMgr.keyExists("languages")).thenReturn(true);
        when(redisCacheMgr.valueExists("languages", "klingon")).thenReturn(false);
        Row row = rowWithCell(8, "Klingon", CellType.STRING);
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateMotherTongueColumn", new Class[]{Row.class, Map.class, List.class}, row, values, errList);
        assertTrue(errList.contains("Invalid Mother Tongue: Mother Tongue should be added from default list and/or cannot contain special character(s)"));
    }

    // ===================== CSV (V1) per-column validators: validate*ColumnV1 (previously entirely uncovered) =====================

    @Test
    void testValidateFullNameColumnV1_invalidName_addsError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(0, "123Invalid");
        List<String> errList = new ArrayList<>();
        invokePrivate("validateFullNameColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, new HashMap<>(), errList);
        assertTrue(errList.contains("Invalid Full Name format"));
    }

    @Test
    void testValidateFullNameColumnV1_valid_stores() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(0, "John Doe");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateFullNameColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("John Doe", values.get(Constants.FIRSTNAME));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateGroupColumnV1_invalid_addsError() throws Exception {
        when(configuration.getGroupValues()).thenReturn(java.util.Arrays.asList("Group1"));
        CSVRecord csvRecord = csvRecordWithColumn(3, "GroupX");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateGroupColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("GroupX", values.get(Constants.GROUP));
        assertTrue(errList.contains("Invalid value of Group Type, please choose a valid value from the default list"));
    }

    @Test
    void testValidateGroupColumnV1_valid_noError() throws Exception {
        when(configuration.getGroupValues()).thenReturn(java.util.Arrays.asList("Group1"));
        CSVRecord csvRecord = csvRecordWithColumn(3, "Group1");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateGroupColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("Group1", values.get(Constants.GROUP));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateDesignationColumnV1_patternMismatch_addsError() throws Exception {
        when(configuration.getCsvDesignationRegex()).thenReturn("^NOMATCH$");
        Map<String, Object> innerResult = new HashMap<>();
        innerResult.put(Constants.DATA, List.of(Map.of("id", "d1")));
        Map<String, Object> outerResult = new HashMap<>();
        outerResult.put(Constants.RESULT, innerResult);
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, outerResult);
        when(configuration.getSearchDesignationResultSize()).thenReturn(10);
        when(configuration.getCbPoresServiceHost()).thenReturn("http://host/");
        when(configuration.getCbPoresMasterDesignationEndpoint()).thenReturn("/designations");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(response);
        CSVRecord csvRecord = csvRecordWithColumn(4, "Manager");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDesignationColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("Manager", values.get(Constants.DESIGNATION));
        assertTrue(errList.contains("Invalid Designation: Designation should be added from default list and cannot contain special character"));
        assertFalse(errList.contains("Invalid Value of Designation, please choose a valid value from the default list"));
    }

    @Test
    void testValidateDesignationColumnV1_valid_noError() throws Exception {
        when(configuration.getCsvDesignationRegex()).thenReturn(".*");
        Map<String, Object> innerResult = new HashMap<>();
        innerResult.put(Constants.DATA, List.of(Map.of("id", "d1")));
        Map<String, Object> outerResult = new HashMap<>();
        outerResult.put(Constants.RESULT, innerResult);
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, outerResult);
        when(configuration.getSearchDesignationResultSize()).thenReturn(10);
        when(configuration.getCbPoresServiceHost()).thenReturn("http://host/");
        when(configuration.getCbPoresMasterDesignationEndpoint()).thenReturn("/designations");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(response);
        CSVRecord csvRecord = csvRecordWithColumn(4, "Manager");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDesignationColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("Manager", values.get(Constants.DESIGNATION));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateGenderColumnV1_invalid_addsError() throws Exception {
        when(configuration.getBulkUploadGenderValue()).thenReturn(List.of("Male"));
        CSVRecord csvRecord = csvRecordWithColumn(5, "Female");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateGenderColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertFalse(values.containsKey(Constants.GENDER));
        assertTrue(errList.stream().anyMatch(e -> e.startsWith("Invalid Gender")));
    }

    @Test
    void testValidateGenderColumnV1_valid_stores() throws Exception {
        when(configuration.getBulkUploadGenderValue()).thenReturn(List.of("Male"));
        CSVRecord csvRecord = csvRecordWithColumn(5, "Male");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateGenderColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("Male", values.get(Constants.GENDER));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateCategoryColumnV1_invalid_addsError() throws Exception {
        when(configuration.getBulkUploadCategoryValue()).thenReturn(List.of("OBC"));
        CSVRecord csvRecord = csvRecordWithColumn(6, "SC");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateCategoryColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertFalse(values.containsKey(Constants.CATEGORY));
        assertTrue(errList.stream().anyMatch(e -> e.startsWith("Invalid Category")));
    }

    @Test
    void testValidateCategoryColumnV1_valid_stores() throws Exception {
        when(configuration.getBulkUploadCategoryValue()).thenReturn(List.of("OBC"));
        CSVRecord csvRecord = csvRecordWithColumn(6, "OBC");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateCategoryColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("OBC", values.get(Constants.CATEGORY));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateDobColumnV1_invalid_addsError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(7, "99-99-9999");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDobColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertTrue(errList.contains("Invalid format for Date of Birth type. Expecting in format dd-mm-yyyy"));
        assertFalse(values.containsKey(Constants.DOB));
    }

    @Test
    void testValidateDobColumnV1_valid_stores() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(7, "01-01-1990");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateDobColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("01-01-1990", values.get(Constants.DOB));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateMotherTongueColumnV1_invalidFieldValue_addsError() throws Exception {
        when(redisCacheMgr.keyExists("languages")).thenReturn(true);
        when(redisCacheMgr.valueExists("languages", "klingon")).thenReturn(false);
        CSVRecord csvRecord = csvRecordWithColumn(8, "Klingon");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateMotherTongueColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("Klingon", values.get(Constants.DOMICILE_MEDIUM));
        assertTrue(errList.contains("Invalid Mother Tongue: Mother Tongue should be added from default list and/or cannot contain special character(s)"));
    }

    @Test
    void testValidateMotherTongueColumnV1_valid_noError() throws Exception {
        when(redisCacheMgr.keyExists("languages")).thenReturn(true);
        when(redisCacheMgr.valueExists("languages", "english")).thenReturn(true);
        CSVRecord csvRecord = csvRecordWithColumn(8, "English");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateMotherTongueColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("English", values.get(Constants.DOMICILE_MEDIUM));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateEmployeeIdColumnV1_invalid_addsError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(9, "ID#Invalid!");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateEmployeeIdColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("ID#Invalid!", values.get(Constants.EMPLOYEE_CODE));
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid Employee ID")));
    }

    @Test
    void testValidateEmployeeIdColumnV1_valid_noError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(9, "EMP001");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateEmployeeIdColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("EMP001", values.get(Constants.EMPLOYEE_CODE));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidatePinCodeColumnV1_invalid_addsError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(10, "abc12");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validatePinCodeColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("abc12", values.get(Constants.PIN_CODE));
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid Pin Code")));
    }

    @Test
    void testValidatePinCodeColumnV1_valid_noError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(10, "560001");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validatePinCodeColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("560001", values.get(Constants.PIN_CODE));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateExternalSystemIdColumnV1_invalid_addsError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(11, "bad id!");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateExternalSystemIdColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("bad id!", values.get(Constants.EXTERNAL_SYSTEM_ID));
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid External System ID")));
    }

    @Test
    void testValidateExternalSystemIdColumnV1_valid_noError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(11, "SYS123");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateExternalSystemIdColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("SYS123", values.get(Constants.EXTERNAL_SYSTEM_ID));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateExternalSystemColumnV1_invalid_addsError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(12, "123456");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateExternalSystemColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("123456", values.get(Constants.EXTERNAL_SYSTEM));
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid External System Name")));
    }

    @Test
    void testValidateExternalSystemColumnV1_valid_noError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(12, "ExtSystem");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateExternalSystemColumnV1", new Class[]{CSVRecord.class, Map.class, List.class}, csvRecord, values, errList);
        assertEquals("ExtSystem", values.get(Constants.EXTERNAL_SYSTEM));
        assertTrue(errList.isEmpty());
    }

    @Test
    void testValidateTagColumnV1_invalid_addsError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(13, "Tag1;Tag2");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateTagColumnV1", new Class[]{CSVRecord.class, Map.class, List.class, String.class}, csvRecord, values, errList, ";");
        assertNotNull(values.get(Constants.TAG));
        assertTrue(errList.stream().anyMatch(e -> e.contains("Invalid Tag")));
    }

    @Test
    void testValidateTagColumnV1_valid_noError() throws Exception {
        CSVRecord csvRecord = csvRecordWithColumn(13, "Bihar Circle;Patna Division");
        Map<String, Object> values = new HashMap<>();
        List<String> errList = new ArrayList<>();
        invokePrivate("validateTagColumnV1", new Class[]{CSVRecord.class, Map.class, List.class, String.class}, csvRecord, values, errList, ";");
        assertNotNull(values.get(Constants.TAG));
        assertTrue(errList.isEmpty());
    }

    // ---------- applyPendingRequestForStatusV1 (previously entirely uncovered) ----------

    @Test
    void testApplyPendingRequestForStatusV1_matchingKey_appliesUpdate() throws Exception {
        WfStatusEntity entity = new WfStatusEntity();
        entity.setWfId("wf-10");
        String json = "[{\"toValue\":{\"firstname\":\"Expected\"}}]";
        entity.setUpdateFieldValues(json);
        List<Map<String, Object>> parsed = List.of(Map.of(Constants.TO_VALUE, Map.of("firstname", "Expected")));
        when(mapper.readValue(json, List.class)).thenReturn(parsed);
        WfStatusEntity successLookup = new WfStatusEntity();
        successLookup.setCurrentStatus(Constants.APPROVED);
        when(wfStatusRepo.findByWfId("wf-10")).thenReturn(successLookup);

        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        valuesToBeUpdate.put("firstname", "Expected");
        Map<String, Object> csvValues = new HashMap<>();

        boolean result = (boolean) invokePrivate("applyPendingRequestForStatusV1",
                new Class[]{WfStatusEntity.class, Map.class, Map.class, boolean.class},
                entity, valuesToBeUpdate, csvValues, true);
        assertTrue(result);
        assertEquals(Constants.SUCCESSFUL_UPERCASE, csvValues.get(Constants.STATUS_BULK_UPLOAD));
    }

    @Test
    void testApplyPendingEntryUpdateV1_notApproved_returnsUnchanged() throws Exception {
        WfStatusEntity entity = new WfStatusEntity();
        entity.setUpdateFieldValues("[]");
        Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>("firstname", "Expected");
        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        valuesToBeUpdate.put("firstname", "Different");
        Map<String, Object> csvValues = new HashMap<>();

        boolean result = (boolean) invokePrivate("applyPendingEntryUpdateV1",
                new Class[]{WfStatusEntity.class, Map.Entry.class, Map.class, Map.class, boolean.class},
                entity, entry, valuesToBeUpdate, csvValues, true);
        assertTrue(result);
        assertEquals(Constants.REJECTED, entity.getCurrentStatus());
        assertTrue(valuesToBeUpdate.containsKey("firstname"));
    }

    // ---------- finalizeRecordWithoutProfileUpdateV1 failure branch (1186-1195) ----------

    @Test
    void testFinalizeRecordWithoutProfileUpdateV1_failure_incrementsFailedCount() throws Exception {
        Object counters = newCounters();
        Map<String, Object> csvValues = new HashMap<>();
        List<Map<String, Object>> updatedRecords = new ArrayList<>();
        invokePrivate("finalizeRecordWithoutProfileUpdateV1",
                new Class[]{boolean.class, Map.class, List.class, Class.forName("org.sunbird.workflow.service.UserBulkUploadService$BulkUploadCounters")},
                false, csvValues, updatedRecords, counters);
        assertEquals(1, getField(counters, "failedRecordsCount"));
        assertEquals(1, updatedRecords.size());
    }

    // ---------- updateBulkUserProfile deeper structural branches (1351-1406) ----------

    @Test
    void testUpdateBulkUserProfile_profileDetailsNotAMap_returnsFalse() throws Exception {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put(Constants.PROFILE_DETAILS, "not-a-map");
        when(userUtil.userProfileRead("user-x")).thenReturn(userResponse);
        boolean result = (boolean) invokePrivate("updateBulkUserProfile", new Class[]{String.class, Map.class}, "user-x", new HashMap<>());
        assertFalse(result);
    }

    @Test
    void testUpdateBulkUserProfile_personalDetailsNotAMap_returnsFalse() throws Exception {
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PERSONAL_DETAILS, "not-a-map");
        profileDetails.put(Constants.EMPLOYMENT_DETAILS, new HashMap<String, Object>());
        profileDetails.put(Constants.PROFESSIONAL_DETAILS, new ArrayList<Map<String, Object>>());
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put(Constants.PROFILE_DETAILS, profileDetails);
        when(userUtil.userProfileRead("user-y")).thenReturn(userResponse);
        boolean result = (boolean) invokePrivate("updateBulkUserProfile", new Class[]{String.class, Map.class}, "user-y", new HashMap<>());
        assertFalse(result);
    }

    @Test
    void testUpdateBulkUserProfile_routesToAllThreeDetailCategories() throws Exception {
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PERSONAL_DETAILS, new HashMap<String, Object>());
        profileDetails.put(Constants.EMPLOYMENT_DETAILS, new HashMap<String, Object>());
        profileDetails.put(Constants.PROFESSIONAL_DETAILS, new ArrayList<Map<String, Object>>());
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put(Constants.PROFILE_DETAILS, profileDetails);
        when(userUtil.userProfileRead("user-z")).thenReturn(userResponse);
        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com");
        when(configuration.getUserProfileUpdateEndPoint()).thenReturn("/user/v1/update");
        when(requestServiceImpl.fetchResultUsingPatch(anyString(), anyMap(), anyMap()))
                .thenReturn(Collections.singletonMap(Constants.RESPONSE_CODE, Constants.OK));

        Map<String, Object> valuesToBeUpdate = new HashMap<>();
        valuesToBeUpdate.put(Constants.FIRSTNAME, "Jane");
        valuesToBeUpdate.put(Constants.GROUP, "G1");
        valuesToBeUpdate.put(Constants.EMPLOYEE_CODE, "E1");

        boolean result = (boolean) invokePrivate("updateBulkUserProfile", new Class[]{String.class, Map.class}, "user-z", valuesToBeUpdate);
        assertTrue(result);

        Map<String, Object> personalDetails = (Map<String, Object>) profileDetails.get(Constants.PERSONAL_DETAILS);
        Map<String, Object> employmentDetails = (Map<String, Object>) profileDetails.get(Constants.EMPLOYMENT_DETAILS);
        List<Map<String, Object>> professionalDetailsList = (List<Map<String, Object>>) profileDetails.get(Constants.PROFESSIONAL_DETAILS);
        assertEquals("Jane", personalDetails.get(Constants.FIRSTNAME));
        assertEquals("E1", employmentDetails.get(Constants.EMPLOYEE_CODE));
        assertEquals("G1", professionalDetailsList.get(0).get(Constants.GROUP));
    }

    @Test
    void testUpdateBulkUserProfile_patchReturnsNonOk_returnsFalse() throws Exception {
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PERSONAL_DETAILS, new HashMap<String, Object>());
        profileDetails.put(Constants.EMPLOYMENT_DETAILS, new HashMap<String, Object>());
        profileDetails.put(Constants.PROFESSIONAL_DETAILS, new ArrayList<Map<String, Object>>());
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put(Constants.PROFILE_DETAILS, profileDetails);
        when(userUtil.userProfileRead("user-w")).thenReturn(userResponse);
        when(configuration.getLmsServiceHost()).thenReturn("http://lms.example.com");
        when(configuration.getUserProfileUpdateEndPoint()).thenReturn("/user/v1/update");
        when(requestServiceImpl.fetchResultUsingPatch(anyString(), anyMap(), anyMap())).thenReturn(null);

        boolean result = (boolean) invokePrivate("updateBulkUserProfile", new Class[]{String.class, Map.class}, "user-w", new HashMap<>());
        assertFalse(result);
    }

    @Test
    void testGetOrCreateFirstProfessionalDetail_firstElementNotAMap_returnsNull() throws Exception {
        List<Object> list = new ArrayList<>();
        list.add("not-a-map");
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PROFESSIONAL_DETAILS, list);
        Object result = invokePrivate("getOrCreateFirstProfessionalDetail", new Class[]{Map.class}, profileDetails);
        assertNull(result);
    }
}
