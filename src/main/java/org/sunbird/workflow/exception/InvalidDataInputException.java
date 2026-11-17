package org.sunbird.workflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
@ResponseBody
public class InvalidDataInputException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	private final String code;
	private final transient Object[] params;

	public String getCode() {
		return code;
	}

	public Object[] getParams() {
		return params;
	}

	public InvalidDataInputException(String code, Object[] params) {
		super();
		this.code = code;
		this.params = params;
	}

	public InvalidDataInputException(String code) {
		super(code);
		this.code = code;
		this.params = null;
	}

	public InvalidDataInputException(String code, Throwable e) {
		super(code, e);
		this.code = code;
		this.params = null;
	}

}
