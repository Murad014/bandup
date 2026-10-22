package com.bandup.api.content.infrastructure.persistence;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** {@code exercise_topic}: one topic of an exercise. */
@Entity
@Table(name = "exercise_topic")
@IdClass(ExerciseTopicEntity.Key.class)
class ExerciseTopicEntity {

    /** Composite primary key. */
    record Key(Long exerciseId, Long topicId) implements Serializable {
    }


    @Id
    @Column(name = "exercise_id")
    Long exerciseId;

    @Id
    @Column(name = "topic_id")
    Long topicId;
}
