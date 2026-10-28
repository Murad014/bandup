package com.bandup.api.identity.domain.user.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * Where a user's request to be an Instructor stands (requirement 12.1). Only
 * an admin moves a {@code PENDING} request on; approval brings the AUTHOR role.
 */
@ValueObject
public enum InstructorStatus {
    /** Signed up as a learner. */
    NONE,
    /** Signed up as an Instructor; waiting for an admin. */
    PENDING,
    APPROVED,
    REJECTED
}
