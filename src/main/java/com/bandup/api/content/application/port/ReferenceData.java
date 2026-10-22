package com.bandup.api.content.application.port;

/** Port: whether referenced stages, topics and media exist, so a save gets a clear 422 instead of a database error. */
public interface ReferenceData {

    boolean stageExists(long id);

    boolean topicExists(long id);

    boolean mediaExists(long id);
}
