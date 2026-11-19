package org.sunbird.workflow.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.utils.CassandraOperation;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowRedisCacheMgrTest {

    @InjectMocks
    private WorkflowRedisCacheMgr cacheMgr;

    @Mock
    private JedisPool jedisPool;

    @Mock
    private WfStatusRepo wfStatusRepo;

    @Mock
    private Configuration configuration;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private Jedis jedis;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(jedisPool.getResource()).thenReturn(jedis);
    }

    @Test
    void testPut_success() {
        cacheMgr.put("key1", "value1", 100, 2);

        verify(jedis).select(2);
        verify(jedis).set("key1", "value1");
        verify(jedis).expire("key1", 100);
    }

    @Test
    void testPut_exception() {
        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis down"));
        assertDoesNotThrow(() -> cacheMgr.put("key1", "value1", 100, 2));
    }

    @Test
    void testGet_success() {
        when(jedis.get("key1")).thenReturn("cachedValue");

        String result = cacheMgr.get("key1", 1);

        assertEquals("cachedValue", result);
        verify(jedis).select(1);
    }

    @Test
    void testGet_exception() {
        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis down"));
        assertNull(cacheMgr.get("key1", 1));
    }

    @Test
    void testIncrementBatchFieldCount_cacheHit() {
        when(configuration.getBpBatchStatsCacheIndex()).thenReturn(3);
        when(jedis.exists(anyString())).thenReturn(true);

        cacheMgr.incrementBatchFieldCount("batch1", "pending");

        verify(jedis).select(3);
        verify(jedis).hincrBy(Constants.BP_BATCH_STATS_PREFIX + "batch1", "pending", 1L);
        verify(wfStatusRepo, never()).countGroupedByStatusForApplicationId(any());
    }

    @Test
    void testIncrementBatchFieldCount_cacheMiss_initializesFromDb() {
        when(configuration.getBpBatchStatsCacheIndex()).thenReturn(3);
        when(jedis.exists(anyString())).thenReturn(false);
        when(wfStatusRepo.countGroupedByStatusForApplicationId("batch1"))
                .thenReturn(buildStatusRows());
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                eq(Constants.TABLE_ENROLMENT_BATCH_LOOKUP), anyMap(), anyList()))
                .thenReturn(buildActiveEnrollmentRows());
        when(configuration.getBpBatchStatsCacheTtl()).thenReturn(600);

        cacheMgr.incrementBatchFieldCount("batch1", "pending");

        verify(jedis).hmset(eq(Constants.BP_BATCH_STATS_PREFIX + "batch1"), anyMap());
        verify(jedis).expire(Constants.BP_BATCH_STATS_PREFIX + "batch1", 600);
        // on cache miss, the delta itself is never applied (DB snapshot already reflects it)
        verify(jedis, never()).hincrBy(anyString(), anyString(), anyLong());
    }

    @Test
    void testIncrementBatchFieldCount_exception() {
        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis down"));
        assertDoesNotThrow(() -> cacheMgr.incrementBatchFieldCount("batch1", "pending"));
    }

    @Test
    void testDecrementBatchFieldCount_cacheHit() {
        when(configuration.getBpBatchStatsCacheIndex()).thenReturn(3);
        when(jedis.exists(anyString())).thenReturn(true);

        cacheMgr.decrementBatchFieldCount("batch1", "pending");

        verify(jedis).hincrBy(Constants.BP_BATCH_STATS_PREFIX + "batch1", "pending", -1L);
    }

    @Test
    void testDecrementBatchFieldCount_cacheMiss_initializesFromDb() {
        when(configuration.getBpBatchStatsCacheIndex()).thenReturn(3);
        when(jedis.exists(anyString())).thenReturn(false);
        when(wfStatusRepo.countGroupedByStatusForApplicationId("batch1"))
                .thenReturn(buildStatusRows());
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                eq(Constants.TABLE_ENROLMENT_BATCH_LOOKUP), anyMap(), anyList()))
                .thenReturn(buildActiveEnrollmentRows());
        when(configuration.getBpBatchStatsCacheTtl()).thenReturn(600);

        cacheMgr.decrementBatchFieldCount("batch1", "pending");

        verify(jedis, never()).hincrBy(anyString(), anyString(), anyLong());
        verify(jedis).hmset(eq(Constants.BP_BATCH_STATS_PREFIX + "batch1"), anyMap());
    }

    @Test
    void testDecrementBatchFieldCount_exception() {
        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis down"));
        assertDoesNotThrow(() -> cacheMgr.decrementBatchFieldCount("batch1", "pending"));
    }

    @Test
    void testGetBatchStats_cacheHit() {
        when(configuration.getBpBatchStatsCacheIndex()).thenReturn(3);
        when(jedis.exists(anyString())).thenReturn(true);
        Map<String, String> stats = Map.of("pending", "2");
        when(jedis.hgetAll(Constants.BP_BATCH_STATS_PREFIX + "batch1")).thenReturn(stats);

        Map<String, String> result = cacheMgr.getBatchStats("batch1");

        assertEquals(stats, result);
        verify(wfStatusRepo, never()).countGroupedByStatusForApplicationId(any());
    }

    @Test
    void testGetBatchStats_cacheMiss_initializesThenReads() {
        when(configuration.getBpBatchStatsCacheIndex()).thenReturn(3);
        when(jedis.exists(anyString())).thenReturn(false);
        when(wfStatusRepo.countGroupedByStatusForApplicationId("batch1"))
                .thenReturn(buildStatusRows());
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                eq(Constants.TABLE_ENROLMENT_BATCH_LOOKUP), anyMap(), anyList()))
                .thenReturn(buildActiveEnrollmentRows());
        when(configuration.getBpBatchStatsCacheTtl()).thenReturn(600);
        Map<String, String> stats = Map.of("pending", "1");
        when(jedis.hgetAll(Constants.BP_BATCH_STATS_PREFIX + "batch1")).thenReturn(stats);

        Map<String, String> result = cacheMgr.getBatchStats("batch1");

        assertEquals(stats, result);
        verify(jedis).hmset(eq(Constants.BP_BATCH_STATS_PREFIX + "batch1"), anyMap());
    }

    @Test
    void testGetBatchStats_exception() {
        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis down"));
        assertEquals(java.util.Collections.emptyMap(), cacheMgr.getBatchStats("batch1"));
    }

    @Test
    void testBulkInitBatchStats_exception() {
        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis down"));
        assertDoesNotThrow(() -> cacheMgr.bulkInitBatchStats(List.of("batch1")));
    }

    @Test
    void testBulkInitBatchStats_allKeysLive_skipsDbWork() {
        when(configuration.getBpBatchStatsCacheIndex()).thenReturn(3);
        Pipeline existsPipeline = mock(Pipeline.class);
        when(jedis.pipelined()).thenReturn(existsPipeline);
        Response<Boolean> liveResponse = mockResponse(true);
        when(existsPipeline.exists(anyString())).thenReturn(liveResponse);

        cacheMgr.bulkInitBatchStats(List.of("batch1", "batch2"));

        verify(wfStatusRepo, never()).countGroupedByStatusForApplicationIds(any());
        verify(cassandraOperation, never()).getRecordsByProperties(any(), any(), any(), any());
    }

    @Test
    void testBulkInitBatchStats_someMissing_writesStats() {
        when(configuration.getBpBatchStatsCacheIndex()).thenReturn(3);
        when(configuration.getBpBatchStatsSyncRedisPipelineSize()).thenReturn(10);
        when(configuration.getBpBatchStatsCacheTtl()).thenReturn(600);

        Pipeline existsPipeline = mock(Pipeline.class);
        Pipeline writePipeline = mock(Pipeline.class);
        when(jedis.pipelined()).thenReturn(existsPipeline, writePipeline);

        Response<Boolean> missingResponse = mockResponse(false);
        when(existsPipeline.exists(anyString())).thenReturn(missingResponse);

        List<Object[]> pgRows = new ArrayList<>();
        pgRows.add(new Object[]{"batch1", "SEND_FOR_APPROVAL", 5L});
        pgRows.add(new Object[]{"batch1", "WITHDRAWN", 1L});
        when(wfStatusRepo.countGroupedByStatusForApplicationIds(anyList())).thenReturn(pgRows);

        List<Map<String, Object>> casRows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put(Constants.BATCH_ID_KEY, "batch1");
        row.put(Constants.ACTIVE, Boolean.TRUE);
        casRows.add(row);
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                eq(Constants.TABLE_ENROLMENT_BATCH_LOOKUP), anyMap(), anyList())).thenReturn(casRows);

        cacheMgr.bulkInitBatchStats(List.of("batch1"));

        verify(writePipeline).hmset(eq(Constants.BP_BATCH_STATS_PREFIX + "batch1"), anyMap());
        verify(writePipeline).expire(Constants.BP_BATCH_STATS_PREFIX + "batch1", 600);
        verify(writePipeline).sync();
    }

    @Test
    void testBulkInitBatchStats_missingButAllCountsZero_skipsWrite() {
        when(configuration.getBpBatchStatsCacheIndex()).thenReturn(3);
        when(configuration.getBpBatchStatsSyncRedisPipelineSize()).thenReturn(10);

        Pipeline existsPipeline = mock(Pipeline.class);
        Pipeline writePipeline = mock(Pipeline.class);
        when(jedis.pipelined()).thenReturn(existsPipeline, writePipeline);
        Response<Boolean> existsResponse = mockResponse(false);
        when(existsPipeline.exists(anyString())).thenReturn(existsResponse);

        when(wfStatusRepo.countGroupedByStatusForApplicationIds(anyList())).thenReturn(new ArrayList<>());
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any())).thenReturn(new ArrayList<>());

        cacheMgr.bulkInitBatchStats(List.of("batchWithNoActivity"));

        verify(writePipeline, never()).hmset(anyString(), anyMap());
        verify(writePipeline).sync();
    }

    @SuppressWarnings("unchecked")
    private Response<Boolean> mockResponse(boolean value) {
        Response<Boolean> response = mock(Response.class);
        when(response.get()).thenReturn(value);
        return response;
    }

    private List<Object[]> buildStatusRows() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"SEND_FOR_APPROVAL", 3L});
        rows.add(new Object[]{"WITHDRAWN", 2L});
        rows.add(new Object[]{"REJECTED", 1L});
        return rows;
    }

    private List<Map<String, Object>> buildActiveEnrollmentRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put(Constants.ACTIVE, Boolean.TRUE);
        rows.add(row);
        Map<String, Object> inactiveRow = new HashMap<>();
        inactiveRow.put(Constants.ACTIVE, Boolean.FALSE);
        rows.add(inactiveRow);
        return rows;
    }

    @Test
    void testCountActiveEnrollments_handlesNullRowGracefully() {
        // exercised indirectly via getBatchStats cache-miss path with a null entry in the Cassandra rows
        when(configuration.getBpBatchStatsCacheIndex()).thenReturn(3);
        when(jedis.exists(anyString())).thenReturn(false);
        when(wfStatusRepo.countGroupedByStatusForApplicationId("batch1")).thenReturn(buildStatusRows());
        List<Map<String, Object>> rowsWithNull = new ArrayList<>();
        rowsWithNull.add(null);
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                eq(Constants.TABLE_ENROLMENT_BATCH_LOOKUP), anyMap(), anyList())).thenReturn(rowsWithNull);
        when(configuration.getBpBatchStatsCacheTtl()).thenReturn(600);
        when(jedis.hgetAll(anyString())).thenReturn(Map.of());

        assertDoesNotThrow(() -> cacheMgr.getBatchStats("batch1"));
    }
}
