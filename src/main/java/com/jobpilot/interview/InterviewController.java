package com.jobpilot.interview;

import com.jobpilot.auth.security.UserPrincipal;
import com.jobpilot.interview.dto.CreateInterviewRequest;
import com.jobpilot.interview.dto.InterviewResponse;
import com.jobpilot.interview.dto.UpdateInterviewRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping("/api/applications/{applicationId}/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID applicationId,
            @Valid @RequestBody CreateInterviewRequest request
    ) {
        return interviewService.create(principal.getId(), applicationId, request);
    }

    @GetMapping("/api/applications/{applicationId}/interviews")
    public Page<InterviewResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID applicationId,
            @PageableDefault(size = 20, sort = {"roundNumber", "scheduledAt"}, direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return interviewService.list(principal.getId(), applicationId, pageable);
    }

    @GetMapping("/api/interviews/{id}")
    public InterviewResponse get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return interviewService.get(principal.getId(), id);
    }

    @PatchMapping("/api/interviews/{id}")
    public InterviewResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInterviewRequest request
    ) {
        return interviewService.update(principal.getId(), id, request);
    }

    @DeleteMapping("/api/interviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        interviewService.delete(principal.getId(), id);
    }
}
