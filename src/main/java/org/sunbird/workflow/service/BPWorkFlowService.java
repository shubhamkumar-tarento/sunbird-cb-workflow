package org.sunbird.workflow.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.sunbird.workflow.models.QrSelfEnrolRequest;
import org.sunbird.workflow.models.Response;
import org.sunbird.workflow.models.SearchCriteria;
import org.sunbird.workflow.models.SearchCriteriaV2;
import org.sunbird.workflow.models.WfRequest;

import java.io.IOException;
import java.util.Map;

public interface BPWorkFlowService {

    public Response enrolBPWorkFlow(String rootOrg, String org, WfRequest wfRequest);

    public Response updateBPWorkFlow(String rootOrg, String org, WfRequest wfRequest,String userId,String role);

    public Response readBPWFApplication(String rootOrg, String org, String wfId, String applicationId);

    public Response blendedProgramSearch(String rootOrg, String org, SearchCriteria criteria);

    public void updateEnrolmentDetails(WfRequest wfRequest);

    Response blendedProgramUserSearch(String rootOrg, String org, String userId, SearchCriteria searchCriteria);

    public Response readBPWFApplication(String wfId, boolean isPc);


    /**

     * This method is responsible for processing the wfRequest based on the state of the wfRequest
     *
     * @param wfRequest - Recieves a wfRequest with the request params.
     */
    public void processWFRequest(WfRequest wfRequest);

    /**
     * Retrieves the statistics of user count in various state for the given courseIds
     * @param request - Request object which contanins the course id list
     * @return - returns the statistics information of user count for each course and batch.
     */
    public Response readStats(Map<String, Object> request);
     /*
     * @param rootOrg   - Root Organization Name ex: "igot"
     * @param org       - Organization name ex: "dopt"
     * @param wfRequest - WorkFlow request which needs to be processed.
     * @return - Return the response of success/failure after processing the request.
     */
    Response adminEnrolBPWorkFlow(String rootOrg, String org, WfRequest wfRequest);

    public Response removeBPWorkFlow(String rootOrg, String org, WfRequest wfRequest,String userId,String role);

    /**
     * This method is responsible for removing a user enrollment details
     *
     * @param wfRequest - Receives a wfRequest with the request params.
     */
    public void removeEnrolmentDetails(WfRequest wfRequest);

    public Response blendedProgramStatusCount(SearchCriteria criteria);

    public Response bpPCSearch(String rootOrg, String org, SearchCriteriaV2 criteria);
    public Response bpMDOSearch(String rootOrg, String org, SearchCriteriaV2 criteria);

    ResponseEntity<ByteArrayResource> generateUserApprovalCsv(SearchCriteria criteria);

    ResponseEntity<?> loadApprovalDataFromCsv(MultipartFile file, String contentId) throws IOException;

    public Response removeApprovedUser(WfRequest wfRequest, boolean isPc);

    public Response nominateUsers(String rootOrg, String org, String actorUserId, Map<String, Object> requestBody);

}
