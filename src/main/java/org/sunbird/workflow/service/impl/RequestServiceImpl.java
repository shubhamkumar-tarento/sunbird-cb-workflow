package org.sunbird.workflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RequestServiceImpl {

	Logger log = LogManager.getLogger(RequestServiceImpl.class);

	private static final String FETCH_RESULT_LOG_PREFIX = ".fetchResult:";
	private static final String URI_LOG_PREFIX = "URI: ";
	private static final String REQUEST_LOG_PREFIX = "Request: ";
	private static final String EXTERNAL_SERVICE_EXCEPTION_LOG = "External Service threw an Exception: ";

	private final ObjectMapper mapper;
	private final RestTemplate restTemplate;
	private final Configuration configuration;

	public Object fetchResult(StringBuilder uri, Object request, Class<?> classType) {
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		Object response = null;
		StringBuilder str = new StringBuilder(this.getClass().getCanonicalName()).append(FETCH_RESULT_LOG_PREFIX)
				.append(System.lineSeparator());
		str.append(URI_LOG_PREFIX).append(uri.toString()).append(System.lineSeparator());
		try {
			str.append(REQUEST_LOG_PREFIX).append(mapper.writeValueAsString(request)).append(System.lineSeparator());
			log.debug(str.toString());
		} catch (JsonProcessingException e) {
			log.error("Json processing exception occured: ", e);
		}
		return restTemplate.postForObject(uri.toString(), request, classType);
	}

	public Object fetchResultUsingGet(StringBuilder uri) {
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		Object response = null;
		StringBuilder str = new StringBuilder(this.getClass().getCanonicalName()).append(FETCH_RESULT_LOG_PREFIX)
				.append(System.lineSeparator());
		str.append(URI_LOG_PREFIX).append(uri.toString()).append(System.lineSeparator());
		try {
			log.debug(str.toString());
			response = restTemplate.getForObject(uri.toString(), Map.class);
		} catch (HttpClientErrorException e) {
			log.error(EXTERNAL_SERVICE_EXCEPTION_LOG, e);
		} catch (Exception e) {
			log.error("Exception while fetching from searcher: ", e);
		}
		return response;
	}

	/**
	 *
	 * @param uri
	 * @param request
	 * @return
	 * @throws Exception
	 */
	public Object fetchResultUsingPost(StringBuilder uri, Object request, Class<?> objectType,HashMap<String, String> headersValue) {
		ObjectMapper localMapper = new ObjectMapper();
		localMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		Object response = null;
		StringBuilder str = new StringBuilder(this.getClass().getCanonicalName()).append(FETCH_RESULT_LOG_PREFIX)
				.append(System.lineSeparator());
		str.append(URI_LOG_PREFIX).append(uri.toString()).append(System.lineSeparator());
		try {
			str.append(REQUEST_LOG_PREFIX).append(localMapper.writeValueAsString(request)).append(System.lineSeparator());
			String message = str.toString();
			log.info(message);
			HttpHeaders headers = new HttpHeaders();
			if (!ObjectUtils.isEmpty(headersValue)) {
				for (Map.Entry<String, String> map : headersValue.entrySet()) {
					headers.set(map.getKey(), map.getValue());
				}
			}
			headers.set(Constants.ROOT_ORG_CONSTANT, configuration.getHubRootOrg());
			HttpEntity<Object> entity = new HttpEntity<>(request, headers);
			response = restTemplate.postForObject(uri.toString(), entity, objectType);
		} catch (HttpClientErrorException e) {
			log.error(EXTERNAL_SERVICE_EXCEPTION_LOG, e);
		} catch (Exception e) {
			log.error("Exception occured while calling the exteranl service: ", e);
		}
		return response;
	}

	public Map<String, Object> fetchResultUsingPatch(String uri, Object request, Map<String, String> headersValues) {
		ObjectMapper localMapper = new ObjectMapper();
		localMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		Map<String, Object> response = null;
		try {
			HttpHeaders headers = new HttpHeaders();
			if (!CollectionUtils.isEmpty(headersValues)) {
				headersValues.forEach((k, v) -> headers.set(k, v));
			}
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Object> entity = new HttpEntity<>(request, headers);
			if (log.isDebugEnabled()) {
				try {
					StringBuilder str = new StringBuilder(this.getClass().getCanonicalName()).append(".fetchResult")
							.append(System.lineSeparator());
					str.append(URI_LOG_PREFIX).append(uri).append(System.lineSeparator());
					str.append(REQUEST_LOG_PREFIX).append(localMapper.writeValueAsString(request)).append(System.lineSeparator());
					log.debug(str.toString());
				} catch (JsonProcessingException je) {
				}
			}
			response = restTemplate.patchForObject(uri, entity, Map.class);
			if (log.isDebugEnabled()) {
				try {
					StringBuilder str = new StringBuilder("Response: ");
					str.append(localMapper.writeValueAsString(response)).append(System.lineSeparator());
					log.debug(str.toString());
				} catch (JsonProcessingException je) {
				}
			}
		} catch (HttpClientErrorException e) {
			try {
				response = localMapper.readValue(e.getResponseBodyAsString(), new TypeReference<HashMap<String, Object>>() {
				});
				response.put(Constants.HTTP_STATUS_CODE, e.getStatusCode());
			} catch (Exception e1) {
			}
			log.error("Error received: " + e.getResponseBodyAsString(), e);
		}
		return response;
	}

	public Object fetchResultUsingPostUnhandled(StringBuilder uri, Object request, Class<?> objectType,HashMap<String, String> headersValue) throws IOException {
		ObjectMapper localMapper = new ObjectMapper();
		localMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		Object response = null;
		StringBuilder str = new StringBuilder(this.getClass().getCanonicalName()).append(FETCH_RESULT_LOG_PREFIX)
				.append(System.lineSeparator());
		str.append(URI_LOG_PREFIX).append(uri.toString()).append(System.lineSeparator());
		try {
			str.append(REQUEST_LOG_PREFIX).append(localMapper.writeValueAsString(request)).append(System.lineSeparator());
			String message = str.toString();
			log.info(message);
			HttpHeaders headers = new HttpHeaders();
			if (!ObjectUtils.isEmpty(headersValue)) {
				for (Map.Entry<String, String> map : headersValue.entrySet()) {
					headers.set(map.getKey(), map.getValue());
				}
			}
			headers.set(Constants.ROOT_ORG_CONSTANT, configuration.getHubRootOrg());
			HttpEntity<Object> entity = new HttpEntity<>(request, headers);
			response = restTemplate.postForObject(uri.toString(), entity, objectType);
		} catch (HttpClientErrorException e) {
			log.error(EXTERNAL_SERVICE_EXCEPTION_LOG, e);
			throw e;
		} catch (IOException e) {
			log.error("Exception occured while calling the exteranl service: ", e);
			throw e;
		} catch (RuntimeException e) {
			log.error("Exception occured while calling the exteranl service: ", e);
			throw e;
		}
		return response;
	}
}
