package com.bandup.api.identity.web;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.identity.application.InstructorRequests;

/** Instructor sign-ups waiting for an admin (requirement 12.5): list, approve, reject. ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/instructor-requests")
@RequiredArgsConstructor
class InstructorRequestController {

    private final InstructorRequests requests;

    @GetMapping
    List<InstructorRequests.Request> pending() {
        return requests.pending();
    }

    @PostMapping("/{userId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@PathVariable UUID userId) {
        requests.approve(userId);
    }

    @PostMapping("/{userId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(@PathVariable UUID userId) {
        requests.reject(userId);
    }
}
