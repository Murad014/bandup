package com.bandup.api.grammar.application.port;

import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;

/** Port: whether learners have used a lesson (runs, finished lists or review cards), so it must be archived, not deleted. */
public interface LessonUsage {

    boolean isUsed(LessonId lesson);
}
