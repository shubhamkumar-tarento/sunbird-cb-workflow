package org.sunbird.workflow.exception;

import org.junit.jupiter.api.Test;
import org.sunbird.workflow.config.Constants;

import static org.junit.jupiter.api.Assertions.*;

class ResponseCodeTest {

    @Test
    void testGettersAndSettersForErrorBasedResponseCode() {
        ResponseCode code = ResponseCode.UNAUTHORIZED;
        assertEquals(ResponseMessage.Key.UNAUTHORIZED_USER, code.getErrorCode());
        assertEquals(ResponseMessage.Message.UNAUTHORIZED_USER, code.getErrorMessage());

        code.setErrorCode("NEW_ERROR_CODE");
        code.setErrorMessage("New error message");

        assertEquals("NEW_ERROR_CODE", code.getErrorCode());
        assertEquals("New error message", code.getErrorMessage());
    }

    @Test
    void testGettersAndSettersForStatusCodeBasedResponseCode() {
        ResponseCode okCode = ResponseCode.OK;
        assertEquals(200, okCode.getResponseCode());

        okCode.setResponseCode(201);
        assertEquals(201, okCode.getResponseCode());

        // set/get on errorCode & errorMessage for OK
        okCode.setErrorCode("DUMMY_CODE");
        okCode.setErrorMessage("Dummy message");
        assertEquals("DUMMY_CODE", okCode.getErrorCode());
        assertEquals("Dummy message", okCode.getErrorMessage());
    }

    @Test
    void testGetMessage() {
        ResponseCode code = ResponseCode.CLIENT_ERROR;
        assertEquals("", code.getMessage(400)); // always returns empty string
    }

    @Test
    void testGetResponse_knownUnauthorized() {
        ResponseCode result = ResponseCode.getResponse(Constants.UNAUTHORIZED);
        assertEquals(ResponseCode.UNAUTHORIZED, result);
    }

    @Test
    void testGetResponse_knownCode_internalError() {
        ResponseCode result = ResponseCode.getResponse("INTERNAL_ERROR");
        assertEquals(ResponseCode.INTERNAL_ERROR, result);
    }

    @Test
    void testGetResponseWithNull() {
        assertNull(ResponseCode.getResponse(null));
    }

    @Test
    void testEnumValuesCoverage() {
        // This covers enum static method values()
        ResponseCode[] values = ResponseCode.values();
        assertTrue(values.length > 0);
    }

    @Test
    void testEnumValueOfCoverage() {
        ResponseCode code = ResponseCode.valueOf("OK");
        assertEquals(ResponseCode.OK, code);
    }
}
