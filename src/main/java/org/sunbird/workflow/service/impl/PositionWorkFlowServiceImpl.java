package org.sunbird.workflow.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.sunbird.workflow.models.Response;
import org.sunbird.workflow.models.SearchCriteria;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.service.PositionWorkFlowService;
import org.sunbird.workflow.service.Workflowservice;

@Service
public class PositionWorkFlowServiceImpl implements PositionWorkFlowService {

    private final Workflowservice workflowService;

    public PositionWorkFlowServiceImpl(@Qualifier("workflowServiceImpl") Workflowservice workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public Response createPositionWorkFlow(String rootOrg, String org, WfRequest wfRequest) {
        return workflowService.workflowTransition(rootOrg, org, wfRequest);
    }

    @Override
    public Response updatePositionWorkFlow(String rootOrg, String org, WfRequest wfRequest) {
        return createPositionWorkFlow(rootOrg, org, wfRequest);
    }

    @Override
    public Response readPositionWFApplication(String rootOrg, String org, String wfId, String applicationId) {
        return workflowService.getWfApplication(rootOrg, org, wfId, applicationId);
    }

    @Override
    public Response positionSearch(String rootOrg, String org, SearchCriteria criteria) {
        return workflowService.applicationsSearch(rootOrg, org, criteria);
    }
}
