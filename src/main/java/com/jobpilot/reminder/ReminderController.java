package com.jobpilot.reminder;

import com.jobpilot.auth.security.UserPrincipal;
import com.jobpilot.reminder.dto.CreateReminderRequest;
import com.jobpilot.reminder.dto.ReminderResponse;
import com.jobpilot.reminder.dto.UpdateReminderRequest;
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
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateReminderRequest request
    ) {
        return reminderService.create(principal.getId(), request);
    }

    @GetMapping
    public Page<ReminderResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) ReminderStatus status,
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return reminderService.list(principal.getId(), status, pageable);
    }

    @GetMapping("/{id}")
    public ReminderResponse get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return reminderService.get(principal.getId(), id);
    }

    @PatchMapping("/{id}")
    public ReminderResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReminderRequest request
    ) {
        return reminderService.update(principal.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        reminderService.delete(principal.getId(), id);
    }
}
