package com.bandup.api.practice.domain.attempt.repository;

import java.util.Optional;

import com.bandup.api.practice.domain.attempt.aggregate.Attempt;
import com.bandup.api.practice.domain.attempt.valueobject.AttemptId;

/** Stores attempts whole (with their graded answers). Implemented in infrastructure. */
public interface AttemptRepository {

    void save(Attempt attempt);

    Optional<Attempt> findById(AttemptId id);
}
