/**
 * Copyright 2017 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.persistentlock;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

import org.immutables.value.Value;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.keyvalue.api.RowResult;

@Value.Immutable
public abstract class LockEntry {
    private static final String REASON_FOR_LOCK_COLUMN = "reasonForLock";
    private static final String LOCK_ID_COLUMN = "lockId";

    public abstract String rowName();
    public abstract String lockId();
    public abstract String reason();

    public static LockEntry fromRowResult(RowResult<com.palantir.atlasdb.keyvalue.api.Value> rowResult) {
        String rowName = asString(rowResult.getRowName());
        String lockId = valueOfColumnInRow(LOCK_ID_COLUMN, rowResult).get();
        String reason = valueOfColumnInRow(REASON_FOR_LOCK_COLUMN, rowResult).get();

        return ImmutableLockEntry.builder()
                .rowName(rowName)
                .lockId(lockId)
                .reason(reason)
                .build();
    }

    public Map<Cell, byte[]> insertionMap() {
        return ImmutableMap.of(
                makeCell(LOCK_ID_COLUMN), asUtf8Bytes(lockId()),
                makeCell(REASON_FOR_LOCK_COLUMN), asUtf8Bytes(reason()));
    }

    public Multimap<Cell, Long> deletionMap() {
        Long timestamp = AtlasDbConstants.TRANSACTION_TS;
        return ImmutableMultimap.of(
                makeCell(LOCK_ID_COLUMN), timestamp,
                makeCell(REASON_FOR_LOCK_COLUMN), timestamp);
    }

    private static Optional<String> valueOfColumnInRow(
            String columnName,
            RowResult<com.palantir.atlasdb.keyvalue.api.Value> rowResult) {
        byte[] columnNameBytes = asUtf8Bytes(columnName);
        SortedMap<byte[], com.palantir.atlasdb.keyvalue.api.Value> columns = rowResult.getColumns();
        if (columns.containsKey(columnNameBytes)) {
            byte[] contents = columns.get(columnNameBytes).getContents();
            return Optional.of(asString(contents));
        } else {
            return Optional.empty();
        }
    }

    private Cell makeCell(String columnName) {
        byte[] rowBytes = asUtf8Bytes(rowName());
        byte[] columnBytes = asUtf8Bytes(columnName);
        return Cell.create(rowBytes, columnBytes);
    }

    private static byte[] asUtf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String asString(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
