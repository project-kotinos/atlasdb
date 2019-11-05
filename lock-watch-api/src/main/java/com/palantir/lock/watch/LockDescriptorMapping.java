/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.lock.watch;

import java.util.Map;
import java.util.Optional;

import org.immutables.value.Value;

import com.palantir.lock.LockDescriptor;

@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class LockDescriptorMapping {
    abstract Map<LockDescriptor, RowReference> mapping();

    public Optional<RowReference> rowReferenceForDescriptor(LockDescriptor lockDescriptor) {
        return Optional.ofNullable(mapping().get(lockDescriptor));
    }

    public static LockDescriptorMapping of(Map<LockDescriptor, RowReference> mapping) {
        return ImmutableLockDescriptorMapping.builder().mapping(mapping).build();
    }
}
