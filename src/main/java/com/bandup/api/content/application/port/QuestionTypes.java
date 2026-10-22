package com.bandup.api.content.application.port;

import java.util.Map;

import com.bandup.api.content.domain.exercise.valueobject.QuestionFamily;

/** Port: the question types ({@code question_type}): code → family. */
public interface QuestionTypes {

    Map<String, QuestionFamily> all();
}
