package com.bandup.api.grammar.domain.lesson.valueobject;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** The CEFR levels grammar courses exist for (grammar requirement 1). */
@ValueObject
@Getter
@RequiredArgsConstructor
public enum GrammarLevel {
    A2("Elementary"),
    B1("Intermediate"),
    B2("Upper-Intermediate"),
    C1("Advanced");

    private final String displayName;
}
