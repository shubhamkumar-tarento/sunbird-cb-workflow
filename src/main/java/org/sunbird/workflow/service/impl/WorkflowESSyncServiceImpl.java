package org.sunbird.workflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.service.WorkflowESSyncService;
import org.sunbird.workflow.utils.ElasticsearchServiceManager;

@Service
@RequiredArgsConstructor
public class WorkflowESSyncServiceImpl implements WorkflowESSyncService {
    Logger logger = LogManager.getLogger(WorkflowESSyncServiceImpl.class);

    private final WfStatusRepo wfStatusRepo;
    private final ElasticsearchServiceManager esServiceManager;

    @Override
    public void syncWithElasticService(WfRequest wfRequest) {
        WfStatusEntity wfStatusEntity = wfStatusRepo.findByWfId(wfRequest.getWfId());
        if (Constants.PROFILE_SERVICE_NAME.equalsIgnoreCase(wfRequest.getServiceName())) {
            switch (wfStatusEntity.getCurrentStatus()) {
                case Constants.SEND_FOR_APPROVAL:
                    if (Constants.ORG_TRANSFER_REQUEST.equalsIgnoreCase(wfStatusEntity.getRequestType())) {
                        esServiceManager.updateWfRequestObject(wfRequest.getWfId(), wfRequest.getUserId(),
                                wfRequest.getDeptName(), Constants.WF_TRANSFER_REQUEST_STRING, true);
                    } else if (Constants.GROUP_CHANGE.equalsIgnoreCase(wfStatusEntity.getRequestType())) {
                        esServiceManager.updateWfRequestObject(wfRequest.getWfId(), wfRequest.getUserId(),
                                wfRequest.getDeptName(), Constants.WF_PROFILE_GROUP_REQUEST_STRING, true);
                    } else if (Constants.DESIGNATION_CHANGE.equalsIgnoreCase(wfStatusEntity.getRequestType())) {
                        esServiceManager.updateWfRequestObject(wfRequest.getWfId(), wfRequest.getUserId(),
                                wfRequest.getDeptName(), Constants.WF_PROFILE_DESIGNATION_REQUEST_STRING, true);
                    }
                    break;
                case Constants.WITHDRAWN, Constants.APPROVED, Constants.REJECTED:
                    if (Constants.ORG_TRANSFER_REQUEST.equalsIgnoreCase(wfStatusEntity.getRequestType())) {
                        esServiceManager.updateWfRequestObject(wfRequest.getWfId(), wfRequest.getUserId(),
                                wfRequest.getDeptName(), Constants.WF_TRANSFER_REQUEST_STRING, false);
                    } else if (Constants.GROUP_CHANGE.equalsIgnoreCase(wfStatusEntity.getRequestType())) {
                        esServiceManager.updateWfRequestObject(wfRequest.getWfId(), wfRequest.getUserId(),
                                wfRequest.getDeptName(), Constants.WF_PROFILE_GROUP_REQUEST_STRING, false);
                    } else if (Constants.DESIGNATION_CHANGE.equalsIgnoreCase(wfStatusEntity.getRequestType())) {
                        esServiceManager.updateWfRequestObject(wfRequest.getWfId(), wfRequest.getUserId(),
                                wfRequest.getDeptName(), Constants.WF_PROFILE_DESIGNATION_REQUEST_STRING, false);
                    }
                    break;
                default:
                    logger.error("Unknown current status for ES Sync request.");
            }
        } else {
            logger.warn("Ignoring to process ESSync request due to unknown service.");
        }
    }
}
