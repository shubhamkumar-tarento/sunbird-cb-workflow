package org.sunbird.workflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.postgres.entity.WfAuditEntity;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfAuditRepo;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class WorkflowAuditProcessingServiceImpl {

    private final WfAuditRepo wfAuditRepo;
    private final WfStatusRepo wfStatusRepo;

    public void createAudit(WfRequest wfRequest){
        WfStatusEntity wfStatusEntity = wfStatusRepo.findByApplicationIdAndWfId(wfRequest.getApplicationId(), wfRequest.getWfId());
        WfAuditEntity wfAuditEntity = new WfAuditEntity();
        wfAuditEntity.setActorUUID(wfRequest.getActorUserId());
        wfAuditEntity.setComment(wfRequest.getComment());
        wfAuditEntity.setCreatedOn(new Date());
        wfAuditEntity.setAction(wfRequest.getAction());
        wfAuditEntity.setState(wfRequest.getState());
        wfAuditEntity.setRootOrg(wfStatusEntity.getRootOrg());
        wfAuditEntity.setUserId(wfRequest.getUserId());
        wfAuditEntity.setInWorkflow(wfStatusEntity.getInWorkflow());
        wfAuditEntity.setWfId(wfRequest.getWfId());
        wfAuditEntity.setApplicationId(wfRequest.getApplicationId());
        wfAuditEntity.setServiceName(wfRequest.getServiceName());
        wfAuditEntity.setUpdateFieldValues(wfStatusEntity.getUpdateFieldValues());
        wfAuditRepo.save(wfAuditEntity);
    }
}
