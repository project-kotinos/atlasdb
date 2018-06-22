/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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
package com.palantir.atlasdb.sweep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

import com.google.common.collect.Sets;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.keyvalue.api.KeyAlreadyExistsException;
import com.palantir.atlasdb.transaction.service.TransactionService;

public class CommitTsCacheTest {
    private static final Long VALID_START_TIMESTAMP = 100L;
    private static final Long VALID_COMMIT_TIMESTAMP = 200L;
    private static final Long ROLLBACK_TIMESTAMP = -1L;
    private static final Long NO_TIMESTAMP = null;

    private final TransactionService mockTransactionService = mock(TransactionService.class);
    private final CommitTsCache loader = CommitTsCache.create(mockTransactionService);

    @Test
    public void loadShouldReturnTheValidTimestamp() throws Exception {
        when(mockTransactionService.get(VALID_START_TIMESTAMP))
                .thenReturn(VALID_COMMIT_TIMESTAMP);

        assertThat(loader.load(VALID_START_TIMESTAMP)).isEqualTo(VALID_COMMIT_TIMESTAMP);
    }

    @Test
    public void loadShouldPutRollbackIfCommitTsIsNull() throws Exception {
        AtomicLong answerCount = new AtomicLong();

        doAnswer((invocation) -> answerCount.get() > 0 ? ROLLBACK_TIMESTAMP : NO_TIMESTAMP)
                .when(mockTransactionService).get(VALID_START_TIMESTAMP);

        doAnswer((invocation) -> {
            answerCount.set(1);
            return null;
        }).when(mockTransactionService).putUnlessExists(VALID_START_TIMESTAMP, ROLLBACK_TIMESTAMP);

        assertThat(loader.load(VALID_START_TIMESTAMP)).isEqualTo(ROLLBACK_TIMESTAMP);

        verify(mockTransactionService).putUnlessExists(VALID_START_TIMESTAMP, ROLLBACK_TIMESTAMP);
    }

    @Test
    public void loadShouldContinueIfKeyAlreadyExistsIsThrown() throws Exception {
        AtomicLong answerCount = new AtomicLong();

        doAnswer((invocation) -> answerCount.get() > 0 ? VALID_COMMIT_TIMESTAMP : NO_TIMESTAMP)
                .when(mockTransactionService).get(VALID_START_TIMESTAMP);

        doAnswer((invocation) -> {
            answerCount.set(1);
            throw new KeyAlreadyExistsException("Already exists");
        }).when(mockTransactionService).putUnlessExists(VALID_START_TIMESTAMP, ROLLBACK_TIMESTAMP);

        assertThat(loader.load(VALID_START_TIMESTAMP)).isEqualTo(VALID_COMMIT_TIMESTAMP);

        verify(mockTransactionService).putUnlessExists(VALID_START_TIMESTAMP, ROLLBACK_TIMESTAMP);
    }

    @Test
    public void loadShouldThrowIfANullIsToBeReturned() throws Exception {
        doAnswer((invocation) -> NO_TIMESTAMP)
                .when(mockTransactionService).get(VALID_START_TIMESTAMP);

        assertThat(loader.load(VALID_START_TIMESTAMP)).isEqualTo(ROLLBACK_TIMESTAMP);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void warmingCacheShouldNotPlaceUndueLoadOnTransactionService() throws Exception {
        long valuesToInsert = 1_000_000;

        doAnswer((invocation) -> {
            Collection<Long> timestamps = (Collection<Long>) invocation.getArguments()[0];
            if (timestamps.size() > AtlasDbConstants.TRANSACTION_TIMESTAMP_LOAD_BATCH_LIMIT) {
                fail("Requested more timestamps in a batch than is reasonable!");
            }
            return timestamps.stream().collect(Collectors.toMap(n -> n, n -> n));
        }).when(mockTransactionService).get(any());

        Set<Long> initialTimestamps = LongStream.range(0, valuesToInsert).boxed().collect(Collectors.toSet());

        loader.loadBatch(initialTimestamps);
        assertThat(loader.load(valuesToInsert - 1)).isEqualTo(valuesToInsert - 1);
    }

    @Test
    public void onlyRequestNonCachedTimestamps() throws Exception {
        Set<Long> initialTimestamps = LongStream.range(0L, 20L).boxed().collect(Collectors.toSet());
        doAnswer(invocation -> assertRequestedTimestampsAndMapIdentity(invocation, initialTimestamps))
                .when(mockTransactionService).get(any());

        loader.loadBatch(initialTimestamps);
        assertThat(loader.load(19L)).isEqualTo(19L);

        Set<Long> moreTimestamps = LongStream.range(10L, 30L).boxed().collect(Collectors.toSet());
        doAnswer(invocation -> assertRequestedTimestampsAndMapIdentity(invocation,
                Sets.difference(moreTimestamps, initialTimestamps)))
                .when(mockTransactionService).get(any());

        loader.loadBatch(moreTimestamps);
        assertThat(loader.load(27L)).isEqualTo(27L);

        Set<Long> evenMoreTimestamps = LongStream.range(7L, 50L).boxed().collect(Collectors.toSet());
        doAnswer(invocation -> assertRequestedTimestampsAndMapIdentity(invocation,
                Sets.difference(evenMoreTimestamps, Sets.union(initialTimestamps, moreTimestamps))))
                .when(mockTransactionService).get(any());

        loader.loadBatch(evenMoreTimestamps);
        assertThat(loader.load(3L)).isEqualTo(3L);
        assertThat(loader.load(37L)).isEqualTo(37L);
        verify(mockTransactionService, times(3)).get(anyList());
        verifyNoMoreInteractions(mockTransactionService);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Long> assertRequestedTimestampsAndMapIdentity(InvocationOnMock invocation,
            Collection<Long> expected) {
        Collection<Long> timestamps = (Collection<Long>) invocation.getArguments()[0];
        assertThat(timestamps).containsExactlyElementsOf(expected);
        return timestamps.stream().collect(Collectors.toMap(n -> n, n -> n));
    }
}
