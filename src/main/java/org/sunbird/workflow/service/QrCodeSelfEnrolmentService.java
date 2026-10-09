package org.sunbird.workflow.service;

import org.sunbird.workflow.models.QrSelfEnrolRequest;
import org.sunbird.workflow.models.Response;

public interface QrCodeSelfEnrolmentService {
    public Response enrolQrCodeBPWorkFlow(String rootOrg, String org, String userAuthToken, QrSelfEnrolRequest qrRequest);

}
