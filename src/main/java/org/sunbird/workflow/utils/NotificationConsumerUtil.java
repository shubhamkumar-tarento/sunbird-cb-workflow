package org.sunbird.workflow.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NotificationConsumerUtil {

    private NotificationConsumerUtil() {
    }

    public static Map<String, Object> getCourseAttributes(CassandraOperation cassandraOperation, Configuration configuration, String courseId) {
        Map<String, Object> propertiesMap = new HashMap<>();
        Map<String, Object> courseDetails = new HashMap<>();
        propertiesMap.put(Constants.IDENTIFIER, courseId);
        List<Map<String, Object>> coursesDataList = cassandraOperation.getRecordsByProperties(configuration.getEnvHierarchyKeystoreKeyspaceName(),
                Constants.CONTENT_HIERARCHY,
                propertiesMap,
                Arrays.asList(Constants.IDENTIFIER, Constants.HIERARCHY));
        Map<String, Object> hierarchy = new Gson().fromJson((String) coursesDataList.get(0).get("hierarchy"), new TypeToken<HashMap<String, Object>>() {
        }.getType());
        courseDetails.put(Constants.COURSE_NAME, hierarchy.get(Constants.NAME));
        return courseDetails;
    }
}
