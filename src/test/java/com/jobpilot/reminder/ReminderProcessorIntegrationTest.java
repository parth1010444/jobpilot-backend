package com.jobpilot.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.notification.NotificationRepository;
import com.jobpilot.notification.NotificationStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReminderProcessorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReminderProcessor reminderProcessor;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void dueReminderCreatesNotificationAndMarksProcessed() throws Exception {
        String token = register("reminder-due@example.com");
        String appId = createApplication(token);
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);

        MvcResult createResult = mockMvc.perform(post("/api/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationId":"%s",
                                  "type":"INTERVIEW_PREPARATION",
                                  "title":"Prepare for interview",
                                  "description":"Review system design notes",
                                  "scheduledAt":"%s"
                                }
                                """.formatted(appId, past)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reminderId = UUID.fromString(
                objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText()
        );

        int processed = reminderProcessor.processDueReminders();
        assertThat(processed).isGreaterThanOrEqualTo(1);

        Reminder reminder = reminderRepository.findById(reminderId).orElseThrow();
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PROCESSED);
        assertThat(reminder.getCompletedAt()).isNotNull();

        assertThat(notificationRepository.countByReminderId(reminderId)).isEqualTo(1);
        var notification = notificationRepository.findByReminderId(reminderId).orElseThrow();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        assertThat(notification.getTitle()).isEqualTo("Prepare for interview");

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reminderId").value(reminderId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("SENT"));
    }

    @Test
    void processingSameDueReminderTwiceCreatesOnlyOneNotification() throws Exception {
        String token = register("reminder-idempotent@example.com");
        Instant past = Instant.now().minus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);

        MvcResult createResult = mockMvc.perform(post("/api/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"CUSTOM",
                                  "title":"Idempotent check",
                                  "scheduledAt":"%s"
                                }
                                """.formatted(past)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reminderId = UUID.fromString(
                objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText()
        );

        assertThat(reminderProcessor.processDueReminders()).isGreaterThanOrEqualTo(1);
        assertThat(reminderProcessor.processDueReminders()).isEqualTo(0);
        // Explicit second claim attempt also no-ops
        assertThat(reminderProcessor.processOne(reminderId, Instant.now())).isFalse();

        assertThat(notificationRepository.countByReminderId(reminderId)).isEqualTo(1);
        Reminder reminder = reminderRepository.findById(reminderId).orElseThrow();
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PROCESSED);
    }

    @Test
    void futureReminderIsNotProcessed() throws Exception {
        String token = register("reminder-future@example.com");
        Instant future = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        MvcResult createResult = mockMvc.perform(post("/api/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"CUSTOM",
                                  "title":"Not yet",
                                  "scheduledAt":"%s"
                                }
                                """.formatted(future)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reminderId = UUID.fromString(
                objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText()
        );

        reminderProcessor.processDueReminders();
        assertThat(reminderRepository.findById(reminderId).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.PENDING);
        assertThat(notificationRepository.countByReminderId(reminderId)).isZero();
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"Processor User"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createApplication(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Acme","jobTitle":"Engineer"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
