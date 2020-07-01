/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.atlasdb.timelock.management;

import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import com.palantir.paxos.Client;
import com.palantir.paxos.SqlitePaxosStateLog;

public final class SqliteNamespaceLoader implements PersistentNamespaceLoader {
    private final Jdbi jdbi;

    private SqliteNamespaceLoader(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public static PersistentNamespaceLoader create(DataSource dataSource) {
        Jdbi jdbi = Jdbi.create(dataSource).installPlugin(new SqlObjectPlugin());
        jdbi.withExtension(SqlitePaxosStateLog.Queries.class, SqlitePaxosStateLog.Queries::createTable);
        return new SqliteNamespaceLoader(jdbi);
    }

    @Override
    public Set<Client> getAllPersistedNamespaces() {
        return jdbi.withExtension(SqlitePaxosStateLog.Queries.class, SqlitePaxosStateLog.Queries::getAllNamespaces)
                .stream()
                .map(Client::of)
                .collect(Collectors.toSet());
    }
}
