package org.sunbird.workflow.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvalidDataInputExceptionTest {

    @Test
    void testConstructorWithCodeAndParams() {
        String code = "ERR_INVALID_DATA";
        Object[] params = {"name", 123};

        InvalidDataInputException ex = new InvalidDataInputException(code, params);

        assertEquals(code, ex.getCode());
        assertArrayEquals(params, ex.getParams());
    }

    @Test
    void testConstructorWithCodeOnly() {
        String code = "ERR_MISSING_FIELD";

        InvalidDataInputException ex = new InvalidDataInputException(code);

        assertEquals(code, ex.getCode());
        assertEquals(code, ex.getMessage()); // Because it's passed to super()
    }

    @Test
    void testConstructorWithCodeAndCause() {
        String code = "ERR_CAUSE";
        Throwable cause = new RuntimeException("Root cause");

        InvalidDataInputException ex = new InvalidDataInputException(code, cause);

        assertEquals(code, ex.getCode());
        assertEquals(code, ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
