package org.sunbird.workflow.models;

public class QrSelfEnrolRequest {

    private String courseId;
    private String batchId;

    public QrSelfEnrolRequest() {
    }

    public QrSelfEnrolRequest(String courseId, String batchId) {
        this.courseId = courseId;
        this.batchId = batchId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
}
