package org.sunbird.workflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.consumer.ApplicationProcessingConsumer;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.models.WfStatus;
import org.sunbird.workflow.models.notification.Config;
import org.sunbird.workflow.models.notification.NotificationRequest;
import org.sunbird.workflow.models.notification.Template;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.service.ContentReadService;
import org.sunbird.workflow.service.Workflowservice;
import org.sunbird.workflow.utils.CassandraOperation;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import static org.sunbird.workflow.config.Constants.*;

@Service
public class NotificationServiceImpl {

	public static final String EMAILTEMPLATE = "emailtemplate";
	Logger logger = LogManager.getLogger(ApplicationProcessingConsumer.class);

	private final WfStatusRepo wfStatusRepo;
	private final Configuration configuration;
	private final RequestServiceImpl requestService;
	private final ObjectMapper mapper;
	private final Workflowservice workflowservice;
	private final UserProfileWfServiceImpl userProfileWfService;
	private final CassandraOperation cassandraOperation;
	private final ContentReadService contentReadService;

	public NotificationServiceImpl(WfStatusRepo wfStatusRepo, Configuration configuration,
			RequestServiceImpl requestService, ObjectMapper mapper,
			@Qualifier("workflowServiceImpl") Workflowservice workflowservice,
			UserProfileWfServiceImpl userProfileWfService, CassandraOperation cassandraOperation,
			ContentReadService contentReadService) {
		this.wfStatusRepo = wfStatusRepo;
		this.configuration = configuration;
		this.requestService = requestService;
		this.mapper = mapper;
		this.workflowservice = workflowservice;
		this.userProfileWfService = userProfileWfService;
		this.cassandraOperation = cassandraOperation;
		this.contentReadService = contentReadService;
	}

	private static final String WORK_FLOW_EVENT_NAME = "workflow_service_notification";

	private static final String USER_NAME_CONSTANT = "user";

	private static final String USER_NAME_TAG = "#userName";

	private static final String STATE_NAME_TAG = "#state";

	private static final String DUE_TO_PREFIX = ", due to <b>";

	private static final String BOLD_CLOSE_SUFFIX = "</b>.";

	private static final String FIELD_KEY_TAG = "#fieldKey";

	private static final String TO_VALUE_TAG = "#toValue";

	private static final String COMMENT_TAG = "#comment";

	private static final String TO_VALUE_CONST = "toValue";

	private static final String MAIL_SUBJECT = "Your request is #state";

	private static final String MDO_MAIL_SUBJECT = "Request for approval";

	private static final String MAIL_BODY = "Your request to update #fieldKey to #toValue is #state ";

	private static final String BP_MAIL_BODY = "Your request for batch enrollment is #state.";

	private static final String COURSE_NAME_TAG = "#course_name";

	private static final String BATCH_NAME_TAG = "#batch_name";

	private static final String ACT_TAG = "#action";

	private static final String BATCH_START_DATE_TAG = "#batch_start_date";

	private static final String ENROLMENT_ACTION_SUBJECT = "Enrollment #action";

	private static final String BP_MDO_PC_SUBJECT_LINE = "Enrollment Request Forwarded to #role";

	private static final String USERNAMAE_TAG = "#username";

	private static final String BLENDED_PROGRAME_NAME_TAG = "#blended_programme_name";

	private static final String ROLE_TAG = "#role";

	/**
	 * Send notification to the user based on state of application
	 *
	 * @param wfRequest workflow request
	 */
	public void sendNotification(WfRequest wfRequest) {
		WfStatusEntity wfStatusEntity = wfStatusRepo.findByApplicationIdAndWfId(wfRequest.getApplicationId(),
				wfRequest.getWfId());
		WfStatus wfStatus = workflowservice.getWorkflowStates(wfStatusEntity.getRootOrg(), wfStatusEntity.getOrg(),
				wfStatusEntity.getServiceName(), wfStatusEntity.getCurrentStatus());
		try {
			logger.info("Notification workflow status entity, {}", mapper.writeValueAsString(wfStatusEntity));
			logger.info("Notification workflow status model, {}", mapper.writeValueAsString(wfStatus));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		if (Constants.PROFILE_SERVICE_NAME.equalsIgnoreCase(wfRequest.getServiceName()) && Constants.WITHDRAWN.equalsIgnoreCase(wfStatusEntity.getCurrentStatus())) {
			return;
		}
		if (Boolean.TRUE.equals(wfStatus.getNotificationEnable())) {
			logger.info("Enter's in the notification block");
            Set<String> usersId = new HashSet<>();
            usersId.add(wfRequest.getUserId());
            Set<String> blendedProgrammeServiceNames = new HashSet<>(Arrays.asList(Constants.BLENDED_PROGRAM_SERVICE_NAME,
                    Constants.ONE_STEP_MDO_APPROVAL,
                    Constants.ONE_STEP_PC_APPROVAL,
                    Constants.TWO_STEP_MDO_AND_PC_APPROVAL,
                    Constants.TWO_STEP_PC_AND_MDO_APPROVAL));
			if(!blendedProgrammeServiceNames.contains(wfRequest.getServiceName())){
				usersId.add(wfStatusEntity.getApplicationId());
			}
			HashMap<String, Object> usersObj = userProfileWfService.getUsersResult(usersId);
			Map<String, Object> senderInfo = (Map<String, Object>)usersObj.get(wfRequest.getUserId());
			Optional<HashMap<String, Object>> updatedFieldValue = wfRequest.getUpdateFieldValues().stream().findFirst();
			String subjectLine = "";
			String body = "";
			if (updatedFieldValue.isPresent()) {
				if (blendedProgrammeServiceNames.contains(wfRequest.getServiceName())) {
					String forwardedMailBody = configuration.getLearnerForwardedMailBody()
							.replace(COURSE_NAME_TAG, wfRequest.getCourseName())
							.replace(BATCH_NAME_TAG, wfRequest.getBatchName())
							.replace(BATCH_START_DATE_TAG, wfRequest.getBatchStartDate().toString());
					switch (wfStatusEntity.getCurrentStatus()){
						case Constants.SEND_FOR_PC_APPROVAL:
							subjectLine = BP_MDO_PC_SUBJECT_LINE.replace(ROLE_TAG,Constants.PROGRAM_COORDINATOR.replace("_"," "));
							body = forwardedMailBody.replace(ROLE_TAG,Constants.PROGRAM_COORDINATOR.replace("_"," "));
							break;
						case Constants.SEND_FOR_MDO_APPROVAL:
							subjectLine = BP_MDO_PC_SUBJECT_LINE.replace(ROLE_TAG,Constants.MDO_ADMIN.split("_")[0]);
							body = forwardedMailBody.replace(ROLE_TAG,Constants.MDO_ADMIN.split("_")[0]);
							break;
						case Constants.APPROVED:
							subjectLine = ENROLMENT_ACTION_SUBJECT.replace(ACT_TAG, wfStatusEntity.getCurrentStatus());
							body = configuration.getApprovedMailBody()
									.replace(BATCH_NAME_TAG, wfRequest.getBatchName())
									.replace(COURSE_NAME_TAG, wfRequest.getCourseName())
									.replace(BATCH_START_DATE_TAG, wfRequest.getBatchStartDate().toString());
							break;
						case Constants.REJECTED, Constants.REMOVED:
							subjectLine = ENROLMENT_ACTION_SUBJECT.replace(ACT_TAG, wfStatusEntity.getCurrentStatus());
							body = configuration.getRejectedOrRemovedMailBody()
									.replace(BATCH_NAME_TAG, wfRequest.getBatchName())
									.replace(COURSE_NAME_TAG, wfRequest.getCourseName())
									.replace(ACT_TAG,wfStatusEntity.getCurrentStatus())
									.replace(BATCH_START_DATE_TAG, wfRequest.getBatchStartDate().toString());
							break;
						default:
							subjectLine = MAIL_SUBJECT.replace(STATE_NAME_TAG, wfStatusEntity.getCurrentStatus());
							body = BP_MAIL_BODY.replace(STATE_NAME_TAG, wfStatusEntity.getCurrentStatus());
							break;
					}
				}
				else {
					HashMap<String, Object> toValue = (HashMap<String, Object>) updatedFieldValue.get().get(TO_VALUE_CONST);
					body = MAIL_BODY.replace(STATE_NAME_TAG, wfStatusEntity.getCurrentStatus())
							.replace(FIELD_KEY_TAG, toValue.entrySet().iterator().next().getKey()).replace(TO_VALUE_TAG, (String) toValue.entrySet().iterator().next().getValue());
					subjectLine = MAIL_SUBJECT.replace(STATE_NAME_TAG, wfStatusEntity.getCurrentStatus());
				}
			}
			if (StringUtils.isNotBlank(wfRequest.getComment())) {
				body = body.substring(0, body.length() - 1) + DUE_TO_PREFIX + wfRequest.getComment() + BOLD_CLOSE_SUFFIX;
			}
			Map<String, Object> mailNotificationDetails = new HashMap<>();
			mailNotificationDetails.put(Constants.EMAIL_TO, senderInfo.get(Constants.FIRST_NAME));
			mailNotificationDetails.put("body", body);
			mailNotificationDetails.put(Constants.SUBJECT, subjectLine);
			mailNotificationDetails.put(Constants.EMAIL_LIST, Collections.singletonList(senderInfo.get(Constants.EMAIL)));
			sendNotificationEmail(mailNotificationDetails);
		}
	}

	public void sendEmailNotification(WfRequest wfRequest) {
		WfStatusEntity wfStatusEntity = wfStatusRepo.findByApplicationIdAndWfId(wfRequest.getApplicationId(),
				wfRequest.getWfId());
		WfStatus wfStatus = workflowservice.getWorkflowStates(wfStatusEntity.getRootOrg(), wfStatusEntity.getOrg(),
				wfStatusEntity.getServiceName(), wfStatusEntity.getCurrentStatus());
		try {
			logger.info("Notification workflow status entity, {}", mapper.writeValueAsString(wfStatusEntity));
			logger.info("Notification workflow status model, {}", mapper.writeValueAsString(wfStatus));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		if (Boolean.TRUE.equals(wfStatus.getNotificationEnable())) {
			logger.info("Enters in the email notification block");
			List<HashMap<String, Object>> updatedFieldValues = wfRequest.getUpdateFieldValues();
			Set<String> emailSet = new HashSet<>();
			// Find the email address from the updated field values
			for (Map<String, Object> fieldValue : updatedFieldValues) {
				if (fieldValue.containsKey(Constants.EMAIL)) {
					String email = (String) fieldValue.get(Constants.EMAIL);
					emailSet.add(email);
					break;
				}
			}
			if (!emailSet.isEmpty()) {
				HashMap<String, Object> params = new HashMap<>();
				NotificationRequest request = new NotificationRequest();
				request.setDeliveryType(Constants.MESSAGE);
				request.setIds(new ArrayList<>(emailSet));
				request.setMode(Constants.EMAIL);
				Template template = new Template();
				template.setId(EMAILTEMPLATE);
				Optional<HashMap<String, Object>> updatedFieldValue = wfRequest.getUpdateFieldValues().stream().findFirst();
				if (updatedFieldValue.isPresent()) {
					HashMap<String, Object> toValue = (HashMap<String, Object>) updatedFieldValue.get().get(TO_VALUE_CONST);
					params.put("body", MAIL_BODY.replace(STATE_NAME_TAG, wfStatusEntity.getCurrentStatus())
							.replace(FIELD_KEY_TAG, toValue.entrySet().iterator().next().getKey()).replace(TO_VALUE_TAG, (String) toValue.entrySet().iterator().next().getValue()));
				}
				if (StringUtils.isNotBlank(wfRequest.getComment())) {
					String body = (String) params.get("body");
					body = body.substring(0, body.length() - 1);
					body = body + DUE_TO_PREFIX + wfRequest.getComment() + BOLD_CLOSE_SUFFIX;
					params.put("body", body);
				}
				params.put("orgImageUrl", null);
				template.setParams(params);
				Config config = new Config();
				config.setSubject(MAIL_SUBJECT.replace(STATE_NAME_TAG, wfStatusEntity.getCurrentStatus()));
				config.setSender(configuration.getSenderMail());
				Map<String, Object> req = new HashMap<>();
				request.setTemplate(template);
				request.setConfig(config);
				Map<String, List<NotificationRequest>> notificationMap = new HashMap<>();
				notificationMap.put(Constants.NOTIFICATIONS, Arrays.asList(request));
				req.put(Constants.REQUEST, notificationMap);
				sendNotification(req);
			} else {
				logger.warn("Email address not found in the update field values.");
			}
		}
	}

	public void sendNotificationToMdoAdmin(WfRequest wfRequest) throws IOException {
		WfStatusEntity wfStatusEntity = wfStatusRepo.findByApplicationIdAndWfId(wfRequest.getApplicationId(),
				wfRequest.getWfId());
		String fieldKey = null;
		List<HashMap<String, Object>> values =  wfRequest.getUpdateFieldValues();
		for(Map<String, Object> fieldMap : values){
			if(!fieldMap.containsKey(Constants.TO_VALUE))
				continue;
			Map<String, Object> toValueMap= (Map<String, Object>) fieldMap.get(Constants.TO_VALUE);
			Optional<Map.Entry<String, Object>> firstEntry = toValueMap.entrySet().stream().findFirst();
			if (firstEntry.isPresent()) {
				fieldKey = firstEntry.get().getKey();
			}
		}
		if(Constants.APPROVE_STATE.equalsIgnoreCase(wfRequest.getAction()) && Constants.NAME.equalsIgnoreCase(fieldKey)){
			this.sendMailToMDOForOrgChange(wfRequest);
		} else{
			WfStatus wfStatus = workflowservice.getWorkflowStates(wfStatusEntity.getRootOrg(), wfStatusEntity.getOrg(),
					wfStatusEntity.getServiceName(), wfStatusEntity.getCurrentStatus());
			if (Boolean.TRUE.equals(wfStatus.getNotificationEnable())
					&& !Arrays.asList(Constants.REJECTED, Constants.APPROVED).contains(wfStatus.getState())) {
				logger.info("Enter in the notification block");
				List<String> mdoAdminList = userProfileWfService.getMdoAdminAndPCDetails(wfRequest.getRootOrgId(), Collections.singletonList(Constants.MDO_ADMIN));
				Map<String, Object> params = new HashMap<>();
				NotificationRequest request = new NotificationRequest();
				request.setDeliveryType(Constants.MESSAGE);
				List<String> mdoMailList = mdoAdminList.stream().toList();
				if (!CollectionUtils.isEmpty(mdoMailList)) {
					request.setIds(mdoMailList);
					request.setMode(Constants.EMAIL);
					Template template = new Template();
					template.setId(configuration.getMdoEmailTemplate());
					HashMap<String, Object> usersObj = userProfileWfService.getUsersResult(Collections.singleton(wfRequest.getUserId()));
					Map<String, Object> recipientInfo = (Map<String, Object>) usersObj.get(wfStatusEntity.getUserId());
					params.put(Constants.USER_NAME, recipientInfo.get(Constants.FIRST_NAME));
					Optional<HashMap<String, Object>> updatedFieldValue = wfRequest.getUpdateFieldValues().stream().findFirst();
					if (updatedFieldValue.isPresent()) {
						HashMap<String, Object> toValue = (HashMap<String, Object>) updatedFieldValue.get().get(TO_VALUE_CONST);
						List<String> fieldNames = toValue.keySet().stream().toList();
						String approvalUrl = configuration.getDomainHost() + configuration.getMdoBaseUrl().replace("{id}", wfRequest.getApplicationId());
						params.put(Constants.LINK, approvalUrl);
						params.put(Constants.FIELDS, fieldNames);
						params.put(Constants.SUPPORT_EMAIL, configuration.getSenderMail());
					}
					String constructedEmailTemplate = constructEmailTemplate(configuration.getMdoEmailTemplate(), params);
					if (StringUtils.isNotEmpty(constructedEmailTemplate)) {
						template.setData(constructedEmailTemplate);
					}
					template.setParams(params);
					Config config = new Config();
					config.setSubject(MDO_MAIL_SUBJECT);
					config.setSender(configuration.getSenderMail());
					Map<String, Object> req = new HashMap<>();
					request.setTemplate(template);
					request.setConfig(config);
					Map<String, List<NotificationRequest>> notificationMap = new HashMap<>();
					notificationMap.put(Constants.NOTIFICATIONS, Arrays.asList(request));
					req.put(Constants.REQUEST, notificationMap);
					sendNotification(req);
				}
			}
		}
	}

	public void sendNotificationToMdoAdminAndPC(WfRequest wfRequest) {
		WfStatusEntity wfStatusEntity = wfStatusRepo.findByApplicationIdAndWfId(wfRequest.getApplicationId(),
				wfRequest.getWfId());
		WfStatus wfStatus = workflowservice.getWorkflowStates(wfStatusEntity.getRootOrg(), wfStatusEntity.getOrg(),
				wfStatusEntity.getServiceName(), wfStatusEntity.getCurrentStatus());

		HashMap<String, Object> usersObj = userProfileWfService.getUsersResult(Collections.singleton(wfRequest.getUserId()));
		Map<String, Object> recipientInfo = (Map<String, Object>) usersObj.get(wfStatusEntity.getUserId());
		String userName = (String) recipientInfo.get(Constants.FIRST_NAME);
		String userRootOrgId = (String) recipientInfo.get(Constants.ROOT_ORG_ID);

		String rootOrgId = contentReadService.getRootOrgId(wfRequest.getCourseId());

		Map<String, Object> mailNotificationDetails = new HashMap<>();
		String subjectLine = "";
		String body = "";
		String enrolmentStateSubject = "Enrolment #state";
		String approvalRequestSubject = "Enrollment Approval Request";
		String requestForwardedSubject = BP_MDO_PC_SUBJECT_LINE;
		String date = wfRequest.getBatchStartDate().toString();
		String nominationRequestMailBody = configuration.getNominationRequestMailBody().replace(USERNAMAE_TAG, userName)
				.replace(BATCH_NAME_TAG, wfRequest.getBatchName())
				.replace(BLENDED_PROGRAME_NAME_TAG, wfRequest.getCourseName());
		nominationRequestMailBody = nominationRequestMailBody.replace(BATCH_START_DATE_TAG, wfRequest.getBatchStartDate().toString());
		String approvalRequestMailBody = configuration.getApprovalRequetMailBody().replace(USERNAMAE_TAG, userName)
				.replace(BATCH_NAME_TAG, wfRequest.getBatchName())
				.replace(BLENDED_PROGRAME_NAME_TAG, wfRequest.getCourseName());
		approvalRequestMailBody = approvalRequestMailBody.replace(BATCH_START_DATE_TAG, date);
		String requestForwardedMailBody = configuration.getRequestForwardedMailBody().replace(USERNAMAE_TAG, userName)
				.replace(BATCH_NAME_TAG, wfRequest.getBatchName())
				.replace(BLENDED_PROGRAME_NAME_TAG, wfRequest.getCourseName())
				.replace(BATCH_START_DATE_TAG, wfRequest.getBatchStartDate().toString());
		mailNotificationDetails.put(Constants.SUBJECT, approvalRequestSubject);
		mailNotificationDetails.put("body", approvalRequestMailBody);

		if (Boolean.TRUE.equals(wfStatus.getNotificationEnable())
				&& !Arrays.asList(Constants.REJECTED, Constants.APPROVED).contains(wfStatus.getState())) {
			logger.info("Enter in the sendNotificationToMdoAdminAndPC block");
			resolveMdoAdminAndPcNotification(wfRequest, wfStatusEntity, rootOrgId, userRootOrgId,
					mailNotificationDetails, requestForwardedSubject, requestForwardedMailBody);
			sendNotificationEmail(mailNotificationDetails);
		} else {
			if (Constants.SEND_FOR_PC_APPROVAL.equalsIgnoreCase(wfRequest.getState()) && (null != wfStatusEntity.getAdditionalProperties() && wfStatusEntity.getAdditionalProperties().contains("isNominatedByMdo:true"))) {
				List<String> mdoEmailList = userProfileWfService.getMdoAdminAndPCDetails(userRootOrgId, Collections.singletonList(Constants.MDO_ADMIN));
				subjectLine = enrolmentStateSubject.replace(STATE_NAME_TAG, wfStatusEntity.getCurrentStatus());
				body = nominationRequestMailBody.replace(STATE_NAME_TAG, wfStatusEntity.getCurrentStatus());
				if (StringUtils.isNotBlank(wfRequest.getComment())) {
					body = body.substring(0, body.length() - 1) + DUE_TO_PREFIX + wfRequest.getComment() + BOLD_CLOSE_SUFFIX;
				}
				mailNotificationDetails.put(Constants.EMAIL_LIST, mdoEmailList);
				mailNotificationDetails.put(Constants.SUBJECT, subjectLine);
				mailNotificationDetails.put("body", body);
				mailNotificationDetails.put(Constants.EMAIL_TO, Constants.TO_MDO_ADMIN);
				sendNotificationEmail(mailNotificationDetails);
				logger.info("current role to send notification ");
			}
		}
	}

	private void resolveMdoAdminAndPcNotification(WfRequest wfRequest, WfStatusEntity wfStatusEntity,
			String rootOrgId, String userRootOrgId, Map<String, Object> mailNotificationDetails,
			String requestForwardedSubject, String requestForwardedMailBody) {
		if (Constants.INITIATE.equalsIgnoreCase(wfRequest.getState()) && Constants.SEND_FOR_PC_APPROVAL.equalsIgnoreCase(wfStatusEntity.getCurrentStatus())) {
			List<String> pcEmailList = userProfileWfService.getMdoAdminAndPCDetails(rootOrgId, Collections.singletonList(Constants.PROGRAM_COORDINATOR));
			mailNotificationDetails.put(Constants.EMAIL_LIST, pcEmailList);
			mailNotificationDetails.put(Constants.EMAIL_TO, Constants.TO_PROGRAMME_COORDINATOR);
			return;
		}
		if (Constants.INITIATE.equalsIgnoreCase(wfRequest.getState()) && Constants.SEND_FOR_MDO_APPROVAL.equalsIgnoreCase(wfStatusEntity.getCurrentStatus())) {
			List<String> mdoEmailList = userProfileWfService.getMdoAdminAndPCDetails(userRootOrgId, Collections.singletonList(Constants.MDO_ADMIN));
			mailNotificationDetails.put(Constants.EMAIL_LIST, mdoEmailList);
			mailNotificationDetails.put(Constants.EMAIL_TO, Constants.TO_MDO_ADMIN);
			return;
		}
		List<String> pcEmailList = userProfileWfService.getMdoAdminAndPCDetails(rootOrgId, Collections.singletonList(Constants.PROGRAM_COORDINATOR));
		List<String> mdoEmailList = userProfileWfService.getMdoAdminAndPCDetails(userRootOrgId, Collections.singletonList(Constants.MDO_ADMIN));
		if (Constants.SEND_FOR_MDO_APPROVAL.equalsIgnoreCase(wfRequest.getState()) && Constants.SEND_FOR_PC_APPROVAL.equalsIgnoreCase(wfStatusEntity.getCurrentStatus())) {
			mailNotificationDetails.put(Constants.EMAIL_LIST, pcEmailList);
			mailNotificationDetails.put(Constants.EMAIL_TO, Constants.TO_PROGRAMME_COORDINATOR);
			sendNotificationEmail(mailNotificationDetails);

			String subjectLine = requestForwardedSubject.replace(ROLE_TAG, Constants.PROGRAM_COORDINATOR.replace("_", " "));
			String body = requestForwardedMailBody.replace(ROLE_TAG, Constants.PROGRAM_COORDINATOR.replace("_", " "));
			mailNotificationDetails.put(Constants.SUBJECT, subjectLine);
			mailNotificationDetails.put("body", body);
			mailNotificationDetails.put(Constants.EMAIL_LIST, mdoEmailList);
			mailNotificationDetails.put(Constants.EMAIL_TO, Constants.TO_MDO_ADMIN);
			return;
		}
		if (Constants.SEND_FOR_PC_APPROVAL.equalsIgnoreCase(wfRequest.getState()) && Constants.SEND_FOR_MDO_APPROVAL.equalsIgnoreCase(wfStatusEntity.getCurrentStatus())) {
			mailNotificationDetails.put(Constants.EMAIL_LIST, mdoEmailList);
			mailNotificationDetails.put(Constants.EMAIL_TO, Constants.TO_MDO_ADMIN);
			sendNotificationEmail(mailNotificationDetails);

			String subjectLine = requestForwardedSubject.replace(ROLE_TAG, Constants.MDO_ADMIN.split("_")[0]);
			String body = requestForwardedMailBody.replace(ROLE_TAG, Constants.MDO_ADMIN.split("_")[0]);
			mailNotificationDetails.put(Constants.SUBJECT, subjectLine);
			mailNotificationDetails.put("body", body);
			mailNotificationDetails.put(Constants.EMAIL_LIST, pcEmailList);
			mailNotificationDetails.put(Constants.EMAIL_TO, Constants.TO_PROGRAMME_COORDINATOR);
		}
	}

	private String constructEmailTemplate(String templateName, Map<String, Object> params) {
		String replacedHTML = "";
		try {
			Map<String, Object> propertyMap = new HashMap<>();
			propertyMap.put(Constants.NAME, templateName);
			List<Map<String, Object>> templateMap = cassandraOperation.getRecordsByProperties(Constants.KEYSPACE_SUNBIRD, Constants.TABLE_EMAIL_TEMPLATE, propertyMap, Collections.singletonList(Constants.TEMPLATE));
			String htmlTemplate = templateMap.stream()
					.findFirst()
					.map(template -> (String) template.get(Constants.TEMPLATE))
					.orElse(null);
			VelocityEngine velocityEngine = new VelocityEngine();
			velocityEngine.init();
			VelocityContext context = new VelocityContext();
			for (Map.Entry<String, Object> entry : params.entrySet()) {
				context.put(entry.getKey(), entry.getValue());
			}
			StringWriter writer = new StringWriter();
			velocityEngine.evaluate(context, writer, "HTMLTemplate", htmlTemplate);
			replacedHTML = writer.toString();
		} catch (Exception e) {
			logger.error("Unable to create template", e);
		}
		return replacedHTML;
	}

		/**
         * Post to the Notification service
         * @param request
         */
	public void sendNotification(Map<String, Object> request) {
		StringBuilder builder = new StringBuilder();
		builder.append(configuration.getNotifyServiceHost()).append(configuration.getNotifyServicePath());
		try {
			Object response = requestService.fetchResultUsingPost(builder, request, Map.class, null);
			logger.info("Notification sent successfully, response is: {}", response);
		} catch (Exception e) {
			logger.error("Exception while posting the data in notification service: ", e);
		}
	}

	private void sendNotificationEmail(Map<String, Object> mailNotificationDetails){
		logger.info("Mail Notification Details: {}", mailNotificationDetails);
		Map<String, Object> params = new HashMap<>();
		NotificationRequest request = new NotificationRequest();
		List<String> emailList = (List<String>) mailNotificationDetails.get(Constants.EMAIL_LIST);
		request.setDeliveryType(Constants.MESSAGE);
		if (!CollectionUtils.isEmpty(emailList)) {
			request.setIds(emailList);
			request.setMode(Constants.EMAIL);
			Template template = new Template();
			template.setId(configuration.getNotificationEmailTemplate());
			params.put(Constants.NAME, mailNotificationDetails.get(Constants.EMAIL_TO));
			params.put("body", mailNotificationDetails.get("body"));
			params.put(Constants.ORG_NAME, Constants.KARMYOGI_BHARAT);
			params.put(Constants.FROM_EMAIL, configuration.getSenderMail());
			String constructedEmailTemplate = constructEmailTemplate(configuration.getNotificationEmailTemplate(), params);
			if (StringUtils.isNotEmpty(constructedEmailTemplate)) {
				template.setData(constructedEmailTemplate);
			}
			template.setParams(params);
			Config config = new Config();
			config.setSubject((String) mailNotificationDetails.get(Constants.SUBJECT));
			config.setSender(configuration.getSenderMail());
			Map<String, Object> req = new HashMap<>();
			request.setTemplate(template);
			request.setConfig(config);
			Map<String, List<NotificationRequest>> notificationMap = new HashMap<>();
			notificationMap.put(Constants.NOTIFICATIONS, Arrays.asList(request));
			req.put(Constants.REQUEST, notificationMap);
			sendNotification(req);
		}
	}

	public void sendMailToMDOForOrgChange(WfRequest wfRequest){
		List<String> mdoAdminList = userProfileWfService.getMdoAdminAndPCDetails(wfRequest.getPreviousRootOrgId(), Collections.singletonList(Constants.MDO_ADMIN));
		Map<String, Object> params = new HashMap<>();
		NotificationRequest request = new NotificationRequest();
		request.setDeliveryType(Constants.MESSAGE);
		List<String> mdoMailList = mdoAdminList.stream().toList();
		if (!CollectionUtils.isEmpty(mdoMailList)) {
			request.setIds(mdoMailList);
			request.setMode(Constants.EMAIL);
			Template template = new Template();
			template.setId(configuration.getOrgChangeNotificationTemplate());
			HashMap<String, Object> usersObj = userProfileWfService.getUsersResult(Collections.singleton(wfRequest.getUserId()));
			Map<String, Object> recipientInfo = (Map<String, Object>) usersObj.get(wfRequest.getUserId());
			String username = (String) recipientInfo.get(Constants.FIRST_NAME);
			params.put(Constants.USER_NAME, username);
			params.put(Constants.SENDER , Constants.KARMYOGI_BHARAT);
			params.put(Constants.SUPPORT_EMAIL, configuration.getSenderMail());
			String constructedEmailTemplate = constructEmailTemplate(configuration.getOrgChangeNotificationTemplate(), params);
			if (StringUtils.isNotEmpty(constructedEmailTemplate)) {
				template.setData(constructedEmailTemplate);
			}
			template.setParams(params);
			Config config = new Config();
			config.setSubject(Constants.TRANSFER_SUBJECT_LINE.replace( USERNAMAE_TAG, username));
			config.setSender(configuration.getSenderMail());
			Map<String, Object> req = new HashMap<>();
			request.setTemplate(template);
			request.setConfig(config);
			Map<String, List<NotificationRequest>> notificationMap = new HashMap<>();
			notificationMap.put(Constants.NOTIFICATIONS, Arrays.asList(request));
			req.put(Constants.REQUEST, notificationMap);
			sendNotification(req);
		}
	}

	public void sendNotificationToMdoLeader(List<String> mdoMails, WfRequest wfRequest, String communityName, String creationDate, List<Map<String, Object>> moderators) {
		try {
			String moderatorDetails = moderators.stream()
					.map(mod -> {
						String name = (String) mod.getOrDefault(Constants.MODERATOR_NAME, "");
						return name;
					})
					.filter(StringUtils::isNotBlank)
					.distinct()
					.collect(Collectors.joining(", "));

			Map<String, Object> params = new HashMap<>();
			params.put(Constants.COMMUNITY_NAME, communityName);
			params.put(Constants.CREATION_DATE, creationDate);
			params.put(Constants.MODERATOR_NAMES, moderatorDetails);
			params.put(Constants.SENDER, Constants.KARMYOGI_BHARAT);
			params.put(Constants.SUPPORT_EMAIL, configuration.getSenderMail());

			Template template = new Template();
			template.setId(configuration.getCommunityModeratorTransferTemplate());

			String emailBody = constructEmailTemplate(template.getId(), params);
			template.setData(emailBody);
			template.setParams(params);

			NotificationRequest notificationRequest = new NotificationRequest();
			notificationRequest.setMode(Constants.EMAIL);
			notificationRequest.setDeliveryType(Constants.MESSAGE);
			notificationRequest.setIds(mdoMails);
			notificationRequest.setTemplate(template);

			Config config = new Config();
			config.setSubject(String.format(Constants.MODERATOR_TRANSFER_SUBJECT_TEMPLATE, communityName));
			config.setSender(configuration.getSenderMail());
			notificationRequest.setConfig(config);

			Map<String, Object> wrapper = new HashMap<>();
			wrapper.put(Constants.REQUEST, Collections.singletonMap(Constants.NOTIFICATIONS, Arrays.asList(notificationRequest)));

			sendNotification(wrapper);

			logger.info("Email sent to MDO admins: {}", mdoMails);
		} catch (Exception e) {
			logger.error("Error sending notification to MDO admins", e);
		}
	}

	public void sendAiAssessmentNotification(WfRequest wfRequest) {
		WfStatusEntity wfStatusEntity = wfStatusRepo
				.findByApplicationIdAndWfId(
						wfRequest.getApplicationId(),
						wfRequest.getWfId());

		String currentStatus = wfStatusEntity.getCurrentStatus();
		logger.info("AI Assessment notification for status: {} userId: {}",
				currentStatus, wfRequest.getUserId());

		if (Constants.PENDING.equalsIgnoreCase(currentStatus)) {
			sendNotificationToSPV(wfRequest, wfStatusEntity);

		} else if (Constants.APPROVED.equalsIgnoreCase(currentStatus)
				|| Constants.REJECTED.equalsIgnoreCase(currentStatus)) {
			sendNotificationToAiAssessmentUser(wfRequest, wfStatusEntity);
		}
	}

	private void sendNotificationToSPV(
			WfRequest wfRequest, WfStatusEntity wfStatusEntity) {

		List<String> spvEmailList = configuration.getAiAssessmentSpvAdminEmail();
		if (CollectionUtils.isEmpty(spvEmailList)) {
			logger.warn("No SPV emails found for rootOrgId: {}",
					wfRequest.getRootOrgId());
			return;
		}

		HashMap<String, Object> usersObj = userProfileWfService
				.getUsersResult(Collections.singleton(wfRequest.getUserId()));
		Map<String, Object> userInfo = (Map<String, Object>) usersObj
				.get(wfRequest.getUserId());

		String userName = (String) userInfo.get(Constants.FIRST_NAME);
		String organization = StringUtils.isNotBlank(wfStatusEntity.getDeptName())
				? wfStatusEntity.getDeptName()
				: (String) userInfo.get(DEPARTMENT_NAME);
		String submittedOn = formatDate(wfStatusEntity.getCreatedOn());
		String approvalLink = configuration.getCbpPortalHost()
				+ configuration.getAiAssessmentApprovalLink();

		String body = configuration.getAiAssessmentSpvEmailBody()
				.replace(PLACEHOLDER_USER_NAME, userName)
				.replace(PLACEHOLDER_ORGANIZATION, organization)
				.replace(PLACEHOLDER_SUBMITTED_ON, submittedOn)
				.replace(PLACEHOLDER_APPROVAL_LINK, approvalLink)
				.replace(PLACEHOLDER_APPLICATION_NAME, KARMYOGI_BHARAT.toString());

		Map<String, Object> mailNotificationDetails = new HashMap<>();
		mailNotificationDetails.put(EMAIL_LIST, spvEmailList);
		mailNotificationDetails.put(EMAIL_TO, SPVPUBLISHER);
		mailNotificationDetails.put(SUBJECT, configuration.getAiAssessmentSpvEmailSubject());
		mailNotificationDetails.put(BODY, body);

		sendNotificationEmail(mailNotificationDetails);
		logger.info("SPV notified for AI Assessment PENDING userId: {}",
				wfRequest.getUserId());
	}


	private void sendNotificationToAiAssessmentUser(
			WfRequest wfRequest, WfStatusEntity wfStatusEntity) {

		HashMap<String, Object> usersObj = userProfileWfService
				.getUsersResult(Collections.singleton(wfRequest.getUserId()));
		Map<String, Object> userInfo = (Map<String, Object>) usersObj
				.get(wfRequest.getUserId());
		String userName = (String) userInfo.get(Constants.FIRST_NAME);

		HashMap<String, Object> approverObj = userProfileWfService
				.getUsersResult(Collections.singleton(wfRequest.getActorUserId()));
		Map<String, Object> approverInfo = (Map<String, Object>) approverObj
				.get(wfRequest.getActorUserId());
		String approverName = approverInfo != null
				? (String) approverInfo.get(Constants.FIRST_NAME) : SPVPUBLISHER;
		String actionDate = formatDate(wfStatusEntity.getLastUpdatedOn());

		String currentStatus = wfStatusEntity.getCurrentStatus();
		String subject;
		String body;

		if (Constants.APPROVED.equalsIgnoreCase(currentStatus)) {
			subject = configuration.getAiAssessmentApprovedEmailSubject();
			body = configuration.getAiAssessmentApprovedEmailBody()
					.replace(PLACEHOLDER_USER_NAME, userName)
					.replace(PLACEHOLDER_APPROVER_NAME, approverName)
					.replace(PLACEHOLDER_ACTION_DATE, actionDate)
					.replace(PLACEHOLDER_APPLICATION_NAME, KARMYOGI_BHARAT.toString());
		} else {
			String rejectionReason = StringUtils.isNotBlank(wfRequest.getComment())
					? wfRequest.getComment() : NOT_SPECIFIED;
			subject = configuration.getAiAssessmentRejectedEmailSubject();
			body = configuration.getAiAssessmentRejectedEmailBody()
					.replace(PLACEHOLDER_USER_NAME, userName)
					.replace(PLACEHOLDER_APPROVER_NAME, approverName)
					.replace(PLACEHOLDER_ACTION_DATE, actionDate)
					.replace(PLACEHOLDER_REJECTION_REASON, rejectionReason)
					.replace(PLACEHOLDER_APPLICATION_NAME, KARMYOGI_BHARAT.toString());
		}

		Map<String, Object> mailNotificationDetails = new HashMap<>();
		mailNotificationDetails.put(EMAIL_LIST,
				Collections.singletonList(userInfo.get(Constants.EMAIL)));
		mailNotificationDetails.put(EMAIL_TO, userName);
		mailNotificationDetails.put(SUBJECT, subject);
		mailNotificationDetails.put(BODY, body);

		sendNotificationEmail(mailNotificationDetails);
		logger.info("User notified for AI Assessment {} userId: {}",
				currentStatus, wfRequest.getUserId());
	}

	private String formatDate(Date date) {
		return ObjectUtils.isEmpty(date)
				? NOT_SPECIFIED
				: date.toInstant()
				  .atZone(IST_ZONE_ID)
				  .format(DATE_FORMATTER);
	}
}
