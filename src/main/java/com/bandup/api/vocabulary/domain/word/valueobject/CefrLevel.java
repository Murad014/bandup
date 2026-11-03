package com.bandup.api.vocabulary.domain.word.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A word's CEFR level. */
@ValueObject
public enum CefrLevel {
    A1, A2, B1, B2, C1, C2;

    public static boolean isLevel(String code) {
        for (CefrLevel level : values()) {
            if (level.name().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
