package org.sunbird.workflow.utils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.exception.ProjectCommonException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CassandraConnectionManagerImplTest {

    @Mock
    PropertiesCache propertiesCache;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetConsistencyLevel_valid() {
        try (MockedStatic<ProjectUtil> staticMock = mockStatic(ProjectUtil.class)) {
            staticMock.when(() -> ProjectUtil.getConfigValue(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("ONE");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertEquals(DefaultConsistencyLevel.ONE, level);
        }
    }

    @Test
    void testGetConsistencyLevel_invalid() {
        try (MockedStatic<ProjectUtil> staticMock = mockStatic(ProjectUtil.class)) {
            staticMock.when(() -> ProjectUtil.getConfigValue(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("INVALID_LEVEL");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertNull(level);
        }
    }

    private ConsistencyLevel invokeGetConsistencyLevel() {
        try {
            Method method = CassandraConnectionManagerImpl.class.getDeclaredMethod("getConsistencyLevel");
            method.setAccessible(true);
            return (ConsistencyLevel) method.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testConstructorThrowsException_whenHostIsBlank() {
        try (
                MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class)
        ) {
            // Arrange
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");

            // Act & Assert
            ProjectCommonException exception = assertThrows(ProjectCommonException.class, CassandraConnectionManagerImpl::new);
            assertEquals("Cassandra host is not configured", exception.getMessage()); // Adjust message if needed
        }
    }

    @Test
    void testResourceCleanUp() {
        CassandraConnectionManagerImpl outer = mock(CassandraConnectionManagerImpl.class);
        CassandraConnectionManagerImpl.ResourceCleanUp cleanup = outer.new ResourceCleanUp();

        Map<String, CqlSession> sessionMap = getStaticSessionMap();
        CqlSession mockSession = mock(CqlSession.class);
        sessionMap.put("keyspace", mockSession);

        setSession(outer, mockSession);

        cleanup.run(); // triggers close()

        verify(mockSession, atLeastOnce()).close();
    }
    private Map<String, CqlSession> getStaticSessionMap() {
        try {
            var field = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
            field.setAccessible(true);
            return (Map<String, CqlSession>) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setSession(CassandraConnectionManagerImpl target, CqlSession session) {
        try {
            var field = CassandraConnectionManagerImpl.class.getDeclaredField("session");
            field.setAccessible(true);
            field.set(target, session);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testGetSession_returnsExistingOpenSession_withoutCreatingNew() throws Exception {
        CqlSession mockSession = mock(CqlSession.class);
        when(mockSession.isClosed()).thenReturn(false);
        Map<String, CqlSession> sessionMap = getStaticSessionMap();
        sessionMap.put("openKeyspace", mockSession);

        CassandraConnectionManagerImpl outer = mock(CassandraConnectionManagerImpl.class);
        doCallRealMethod().when(outer).getSession("openKeyspace");

        CqlSession result = outer.getSession("openKeyspace");

        assertSame(mockSession, result);
        sessionMap.remove("openKeyspace");
    }

    @Test
    void testGetSession_createsNewSession_whenExistingIsClosed() throws Exception {
        CqlSession closedSession = mock(CqlSession.class);
        when(closedSession.isClosed()).thenReturn(true);
        Map<String, CqlSession> sessionMap = getStaticSessionMap();
        sessionMap.put("closedKeyspace", closedSession);

        try (MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class)) {
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");

            CassandraConnectionManagerImpl outer = mock(CassandraConnectionManagerImpl.class);
            doCallRealMethod().when(outer).getSession("closedKeyspace");

            assertThrows(ProjectCommonException.class, () -> outer.getSession("closedKeyspace"));
        } finally {
            sessionMap.remove("closedKeyspace");
        }
    }

    @Test
    void testCreateCassandraConnectionWithKeySpaces_success_withKeyspace() throws Exception {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class);
             MockedStatic<CqlSession> cqlSessionStatic = Mockito.mockStatic(CqlSession.class)) {

            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("host1,host2");
            when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("1");
            when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
            when(mockPropertiesCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30");

            CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class, Mockito.RETURNS_SELF);
            CqlSession mockSession = mock(CqlSession.class);
            when(mockBuilder.build()).thenReturn(mockSession);
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            Metadata mockMetadata = mock(Metadata.class);
            when(mockSession.getMetadata()).thenReturn(mockMetadata);
            when(mockMetadata.getClusterName()).thenReturn(Optional.of("testCluster"));
            Node mockNode = mock(Node.class);
            when(mockNode.getDatacenter()).thenReturn("dc1");
            when(mockNode.getEndPoint()).thenReturn(null);
            when(mockNode.getRack()).thenReturn("rack1");
            Map<UUID, Node> nodes = Map.of(UUID.randomUUID(), mockNode);
            when(mockMetadata.getNodes()).thenReturn(nodes);

            CassandraConnectionManagerImpl outer = mock(CassandraConnectionManagerImpl.class);
            Method method = CassandraConnectionManagerImpl.class
                    .getDeclaredMethod("createCassandraConnectionWithKeySpaces", String.class);
            method.setAccessible(true);

            CqlSession result = (CqlSession) method.invoke(outer, "myKeyspace");

            assertSame(mockSession, result);
            verify(mockBuilder).withKeyspace("myKeyspace");
        }
    }

    @Test
    void testCreateCassandraConnectionWithKeySpaces_success_withoutKeyspace() throws Exception {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class);
             MockedStatic<CqlSession> cqlSessionStatic = Mockito.mockStatic(CqlSession.class)) {

            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("host1");
            when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("1");
            when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
            when(mockPropertiesCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30");

            CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class, Mockito.RETURNS_SELF);
            CqlSession mockSession = mock(CqlSession.class);
            when(mockBuilder.build()).thenReturn(mockSession);
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            Metadata mockMetadata = mock(Metadata.class);
            when(mockSession.getMetadata()).thenReturn(mockMetadata);
            when(mockMetadata.getClusterName()).thenReturn(Optional.empty());
            when(mockMetadata.getNodes()).thenReturn(Map.of());

            Method method = CassandraConnectionManagerImpl.class
                    .getDeclaredMethod("createCassandraConnectionWithKeySpaces", String.class);
            method.setAccessible(true);
            CassandraConnectionManagerImpl outer = mock(CassandraConnectionManagerImpl.class);

            CqlSession result = (CqlSession) method.invoke(outer, (Object) null);

            assertSame(mockSession, result);
            verify(mockBuilder, never()).withKeyspace(anyString());
        }
    }

    @Test
    void testCreateCassandraConnectionWithKeySpaces_wrapsExceptionFromBuild() throws Exception {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class);
             MockedStatic<CqlSession> cqlSessionStatic = Mockito.mockStatic(CqlSession.class)) {

            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("host1");
            when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("1");
            when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
            when(mockPropertiesCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30");

            CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class, Mockito.RETURNS_SELF);
            when(mockBuilder.build()).thenThrow(new RuntimeException("connection refused"));
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            Method method = CassandraConnectionManagerImpl.class
                    .getDeclaredMethod("createCassandraConnectionWithKeySpaces", String.class);
            method.setAccessible(true);
            CassandraConnectionManagerImpl outer = mock(CassandraConnectionManagerImpl.class);

            Exception thrown = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> method.invoke(outer, (Object) null));
            assertInstanceOf(ProjectCommonException.class, thrown.getCause());
        }
    }

    @Test
    void testGetTableList_returnsTableNames_whenKeyspacePresent() throws Exception {
        CassandraConnectionManagerImpl outer = mock(CassandraConnectionManagerImpl.class);
        CqlSession mockSession = mock(CqlSession.class);
        setSession(outer, mockSession);
        doCallRealMethod().when(outer).getTableList("myKeyspace");

        Metadata mockMetadata = mock(Metadata.class);
        when(mockSession.getMetadata()).thenReturn(mockMetadata);
        KeyspaceMetadata mockKeyspaceMetadata = mock(KeyspaceMetadata.class);
        when(mockMetadata.getKeyspace("myKeyspace")).thenReturn(Optional.of(mockKeyspaceMetadata));
        CqlIdentifier tableId = CqlIdentifier.fromCql("my_table");
        TableMetadata mockTableMetadata = mock(TableMetadata.class);
        when(mockKeyspaceMetadata.getTables()).thenReturn(Map.of(tableId, mockTableMetadata));

        List<String> tables = outer.getTableList("myKeyspace");

        assertEquals(List.of(tableId.toString()), tables);
    }

    @Test
    void testGetTableList_throwsWhenKeyspaceNotFound() throws Exception {
        CassandraConnectionManagerImpl outer = mock(CassandraConnectionManagerImpl.class);
        CqlSession mockSession = mock(CqlSession.class);
        setSession(outer, mockSession);
        doCallRealMethod().when(outer).getTableList("missingKeyspace");

        Metadata mockMetadata = mock(Metadata.class);
        when(mockSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getKeyspace("missingKeyspace")).thenReturn(Optional.empty());

        ProjectCommonException exception = assertThrows(ProjectCommonException.class,
                () -> outer.getTableList("missingKeyspace"));
        assertTrue(exception.getMessage().contains("Keyspace not found: missingKeyspace"));
    }

    @Test
    void testGetSession_cacheMiss_createsAndCachesNewSession() throws Exception {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class);
             MockedStatic<CqlSession> cqlSessionStatic = Mockito.mockStatic(CqlSession.class)) {

            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("host1");
            when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("1");
            when(mockPropertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
            when(mockPropertiesCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30");

            CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class, Mockito.RETURNS_SELF);
            CqlSession mockSession = mock(CqlSession.class);
            when(mockBuilder.build()).thenReturn(mockSession);
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            Metadata mockMetadata = mock(Metadata.class);
            when(mockSession.getMetadata()).thenReturn(mockMetadata);
            when(mockMetadata.getClusterName()).thenReturn(Optional.of("cluster"));
            when(mockMetadata.getNodes()).thenReturn(Map.of());

            Map<String, CqlSession> sessionMap = getStaticSessionMap();
            sessionMap.remove("brandNewKeyspace");

            CassandraConnectionManagerImpl outer = mock(CassandraConnectionManagerImpl.class);
            doCallRealMethod().when(outer).getSession("brandNewKeyspace");

            CqlSession result = outer.getSession("brandNewKeyspace");

            assertSame(mockSession, result);
            assertSame(mockSession, sessionMap.get("brandNewKeyspace"));
            sessionMap.remove("brandNewKeyspace");
        }
    }

    @Test
    void testGetTableList_wrapsUnexpectedException() throws Exception {
        CassandraConnectionManagerImpl outer = mock(CassandraConnectionManagerImpl.class);
        CqlSession mockSession = mock(CqlSession.class);
        setSession(outer, mockSession);
        doCallRealMethod().when(outer).getTableList("errorKeyspace");

        when(mockSession.getMetadata()).thenThrow(new RuntimeException("boom"));

        ProjectCommonException exception = assertThrows(ProjectCommonException.class,
                () -> outer.getTableList("errorKeyspace"));
        assertTrue(exception.getMessage().contains("Error fetching tables for keyspace errorKeyspace"));
    }

}
