package org.sunbird.workflow.utils;

public final class CassandraConnectionMngrFactory {

    private CassandraConnectionMngrFactory() {
    }

    private static class InstanceHolder {
        private static final CassandraConnectionManager INSTANCE = new CassandraConnectionManagerImpl();
    }

    public static CassandraConnectionManager getInstance() {
        return InstanceHolder.INSTANCE;
    }
}