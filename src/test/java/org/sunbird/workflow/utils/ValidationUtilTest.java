package org.sunbird.workflow.utils;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationUtilTest {

    @Test
    void testIsStringNullOREmpty_nullValue() {
        assertTrue(ValidationUtil.isStringNullOREmpty(null));
    }

    @Test
    void testIsStringNullOREmpty_emptyValue() {
        assertTrue(ValidationUtil.isStringNullOREmpty(""));
    }

    @Test
    void testIsStringNullOREmpty_blankValue() {
        assertTrue(ValidationUtil.isStringNullOREmpty("   "));
    }

    @Test
    void testIsStringNullOREmpty_nonEmptyValue() {
        assertFalse(ValidationUtil.isStringNullOREmpty("value"));
    }

    @Test
    void testValidateEmailPattern_valid() {
        assertTrue(ValidationUtil.validateEmailPattern("user@example.com"));
    }

    @Test
    void testValidateEmailPattern_invalid_noAtSymbol() {
        assertFalse(ValidationUtil.validateEmailPattern("userexample.com"));
    }

    @Test
    void testValidateEmailPattern_invalid_noDomain() {
        assertFalse(ValidationUtil.validateEmailPattern("user@"));
    }

    @Test
    void testValidateContactPattern_valid() {
        assertTrue(ValidationUtil.validateContactPattern("9876543210"));
    }

    @Test
    void testValidateContactPattern_invalid_tooShort() {
        assertFalse(ValidationUtil.validateContactPattern("98765"));
    }

    @Test
    void testValidateContactPattern_invalid_nonNumeric() {
        assertFalse(ValidationUtil.validateContactPattern("98765abcde"));
    }

    @Test
    void testValidateDate_validPastDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -20);
        String dateString = new SimpleDateFormat("dd-MM-yyyy").format(calendar.getTime());
        assertTrue(ValidationUtil.validateDate(dateString));
    }

    @Test
    void testValidateDate_tooOld_beyond65Years() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -70);
        String dateString = new SimpleDateFormat("dd-MM-yyyy").format(calendar.getTime());
        assertFalse(ValidationUtil.validateDate(dateString));
    }

    @Test
    void testValidateDate_future_afterToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 1);
        String dateString = new SimpleDateFormat("dd-MM-yyyy").format(calendar.getTime());
        assertFalse(ValidationUtil.validateDate(dateString));
    }

    @Test
    void testValidateDate_today() {
        String dateString = new SimpleDateFormat("dd-MM-yyyy").format(new java.util.Date());
        assertTrue(ValidationUtil.validateDate(dateString));
    }

    @Test
    void testValidateDate_unparseableString_returnsFalse() {
        assertFalse(ValidationUtil.validateDate("not-a-date"));
    }

    @Test
    void testValidateDate_invalidCalendarValues_lenientFalse() {
        assertFalse(ValidationUtil.validateDate("32-13-2020"));
    }

    @Test
    void testValidateExternalSystemId_valid() {
        assertTrue(ValidationUtil.validateExternalSystemId("abc-123"));
    }

    @Test
    void testValidateExternalSystemId_invalid_tooLong() {
        assertFalse(ValidationUtil.validateExternalSystemId("a".repeat(31)));
    }

    @Test
    void testValidateExternalSystemId_invalid_specialCharacters() {
        assertFalse(ValidationUtil.validateExternalSystemId("abc_123!"));
    }

    @Test
    void testValidateExternalSystem_valid() {
        assertTrue(ValidationUtil.validateExternalSystem("System A-1"));
    }

    @Test
    void testValidateExternalSystem_invalid_onlyNumeric() {
        assertFalse(ValidationUtil.validateExternalSystem("12345"));
    }

    @Test
    void testValidateFullName_valid_singleWord() {
        assertTrue(ValidationUtil.validateFullName("John"));
    }

    @Test
    void testValidateFullName_valid_withApostropheAndSpace() {
        assertTrue(ValidationUtil.validateFullName("O'Brien Smith"));
    }

    @Test
    void testValidateFullName_invalid_trailingSpace() {
        assertFalse(ValidationUtil.validateFullName("John "));
    }

    @Test
    void testValidateFullName_invalid_containsDigits() {
        assertFalse(ValidationUtil.validateFullName("John123"));
    }

    @Test
    void testValidateTag_allValid() {
        assertTrue(ValidationUtil.validateTag(List.of("Bihar Circle", "Patna Division")));
    }

    @Test
    void testValidateTag_oneInvalid() {
        assertFalse(ValidationUtil.validateTag(List.of("Valid Tag", "Invalid123")));
    }

    @Test
    void testValidateTag_emptyList() {
        assertTrue(ValidationUtil.validateTag(Collections.emptyList()));
    }

    @Test
    void testValidateEmployeeId_valid() {
        assertTrue(ValidationUtil.validateEmployeeId("EMP123"));
    }

    @Test
    void testValidateEmployeeId_invalid_tooLong() {
        assertFalse(ValidationUtil.validateEmployeeId("A".repeat(31)));
    }

    @Test
    void testValidateEmployeeId_invalid_specialCharacter() {
        assertFalse(ValidationUtil.validateEmployeeId("EMP-123"));
    }

    @Test
    void testValidateRegexPatternWithNoSpecialCharacter_valid() {
        assertTrue(ValidationUtil.validateRegexPatternWithNoSpecialCharacter("Senior Manager (HR)/Lead"));
    }

    @Test
    void testValidateRegexPatternWithNoSpecialCharacter_invalid() {
        assertFalse(ValidationUtil.validateRegexPatternWithNoSpecialCharacter("Manager@HR"));
    }

    @Test
    void testValidateCsvDesignationPattern_valid() {
        assertTrue(ValidationUtil.validateCsvDesignationPattern("Manager", "^[a-zA-Z]+$"));
    }

    @Test
    void testValidateCsvDesignationPattern_invalid() {
        assertFalse(ValidationUtil.validateCsvDesignationPattern("Manager123", "^[a-zA-Z]+$"));
    }

    @Test
    void testValidatePinCode_valid() {
        assertTrue(ValidationUtil.validatePinCode("560001"));
    }

    @Test
    void testValidatePinCode_invalid_tooShort() {
        assertFalse(ValidationUtil.validatePinCode("5600"));
    }

    @Test
    void testValidatePinCode_invalid_nonNumeric() {
        assertFalse(ValidationUtil.validatePinCode("ABCDEF"));
    }
}
