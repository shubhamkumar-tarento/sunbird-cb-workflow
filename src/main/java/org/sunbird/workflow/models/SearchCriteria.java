package org.sunbird.workflow.models;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

public class SearchCriteria {

	private String serviceName;

	private String applicationStatus;

	private List<String> applicationIds;

	private Integer limit;

	private Integer offset;

	private String deptName;

	private String userId;

	private Map<String, String> sortBy;

	private List<String> requestType;

	private String query;

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public String getApplicationStatus() {
		return applicationStatus;
	}

	public void setApplicationStatus(String applicationStatus) {
		this.applicationStatus = applicationStatus;
	}

	public Integer getLimit() {
		return limit;
	}

	public void setLimit(Integer limit) {
		this.limit = limit;
	}

	public Integer getOffset() {
		return offset;
	}

	public void setOffset(Integer offset) {
		this.offset = offset;
	}

	public boolean isEmpty() {
		return (!StringUtils.hasLength(this.applicationStatus) && !StringUtils.hasLength(this.serviceName));
	}

	public List<String> getApplicationIds() {
		return applicationIds;
	}

	public void setApplicationIds(List<String> applicationIds) {
		this.applicationIds = applicationIds;
	}

	public String getDeptName() {
		return deptName;
	}

	public void setDeptName(String deptName) {
		this.deptName = deptName;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public Map<String, String> getSortBy() {
		return sortBy;
	}

	public void setSortBy(Map<String, String> sortBy) {
		this.sortBy = sortBy;
	}

	public List<String> getRequestType() {
		return requestType;
	}

	public void setRequestType(List<String> requestType) {
		this.requestType = requestType;
	}

	public String getQuery() { return query; }

	public void setQuery(String query) { this.query = query; }
}
