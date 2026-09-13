package com.jobpilot.resume;

import com.jobpilot.auth.security.UserPrincipal;
import com.jobpilot.resume.dto.CreateResumeRequest;
import com.jobpilot.resume.dto.ResumeResponse;
import com.jobpilot.resume.dto.UpdateResumeRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateResumeRequest request
    ) {
        return resumeService.create(principal.getId(), request);
    }

    @GetMapping
    public List<ResumeResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return resumeService.list(principal.getId());
    }

    @GetMapping("/{id}")
    public ResumeResponse get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return resumeService.get(principal.getId(), id);
    }

    @PatchMapping("/{id}")
    public ResumeResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateResumeRequest request
    ) {
        return resumeService.update(principal.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        resumeService.delete(principal.getId(), id);
    }
}
