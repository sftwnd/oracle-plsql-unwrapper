// SPDX-License-Identifier: BSD-3-Clause

package com.github.sftwnd.oracle.unwrap.v2;

/** What becomes of a stray CR of a V2 text ({@link V2StrayCr}): the values of the option {@code strayCr}. */
enum StrayCr {
    /** A space, as WRAP 10.2 to 11.2 wrote for the other CRs of the source. */
    SPACE,
    /** The CR as the unit holds it. */
    KEEP
}
