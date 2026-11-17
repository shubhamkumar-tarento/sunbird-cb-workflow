package org.sunbird.workflow.utils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.api.querybuilder.term.Term;
import com.datastax.oss.driver.api.querybuilder.update.Assignment;
import com.datastax.oss.driver.api.querybuilder.update.Update;
import com.datastax.oss.driver.api.querybuilder.update.UpdateStart;
import com.datastax.oss.driver.api.querybuilder.update.UpdateWithAssignments;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;

@Component
public class CassandraOperationImpl implements CassandraOperation {

    private Logger logger = LoggerFactory.getLogger(getClass().getName());
    protected CassandraConnectionManager connectionManager = CassandraConnectionMngrFactory.getInstance();

    @Override
    public List<Map<String, Object>> getRecordsByProperties(String keyspaceName, String tableName,
                                                            Map<String, Object> propertyMap, List<String> fields) {
        List<Map<String, Object>> response = new ArrayList<>();
        CqlSession session = null;
        try {
            session = connectionManager.getSession(keyspaceName);
            Select selectQuery = processQuery(keyspaceName, tableName, propertyMap, fields);
            ResultSet results = session.execute(selectQuery.build());
            response = CassandraUtil.createResponse(results);
        } catch (Exception e) {
            logger.error(Constants.EXCEPTION_MSG_FETCH + tableName + " : " + e.getMessage(), e);
        }
        return response;
    }

    @Override
    public int getCountByProperties(String keyspaceName, String tableName, Map<String, Object> propertyMap) {
        int count = 0;
        CqlSession session = null;
        try {
            session = connectionManager.getSession(keyspaceName);
            Select selectQuery = selectFrom(keyspaceName, tableName).countAll().where();
            propertyMap.forEach((key, value) -> selectQuery.whereColumn(key).isEqualTo(bindMarker()));
            PreparedStatement preparedStatement = session.prepare(selectQuery.build());
            BoundStatement boundStatement = preparedStatement.bind(propertyMap.values().toArray());
            ResultSet resultSet = session.execute(boundStatement);
            Row row = resultSet.one();
            if (row != null) {
                count = (int) row.getLong(0);
            }
        } catch (Exception e) {
            logger.error(Constants.EXCEPTION_MSG_FETCH + tableName + " : " + e.getMessage(), e);
        }
        return count;
    }

    private Select processQuery(String keyspaceName, String tableName, Map<String, Object> propertyMap,
                                List<String> fields) {

        // Check if we have multiple IN clauses that might cause the error
        boolean hasMultipleListValues = propertyMap.values().stream()
                .filter(v -> v instanceof List && ((List<?>) v).size() > 1)
                .count() > 1;

        // If we have multiple IN clauses, we need to handle it differently
        if (hasMultipleListValues) {
            return processQueryForMultipleInClauses(keyspaceName, tableName, propertyMap, fields);
        }

        Select selectQuery = buildBaseSelect(keyspaceName, tableName, fields);
        if (MapUtils.isNotEmpty(propertyMap)) {
            for (Entry<String, Object> entry : propertyMap.entrySet()) {
                selectQuery = applyPropertyCondition(selectQuery, entry);
            }
            selectQuery = selectQuery.allowFiltering();
        }

        return selectQuery;
    }

    /**
     * Handle the case where we have multiple IN clauses that might cause the error:
     * "Cannot restrict clustering columns by IN relations when a collection is selected by the query"
     */
    private Select processQueryForMultipleInClauses(String keyspaceName, String tableName,
                                                    Map<String, Object> propertyMap, List<String> fields) {

        // Find the first list property to use as the primary IN clause
        Map.Entry<String, Object> primaryListEntry = propertyMap.entrySet().stream()
                .filter(e -> e.getValue() instanceof List && ((List<?>) e.getValue()).size() > 1)
                .findFirst()
                .orElse(null);

        // If no list found, fall back to standard processing
        if (primaryListEntry == null) {
            return processQuery(keyspaceName, tableName, propertyMap, fields);
        }

        Select selectQuery = buildBaseSelect(keyspaceName, tableName, fields);

        // Create a modified property map without the primary list property
        Map<String, Object> modifiedPropertyMap = new HashMap<>(propertyMap);
        modifiedPropertyMap.remove(primaryListEntry.getKey());

        // Add all non-list conditions or single-value lists
        for (Entry<String, Object> entry : modifiedPropertyMap.entrySet()) {
            selectQuery = applyPropertyCondition(selectQuery, entry);
        }

        // Add the primary list using IN clause
        List<?> primaryList = (List<?>) primaryListEntry.getValue();
        if (CollectionUtils.isNotEmpty(primaryList)) {
            List<Term> terms = primaryList.stream()
                    .map(QueryBuilder::literal)
                    .collect(Collectors.toList());
            selectQuery = selectQuery.whereColumn(primaryListEntry.getKey()).in(terms);
        }

        // Add filtering directive
        selectQuery = selectQuery.allowFiltering();

        return selectQuery;
    }

    private Select buildBaseSelect(String keyspaceName, String tableName, List<String> fields) {
        if (CollectionUtils.isNotEmpty(fields)) {
            return QueryBuilder.selectFrom(keyspaceName, tableName).columns(fields.toArray(new String[0]));
        }
        return QueryBuilder.selectFrom(keyspaceName, tableName).all();
    }

    private Select applyPropertyCondition(Select selectQuery, Entry<String, Object> entry) {
        if (!(entry.getValue() instanceof List)) {
            return selectQuery.whereColumn(entry.getKey())
                    .isEqualTo(QueryBuilder.literal(entry.getValue()));
        }
        List<?> list = (List<?>) entry.getValue();
        if (CollectionUtils.isEmpty(list)) {
            return selectQuery;
        }
        // If there's only one value in the list, use equals instead of IN
        if (list.size() == 1) {
            return selectQuery.whereColumn(entry.getKey())
                    .isEqualTo(QueryBuilder.literal(list.get(0)));
        }
        List<Term> terms = list.stream()
                .map(QueryBuilder::literal)
                .collect(Collectors.toList());
        return selectQuery.whereColumn(entry.getKey()).in(terms);
    }

    public Response insertRecord(String keyspaceName, String tableName, Map<String, Object> request) {
        Response response = new Response();
        CqlSession session = null;
        try {
            session = connectionManager.getSession(keyspaceName);
            String query = CassandraUtil.getPreparedStatement(keyspaceName, tableName, request);
            PreparedStatement statement = session.prepare(query);
            BoundStatement boundStatement = statement.bind(request.values().toArray());
            session.execute(boundStatement);
            response.put("STATUS", "SUCCESS");
        } catch (Exception e) {
            String errorMessage = String.format("Exception occurred while inserting record to %s %s", tableName, e.getMessage());
            logger.error(errorMessage, e);
            response.put("STATUS", "FAILED");
        }
        return response;
    }

    public Map<String, Object> updateRecord(String keyspaceName, String tableName, Map<String, Object> updateAttributes,
                                            Map<String, Object> compositeKey) {
        Map<String, Object> response = new HashMap<>();
        CqlSession session = null;
        try {
            session = connectionManager.getSession(keyspaceName);
            UpdateStart updateStart = QueryBuilder.update(keyspaceName, tableName);
            UpdateWithAssignments updateWithAssignments = updateStart.set(updateAttributes.entrySet().stream()
                    .map(entry -> Assignment.setColumn(entry.getKey(), QueryBuilder.literal(entry.getValue())))
                    .toArray(Assignment[]::new));
            Update update = updateWithAssignments.where(compositeKey.entrySet().stream()
                    .map(entry -> Relation.column(entry.getKey()).isEqualTo(QueryBuilder.literal(entry.getValue())))
                    .toArray(Relation[]::new));
            SimpleStatement statement = update.build();
            session.execute(statement);
            response.put(Constants.RESPONSE, Constants.SUCCESS);
        } catch (Exception e) {
            String errMsg = String.format("Exception occurred while updating record to %s: %s", tableName, e.getMessage());
            logger.error(errMsg, e);
            response.put(Constants.RESPONSE, Constants.FAILED);
            response.put(Constants.ERROR_MESSAGE, errMsg);
            throw e;
        }
        return response;
    }

    @Override
    public Map<String, Object> getRecordsByProperties(String keyspaceName, String tableName,
                                                      Map<String, Object> propertyMap, List<String> fields, String key) {
        Select selectQuery = null;
        Map<String, Object> response = new HashMap<>();
        try {
            selectQuery = processQuery(keyspaceName, tableName, propertyMap, fields);
            ResultSet results = connectionManager.getSession(keyspaceName).execute(selectQuery.build());
            response = CassandraUtil.createResponse(results, key);

        } catch (Exception e) {
            logger.error(Constants.EXCEPTION_MSG_FETCH + tableName + " : " + e.getMessage(), e);
        }
        return response;
    }
}
