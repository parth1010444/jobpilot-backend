package com.jobpilot.resume;

import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.resume.dto.CreateResumeRequest;
import com.jobpilot.resume.dto.ResumeResponse;
import com.jobpilot.resume.dto.UpdateResumeRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;

    public ResumeService(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    @Transactional
    public ResumeResponse create(UUID userId, CreateResumeRequest request) {
        Resume resume = new Resume(
                userId,
                request.name().trim(),
                blankToNull(request.versionLabel()),
                blankToNull(request.description()),
                blankToNull(request.fileUrl())
        );
        resumeRepository.save(resume);
        return ResumeResponse.from(resume);
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> list(UUID userId) {
        return resumeRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(ResumeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeResponse get(UUID userId, UUID id) {
        return ResumeResponse.from(requireOwned(userId, id));
    }

    @Transactional
    public ResumeResponse update(UUID userId, UUID id, UpdateResumeRequest request) {
        Resume resume = requireOwned(userId, id);

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new JobPilotException(HttpStatus.BAD_REQUEST, "name: must not be blank");
            }
            resume.setName(request.name().trim());
        }
        if (request.versionLabel() != null) {
            resume.setVersionLabel(blankToNull(request.versionLabel()));
        }
        if (request.description() != null) {
            resume.setDescription(blankToNull(request.description()));
        }
        if (request.fileUrl() != null) {
            resume.setFileUrl(blankToNull(request.fileUrl()));
        }

        resumeRepository.save(resume);
        return ResumeResponse.from(resume);
    }

    /**
     * Deletes the resume. Applications that referenced it keep their row;
     * {@code applications.resume_id} is set to NULL by the FK (ON DELETE SET NULL).
     */
    @Transactional
    public void delete(UUID userId, UUID id) {
        resumeRepository.delete(requireOwned(userId, id));
    }

    private Resume requireOwned(UUID userId, UUID id) {
        return resumeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new JobPilotException(HttpStatus.NOT_FOUND, "Resume not found"));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
