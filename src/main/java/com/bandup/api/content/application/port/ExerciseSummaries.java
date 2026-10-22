package com.bandup.api.content.application.port;

import java.util.List;

import com.bandup.api.content.application.ExerciseSummary;

/** Port: the admin list (read model, SQL), newest change first. */
public interface ExerciseSummaries {

    List<ExerciseSummary> all();
}
