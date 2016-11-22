/**
 * Copyright 2016 Palantir Technologies
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
package com.palantir.atlasdb.jepsen;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(as = ImmutableInvokeRead.class)
@JsonDeserialize(as = ImmutableInvokeRead.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(InvokeRead.TYPE)
@Value.Immutable
public abstract class InvokeRead implements Event {
    public static final String TYPE = "invoke";

    public abstract Long time();

    public abstract Integer process();

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
