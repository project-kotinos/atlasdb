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

package com.palantir.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;

import com.palantir.logsafe.exceptions.SafeRuntimeException;

public final class ExceptionHandlingRunnerTests {
    private static final Runnable RUNNABLE_WITH_EXCEPTION = () -> {
        throw new RuntimeException();
    };

    private ExceptionHandlingRunner runner;

    @Before
    public void before() {
        runner = new ExceptionHandlingRunner();
    }

    @Test
    public void runnableExceptionNotThrown() {
        assertThatRunnableDoesNotThrow(RUNNABLE_WITH_EXCEPTION);
        assertThatCloseThrows();
    }

    @Test
    public void supplierExceptionNotThrown() {
        Supplier<?> supplierWithException = () -> {
            throw new RuntimeException();
        };
        assertThatSupplierDoesNotThrow(supplierWithException);
        assertThatCloseThrows();
    }

    @Test
    public void closeDoesNotThrowWhenNoExceptionsCaught() {
        Supplier<Integer> cleanSupplier = () -> 4;
        Runnable cleanRunnable = () -> {
        };
        assertThatSupplierDoesNotThrow(cleanSupplier);
        assertThatRunnableDoesNotThrow(cleanRunnable);
        assertThatCode(runner::close).doesNotThrowAnyException();
    }

    @Test
    public void closeThrowsWhenInstantiatedWithException() {
        RuntimeException runtimeException = new RuntimeException();
        runner = new ExceptionHandlingRunner(runtimeException);
        assertThatCloseThrows();
    }

    @Test
    public void closeMultipleTimesRethrows() {
        assertThatRunnableDoesNotThrow(RUNNABLE_WITH_EXCEPTION);
        assertThatCloseThrows();
        assertThatCloseThrows();
    }

    private void assertThatRunnableDoesNotThrow(Runnable runnable) {
        assertThatCode(() -> runner.runSafely(runnable)).doesNotThrowAnyException();
    }

    private void assertThatSupplierDoesNotThrow(Supplier<?> supplier) {
        assertThatCode(() -> runner.supplySafely(supplier)).doesNotThrowAnyException();
    }

    private void assertThatCloseThrows() {
        assertThatExceptionOfType(SafeRuntimeException.class)
                .isThrownBy(runner::close);
    }
}