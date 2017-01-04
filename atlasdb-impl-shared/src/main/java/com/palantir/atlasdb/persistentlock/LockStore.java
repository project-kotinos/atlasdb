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

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.keyvalue.api.RangeRequest;
import com.palantir.atlasdb.keyvalue.api.RowResult;
import com.palantir.atlasdb.keyvalue.api.Value;

public final class LockStore {
    private static final String ROW_NAME = "DeletionLock";
    private final KeyValueService keyValueService;

    private LockStore(KeyValueService kvs) {
        this.keyValueService = kvs;
    }

    public static LockStore create(KeyValueService kvs) {
        kvs.createTable(AtlasDbConstants.PERSISTED_LOCKS_TABLE, AtlasDbConstants.GENERIC_TABLE_METADATA);
        return new LockStore(kvs);
    }

    public LockEntry acquireLock(String reason) {
        LockEntry lockEntry = generateUniqueLockEntry(reason);

        keyValueService.putUnlessExists(AtlasDbConstants.PERSISTED_LOCKS_TABLE, lockEntry.insertionMap());

        return lockEntry;
    }

    public void releaseLock(LockEntry lockEntry) {
        if (!allLockEntries().contains(lockEntry)) {
            return;
        }

        // TODO this seems to delete *all* lock entries, hence the line above. We should throw an exception if we don't have this lock out.
        keyValueService.delete(AtlasDbConstants.PERSISTED_LOCKS_TABLE, lockEntry.deletionMap());
    }

    public Set<LockEntry> allLockEntries() {
        Set<RowResult<Value>> results = ImmutableSet.copyOf(keyValueService.getRange(
                AtlasDbConstants.PERSISTED_LOCKS_TABLE,
                RangeRequest.all(),
                AtlasDbConstants.TRANSACTION_TS + 1));

        return results.stream().map(LockEntry::fromRowResult).collect(Collectors.toSet());
    }

    private LockEntry generateUniqueLockEntry(String reason) {
        UUID uuid = UUID.randomUUID();
        return ImmutableLockEntry.builder().rowName(ROW_NAME).lockId(uuid.toString()).reason(reason).build();
    }
}
