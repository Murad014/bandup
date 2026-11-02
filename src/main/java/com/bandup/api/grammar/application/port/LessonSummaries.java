package com.bandup.api.grammar.application.port;

import java.util.List;

import com.bandup.api.grammar.application.LessonSummary;

/** Port: the admin lesson list (a read model), newest change first. */
public interface LessonSummaries {

    /** @param level  null for every level @param status null for every status */
    List<LessonSummary> list(String level, String status);
}
