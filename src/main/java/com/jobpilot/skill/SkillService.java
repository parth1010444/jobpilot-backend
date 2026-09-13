package com.jobpilot.skill;

import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.skill.dto.CreateSkillRequest;
import com.jobpilot.skill.dto.SkillResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> list(UUID userId) {
        return skillRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(SkillResponse::from)
                .toList();
    }

    @Transactional
    public SkillResponse create(UUID userId, CreateSkillRequest request) {
        String name = normalizeName(request.name());
        if (skillRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw duplicateSkill();
        }

        Skill skill = new Skill(userId, name);
        try {
            skillRepository.saveAndFlush(skill);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateSkill(ex);
        }
        return SkillResponse.from(skill);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        skillRepository.delete(requireOwned(userId, id));
    }

    private Skill requireOwned(UUID userId, UUID id) {
        return skillRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new JobPilotException(HttpStatus.NOT_FOUND, "Skill not found"));
    }

    /**
     * Skill names are stored trimmed and case-folded so unique(user_id, name)
     * matches unique(user_id, lower(name)).
     */
    static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static JobPilotException duplicateSkill() {
        return new JobPilotException(HttpStatus.CONFLICT, "Skill already exists");
    }

    private static JobPilotException duplicateSkill(Throwable cause) {
        return new JobPilotException(HttpStatus.CONFLICT, "Skill already exists", cause);
    }
}
