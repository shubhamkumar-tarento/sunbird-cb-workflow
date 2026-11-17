package org.sunbird.workflow.models;


import java.util.List;

public class SearchCriteriaV2 {

	private List<String> serviceName;

	private List<String> applicationStatus;

	private List<String> applicationIds;

	private Integer limit;

	private Integer offset;

	private  List<String> deptName;

	private String userId;

	public List<String> getServiceName() {
		return serviceName;
	}

	public void setServiceName(List<String> serviceName) {
		this.serviceName = serviceName;
	}

	public List<String> getApplicationStatus() {
		return applicationStatus;
	}

	public void setApplicationStatus(List<String> applicationStatus) {
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
		return (this.applicationStatus == null && this.serviceName == null);
	}

	public List<String> getApplicationIds() {
		return applicationIds;
	}

	public void setApplicationIds(List<String> applicationIds) {
		this.applicationIds = applicationIds;
	}

	public List<String> getDeptName() {
		return deptName;
	}

	public void setDeptName(List<String> deptName) {
		this.deptName = deptName;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

}
