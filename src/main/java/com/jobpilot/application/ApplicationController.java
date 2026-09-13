package com.jobpilot.application;

import com.jobpilot.application.dto.ApplicationResponse;
import com.jobpilot.application.dto.CreateApplicationRequest;
import com.jobpilot.application.dto.UpdateApplicationRequest;
import com.jobpilot.auth.security.UserPrincipal;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateApplicationRequest request
    ) {
        return applicationService.create(principal.getId(), request);
    }

    @GetMapping
    public Page<ApplicationResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "appliedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return applicationService.list(principal.getId(), status, q, pageable);
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return applicationService.get(principal.getId(), id);
    }

    @PatchMapping("/{id}")
    public ApplicationResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationRequest request
    ) {
        return applicationService.update(principal.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        applicationService.delete(principal.getId(), id);
    }
}
