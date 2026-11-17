package org.sunbird.workflow.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectCommonExceptionTest {

    @Test
    void testConstructorAndGetters() {
        String code = "ERR_CODE";
        String message = "An error occurred";
        int responseCode = 400;

        ProjectCommonException exception = new ProjectCommonException(code, message, responseCode);

        assertEquals(code, exception.getCode());
        assertEquals(message, exception.getMessage());
        assertEquals(responseCode, exception.getResponseCode());
    }
}
