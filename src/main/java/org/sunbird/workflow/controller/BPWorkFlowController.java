package org.sunbird.workflow.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.*;
import org.sunbird.workflow.service.BPWorkFlowService;
import org.sunbird.workflow.service.QrCodeSelfEnrolmentService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/blendedprogram/workflow")
public class BPWorkFlowController {

    @Autowired
    private BPWorkFlowService bPWorkFlowService;

    @Autowired
    private QrCodeSelfEnrolmentService qrCodeSelfEnrolmentService;


    @PostMapping("/enrol")
    public ResponseEntity<Response> blendedProgramEnrolWf(@RequestHeader String rootOrg, @RequestHeader String org,
                                             @RequestBody WfRequest wfRequest) {
        Response response = bPWorkFlowService.enrolBPWorkFlow(rootOrg, org, wfRequest);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @PostMapping("/unenrol")
    public ResponseEntity<Response> blendedProgramUnEnrolWf(@RequestHeader String rootOrg, @RequestHeader String org,
                                                            @RequestBody WfRequest wfRequest) {
        Response response = bPWorkFlowService.updateBPWorkFlow(rootOrg, org, wfRequest,wfRequest.getUserId(), "");
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @PostMapping("/update/pc")
    public ResponseEntity<Response> blendedProgramWfPCUpdate(@RequestHeader(Constants.X_AUTH_USER_ID) String userId,@RequestHeader String rootOrg, @RequestHeader String org,
                                             @RequestBody WfRequest wfRequest) {
        Response response = bPWorkFlowService.updateBPWorkFlow(rootOrg, org, wfRequest,userId,Constants.PROGRAM_COORDINATOR);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @PostMapping("/update/mdo")
    public ResponseEntity<Response> blendedProgramWfMDOUpdate(@RequestHeader(Constants.X_AUTH_USER_ID) String userId,@RequestHeader String rootOrg, @RequestHeader String org,
                                                           @RequestBody WfRequest wfRequest) {
        Response response = bPWorkFlowService.updateBPWorkFlow(rootOrg, org, wfRequest,userId,Constants.MDO_ADMIN);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @GetMapping(path = "/read/{wfId}/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response> getBlendedProgramWfApplication(@RequestHeader String rootOrg, @RequestHeader String org,
                                                     @PathVariable("wfId") String wfId, @PathVariable("applicationId") String applicationId) {
        Response response = bPWorkFlowService.readBPWFApplication(rootOrg, org, wfId, applicationId);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @PostMapping(path = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response> blendedProgramWfSearch(@RequestHeader String rootOrg, @RequestHeader String org, @RequestBody SearchCriteria searchCriteria) {
        //Department is not eligible filter for the Blended Program Search, marking it as null.
        //if(searchCriteria !=null) searchCriteria.setDeptName(null);
        Response response = bPWorkFlowService.blendedProgramSearch(rootOrg, org, searchCriteria);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @PostMapping(path = "/searchV2/pc", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response> blendedProgramWfPCSearch(@RequestHeader String rootOrg, @RequestHeader String org, @RequestBody SearchCriteriaV2 searchCriteria) {

        Response response = bPWorkFlowService.bpPCSearch(rootOrg, org, searchCriteria);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @PostMapping(path = "/searchV2/mdo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response> blendedProgramWfMDOSearch(@RequestHeader String rootOrg, @RequestHeader String org, @RequestBody SearchCriteriaV2 searchCriteria) {

        Response response = bPWorkFlowService.bpMDOSearch(rootOrg, org, searchCriteria);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @PostMapping(path = "/user/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response> userWfSearch(@RequestHeader(Constants.X_AUTH_USER_ID) String userId, @RequestHeader String rootOrg, @RequestHeader String org, @RequestBody SearchCriteria searchCriteria) {
        Response response = bPWorkFlowService.blendedProgramUserSearch(rootOrg, org, userId, searchCriteria);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @GetMapping(path = "/read/mdo/{wfId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response> getBPApplicationByMdo(
                                                     @PathVariable("wfId") String wfId) {
        Response response = bPWorkFlowService.readBPWFApplication(wfId, false);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @GetMapping(path = "/read/pc/{wfId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response> getBpApplicationByPC(
                                                     @PathVariable("wfId") String wfId) {
        Response response = bPWorkFlowService.readBPWFApplication(wfId, true);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }


    @PostMapping(path = "/stats")
    public ResponseEntity<Response> getBatchStats(@RequestBody Map<String, Object> request) {
        Response response = bPWorkFlowService.readStats(request);
        return new ResponseEntity<Response>(response, response.getResponseCode());
    }

    /**
     * @param rootOrg   - Root Organization Name ex: "igot"
     * @param org       - Organization name ex: "dopt"
     * @param wfRequests - Bulk WorkFlow requests which needs to be processed.
     * @return - Return the response of success/failure after processing the request.
     */
    @PostMapping("/admin/enrol")
    public ResponseEntity<List<Response>> blendedProgramAdminEnrolWf(@RequestHeader String rootOrg, @RequestHeader String org,
                                                                     @RequestBody List<WfRequest> wfRequests)  {
        List<Response> responses = new ArrayList<>();
        for(WfRequest wfRequest:wfRequests){
            Response response = bPWorkFlowService.adminEnrolBPWorkFlow(rootOrg, org, wfRequest);
            responses.add(response);
        }
        return new ResponseEntity<>(responses,HttpStatus.OK);
    }

    @PostMapping("/remove/pc")
    public ResponseEntity<Response> bpPCWfRemove(@RequestHeader(Constants.X_AUTH_USER_ID) String userId,@RequestHeader String rootOrg, @RequestHeader String org,
                                                           @RequestBody WfRequest wfRequest) {
        Response response = bPWorkFlowService.removeBPWorkFlow(rootOrg, org, wfRequest,userId,Constants.PROGRAM_COORDINATOR);
        return new ResponseEntity<>(response, HttpStatus.OK);

    }

    @PostMapping("/remove/mdo")
    public ResponseEntity<Response> bpMDOWfRemove(@RequestHeader(Constants.X_AUTH_USER_ID) String userId,@RequestHeader String rootOrg, @RequestHeader String org,
                                                           @RequestBody WfRequest wfRequest) {
        Response response = bPWorkFlowService.removeBPWorkFlow(rootOrg, org, wfRequest,userId,Constants.MDO_ADMIN);
        return new ResponseEntity<>(response, HttpStatus.OK);

    }

    @PostMapping(path = "/enrol/status/count", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response> blendedProgramWfStatusCount( @RequestBody SearchCriteria searchCriteria) {
        Response response = bPWorkFlowService.blendedProgramStatusCount(searchCriteria);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @PostMapping(path = "/getUserApprovalDataInCsv", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getUserApprovalDataInCsv(@RequestBody SearchCriteria searchCriteria) {
        return bPWorkFlowService.generateUserApprovalCsv(searchCriteria);
    }

    @PostMapping(value = "/bulkApprovalDataFromCsv/{contentId}", consumes = "multipart/form-data")
    public ResponseEntity<?> loadApprovalDataFromCsv(
            @RequestParam("file") MultipartFile file,
            @PathVariable String contentId) throws IOException {
        return bPWorkFlowService.loadApprovalDataFromCsv(file, contentId);
    }

    @PostMapping("/remove/approved/user")
    public ResponseEntity<Response> bpRemoveApprovedUser(@RequestHeader boolean isPc,
                                                         @RequestBody WfRequest wfRequest) {
        Response response = bPWorkFlowService.removeApprovedUser(wfRequest, isPc);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @PostMapping("/nominate")
    public ResponseEntity<Response> nominateUsers(
            @RequestHeader(Constants.X_AUTH_USER_ID) String actorUserId,
            @RequestHeader String rootOrg,
            @RequestHeader String org,
            @RequestBody Map<String, Object> requestBody) {

        Response response = bPWorkFlowService.nominateUsers(rootOrg, org, actorUserId, requestBody);
        return new ResponseEntity<>(response, (HttpStatus) response.get(Constants.STATUS));
    }

    @PostMapping("/qr/enrolments")
    public ResponseEntity<Response> blendedProgramQrEnrol(@RequestHeader String rootOrg,
                                                          @RequestHeader String org,
                                                          @RequestHeader(Constants.X_AUTH_TOKEN) String userAuthToken,
                                                          @RequestBody QrSelfEnrolRequest qrRequest) {

        Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userAuthToken, qrRequest);
        HttpStatus statusCode = (HttpStatus) response.get(Constants.STATUS);
        return new ResponseEntity<>(response, statusCode);
    }

}
