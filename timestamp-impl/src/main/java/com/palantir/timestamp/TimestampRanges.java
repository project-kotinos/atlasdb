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

package com.palantir.timestamp;

import java.util.OptionalLong;

import com.google.common.base.Preconditions;
import com.google.common.math.LongMath;

/**
 * Utilities for manipulating {@link TimestampRange} objects.
 */
public final class TimestampRanges {
    private TimestampRanges() {
        // utility
    }

    /**
     * Returns the lowest timestamp in the provided timestamp range that has the provided residue class modulo the
     * provided modulus.
     *
     * If the timestamp range does not contain a timestamp matching the criteria, returns empty.
     *
     * @param residue desired residue class of the timestamp returned
     * @param modulus modulus used to partition numbers into residue classes
     * @return a timestamp in the given range in the relevant residue class modulo modulus
     * @throws IllegalArgumentException if modulus <= 0
     * @throws IllegalArgumentException if |residue| >= modulus; this is unsolvable
     */
    public static OptionalLong getLowestTimestampMatchingModulus(TimestampRange range, int residue, int modulus) {
        checkModulusAndResidue(residue, modulus);

        long lowerBoundResidue = LongMath.mod(range.getLowerBound(), modulus);
        long shift = residue < lowerBoundResidue ? modulus + residue - lowerBoundResidue :
                residue - lowerBoundResidue;
        long candidate = range.getLowerBound() + shift;

        return range.getUpperBound() >= candidate
                ? OptionalLong.of(candidate)
                : OptionalLong.empty();
    }

    /**
     * Returns the lowest timestamp in the provided timestamp range that has the provided residue class modulo the
     * provided modulus.
     *
     * If the timestamp range does not contain a timestamp matching the criteria, returns empty.
     *
     * @param residue desired residue class of the timestamp returned
     * @param modulus modulus used to partition numbers into residue classes
     * @return a timestamp in the given range in the relevant residue class modulo modulus
     * @throws IllegalArgumentException if modulus <= 0
     * @throws IllegalArgumentException if |residue| >= modulus; this is unsolvable
     */
    public static OptionalLong getHighestTimestampMatchingModulus(TimestampRange range, int residue, int modulus) {
        checkModulusAndResidue(residue, modulus);

        long upperBoundResidue = LongMath.mod(range.getUpperBound(), modulus);
        long shift = residue > upperBoundResidue ? modulus + upperBoundResidue - residue:
                upperBoundResidue - residue;
        long candidate = range.getUpperBound() - shift;

        return range.getLowerBound() <= candidate
                ? OptionalLong.of(candidate)
                : OptionalLong.empty();
    }

    private static void checkModulusAndResidue(int residue, int modulus) {
        Preconditions.checkArgument(modulus > 0, "Modulus should be positive, but found %s.", modulus);
        Preconditions.checkArgument(Math.abs(residue) < modulus,
                "Absolute value of residue %s equals or exceeds modulus %s - no solutions",
                residue,
                modulus);
    }
}
