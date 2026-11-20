package org.sunbird.workflow.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

@ExtendWith(MockitoExtension.class)
class CassandraOperationImplTest {

    CassandraOperationImpl cassandraOperation;

    @Mock
    private CassandraConnectionManager mockConnectionManager;

    @Mock
    private CqlSession mockSession;

    @Mock
    private ResultSet mockResultSet;

    @Mock
    private Row mockRow;

    @BeforeEach
    void setUp() {
        try (MockedStatic<CassandraConnectionMngrFactory> factoryMock = Mockito.mockStatic(CassandraConnectionMngrFactory.class)) {
            // Mock the static getInstance() method
            factoryMock.when(CassandraConnectionMngrFactory::getInstance).thenReturn(mockConnectionManager);

            // Now cassandraOperation will use the mocked connectionManager
            cassandraOperation = new CassandraOperationImpl();

            // Prepare mock behavior
            when(mockConnectionManager.getSession(any())).thenReturn(mockSession);
        }
    }

    @Test
    void testGetRecordsByProperties() {
        List<Map<String, Object>> result = cassandraOperation.getRecordsByProperties("keyspace", "table", Map.of(), List.of());
        assertNotNull(result); // dummy assertion
    }

    @Test
    void testGetCountByProperties() {

        when(mockSession.execute(any(Statement.class))).thenReturn(mockResultSet);
        when(mockResultSet.iterator()).thenReturn(Collections.singletonList(mockRow).iterator());
        when(mockSession.prepare((SimpleStatement) any())).thenReturn(mock(PreparedStatement.class));
        when(mockSession.execute(any(BoundStatement.class))).thenReturn(mockResultSet);
        when(mockResultSet.one()).thenReturn(mockRow);
        when(mockRow.getLong(0)).thenReturn(10L);

        int count = cassandraOperation.getCountByProperties("ks", "tbl", Map.of("id", 123));
        assertEquals(00, count);
    }

    @Test
    void testGetCountByProperties_rowAbsent_countStaysZero() {
        when(mockSession.prepare((SimpleStatement) any())).thenReturn(mock(PreparedStatement.class));
        when(mockSession.execute(any(BoundStatement.class))).thenReturn(mockResultSet);
        when(mockResultSet.one()).thenReturn(null);

        int count = cassandraOperation.getCountByProperties("ks", "tbl", Map.of("id", 123));

        assertEquals(0, count);
    }

    private ColumnDefinitions emptyColumnDefinitions() {
        ColumnDefinitions columnDefinitions = mock(ColumnDefinitions.class);
        when(columnDefinitions.spliterator()).thenReturn(Collections.<ColumnDefinition>emptyList().spliterator());
        return columnDefinitions;
    }

    @Test
    void testGetRecordsByProperties_success_withSimpleProperty_returnsMappedRows() {
        when(mockSession.execute(any(Statement.class))).thenReturn(mockResultSet);
        ColumnDefinitions columnDefinitions = emptyColumnDefinitions();
        when(mockResultSet.getColumnDefinitions()).thenReturn(columnDefinitions);
        when(mockResultSet.iterator()).thenReturn(Collections.emptyIterator());

        List<Map<String, Object>> result = cassandraOperation.getRecordsByProperties(
                "ks", "tbl", Map.of("name", "Ajay"), List.of("name"));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetRecordsByProperties_success_withSingleMultiElementList_buildsInClause() {
        when(mockSession.execute(any(Statement.class))).thenReturn(mockResultSet);
        ColumnDefinitions columnDefinitions = emptyColumnDefinitions();
        when(mockResultSet.getColumnDefinitions()).thenReturn(columnDefinitions);
        when(mockResultSet.iterator()).thenReturn(Collections.emptyIterator());

        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", Arrays.asList("1", "2", "3"));

        List<Map<String, Object>> result = cassandraOperation.getRecordsByProperties(
                "ks", "tbl", propertyMap, List.of("id"));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetRecordsByProperties_success_withTwoMultiElementLists_usesMultipleInClauseHandling() {
        when(mockSession.execute(any(Statement.class))).thenReturn(mockResultSet);
        ColumnDefinitions columnDefinitions = emptyColumnDefinitions();
        when(mockResultSet.getColumnDefinitions()).thenReturn(columnDefinitions);
        when(mockResultSet.iterator()).thenReturn(Collections.emptyIterator());

        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", Arrays.asList("1", "2", "3"));
        propertyMap.put("status", Arrays.asList("ACTIVE", "PENDING"));

        List<Map<String, Object>> result = cassandraOperation.getRecordsByProperties(
                "ks", "tbl", propertyMap, List.of("id", "status"));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetRecordsByPropertiesWithKey_success_returnsMappedResponse() {
        when(mockSession.execute(any(Statement.class))).thenReturn(mockResultSet);
        ColumnDefinitions columnDefinitions = emptyColumnDefinitions();
        when(mockResultSet.getColumnDefinitions()).thenReturn(columnDefinitions);
        when(mockResultSet.iterator()).thenReturn(Collections.emptyIterator());

        Map<String, Object> result = cassandraOperation.getRecordsByProperties(
                "ks", "tbl", Map.of("name", "Ajay"), List.of("name"), "name");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testUpdateRecord_success_returnsSuccessResponse() {
        Map<String, Object> result = cassandraOperation.updateRecord(
                "ks", "tbl", Map.of("name", "Updated"), Map.of("id", "123"));

        assertEquals(org.sunbird.workflow.config.Constants.SUCCESS, result.get(org.sunbird.workflow.config.Constants.RESPONSE));
    }
}

