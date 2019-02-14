/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.keyvalue.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.thrift.TException;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.palantir.atlasdb.cassandra.CassandraKeyValueServiceConfig;

public class CassandraKeyValueServicesSchemaConsensusTest {
    private static CassandraKeyValueServiceConfig config = mock(CassandraKeyValueServiceConfig.class);
    private static CassandraKeyValueServiceConfig waitingConfig = mock(CassandraKeyValueServiceConfig.class);
    private static CassandraClient client = mock(CassandraClient.class);

    private static final Set<InetSocketAddress> FIVE_SERVERS = ImmutableSet.of(
            new InetSocketAddress("1", 0),
            new InetSocketAddress("2", 0),
            new InetSocketAddress("3", 0),
            new InetSocketAddress("4", 0),
            new InetSocketAddress("5", 0));
    private static final String TABLE = "table";
    private static final String VERSION_1 = "v1";
    private static final String VERSION_2 = "v2";
    private static final String VERSION_UNREACHABLE = "UNREACHABLE";
    private static final List<String> QUORUM_OF_NODES = ImmutableList.of("1", "2", "3");
    private static final List<String> REST_OF_NODES = ImmutableList.of("4", "5");
    private static final List<String> ALL_NODES = ImmutableList.of("1", "2", "3", "4", "5");

    @BeforeClass
    public static void initializeMocks() {
        when(config.schemaMutationTimeoutMillis()).thenReturn(0);
        when(config.servers()).thenReturn(FIVE_SERVERS);
        when(waitingConfig.schemaMutationTimeoutMillis()).thenReturn(10_000);
        when(waitingConfig.servers()).thenReturn(FIVE_SERVERS);
    }

    @Test
    public void waitSucceedsForSameSchemaVersion() throws TException {
        when(client.describe_schema_versions()).thenReturn(ImmutableMap.of(VERSION_1, ALL_NODES));
        assertrunWithWaitingForSchemasDoesNotThrow();
    }

    @Test
    public void waitThrowsForAllUnreachableSchemaVersion() throws TException {
        when(client.describe_schema_versions()).thenReturn(ImmutableMap.of(VERSION_UNREACHABLE, ALL_NODES));
        assertRunWithWaitingForSchemaVersionsThrowsAndContainsConfigNodesInformation();
    }

    @Test
    public void waitThrowsForQuorumOfUnreachableNodes() throws TException {
        when(client.describe_schema_versions())
                .thenReturn(ImmutableMap.of(VERSION_UNREACHABLE, QUORUM_OF_NODES, VERSION_1, REST_OF_NODES));
        assertRunWithWaitingForSchemaVersionsThrowsAndContainsConfigNodesInformation();
    }

    @Test
    public void waitSuceedsForOneUnreachableNode() throws TException {
        when(client.describe_schema_versions())
                .thenReturn(ImmutableMap.of(VERSION_UNREACHABLE, ImmutableList.of("1"), VERSION_1, REST_OF_NODES));
        assertrunWithWaitingForSchemasDoesNotThrow();
    }

    @Test
    public void waitSucceedsWhenAllOnSameSchemaVersionButSomeNodesFromConfigDoNotExist() throws TException {
        when(client.describe_schema_versions()).thenReturn(ImmutableMap.of(VERSION_1, REST_OF_NODES));
        assertrunWithWaitingForSchemasDoesNotThrow();
    }

    @Test
    public void waitThrowsForMoreThanOneUnreachableSchemaVersion() throws TException {
        when(client.describe_schema_versions())
                .thenReturn(ImmutableMap.of(VERSION_1, QUORUM_OF_NODES, VERSION_UNREACHABLE, REST_OF_NODES));
        assertRunWithWaitingForSchemaVersionsThrowsAndContainsConfigNodesInformation();
    }

    @Test
    public void waitThrowsForDifferentSchemaVersion() throws TException {
        when(client.describe_schema_versions())
                .thenReturn(ImmutableMap.of(VERSION_1, QUORUM_OF_NODES, VERSION_2, REST_OF_NODES));
        assertRunWithWaitingForSchemaVersionsThrowsAndContainsConfigNodesInformation();
    }

    @Test
    public void waitCanFailAfterTask() throws TException {
        when(client.describe_schema_versions())
                .thenReturn(ImmutableMap.of(VERSION_1, ALL_NODES))
                .thenReturn(ImmutableMap.of(VERSION_1, QUORUM_OF_NODES, VERSION_2, REST_OF_NODES));
        AtomicBoolean taskPerformed = new AtomicBoolean(false);
        assertThatThrownBy(
                () -> CassandraKeyValueServices.runWithWaitingForSchemas(
                        () -> taskPerformed.set(true),
                        config,
                        client,
                        TABLE)
        ).isInstanceOf(IllegalStateException.class);
        assertThat(taskPerformed.get()).isTrue();
    }

    @Test
    public void waitCanFailBeforeTask() throws TException {
        when(client.describe_schema_versions())
                .thenReturn(ImmutableMap.of(VERSION_1, QUORUM_OF_NODES, VERSION_2, REST_OF_NODES));
        AtomicBoolean taskPerformed = new AtomicBoolean(false);
        assertThatThrownBy(
                () -> CassandraKeyValueServices.runWithWaitingForSchemas(
                        () -> taskPerformed.set(true),
                        config,
                        client,
                        TABLE)
        ).isInstanceOf(IllegalStateException.class);
        assertThat(taskPerformed.get()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void assertRunWithWaitingForSchemaVersionsWaitsForSchemaVersions() throws TException {
        CassandraClient waitingClient = mock(CassandraClient.class);
        when(waitingClient.describe_schema_versions()).thenReturn(
                ImmutableMap.of(VERSION_UNREACHABLE, QUORUM_OF_NODES, VERSION_1, REST_OF_NODES),
                ImmutableMap.of(VERSION_1, QUORUM_OF_NODES, VERSION_UNREACHABLE, REST_OF_NODES),
                ImmutableMap.of(VERSION_1, ALL_NODES),
                ImmutableMap.of(VERSION_UNREACHABLE, QUORUM_OF_NODES, VERSION_1, REST_OF_NODES),
                ImmutableMap.of(VERSION_1, ALL_NODES));

        CassandraKeyValueServices.runWithWaitingForSchemas(() -> { }, waitingConfig, waitingClient, TABLE);
        verify(waitingClient, times(3 + 2)).describe_schema_versions();
    }

    private void assertRunWithWaitingForSchemaVersionsThrowsAndContainsConfigNodesInformation() {
        assertThatThrownBy(() -> CassandraKeyValueServices.runWithWaitingForSchemas(() -> { }, config, client, TABLE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FIVE_SERVERS.iterator().next().getHostName());
    }

    private void assertrunWithWaitingForSchemasDoesNotThrow() throws TException {
        CassandraKeyValueServices.runWithWaitingForSchemas(() -> { }, config, client, TABLE);
    }
}
