package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.Task;
import com.vikkash.assetmanagementv1.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Runs the Haoda Pulse "Reminder Rules": generates task notifications
 * 7 days before due, 3 days before, 1 day before, on the due date, and
 * every day a task remains overdue — until the task is completed or
 * cancelled. "Immediately after creation" and "when completed" are
 * handled synchronously in TaskService instead of here.
 */
@Service
public class PulseReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PulseReminderScheduler.class);
    private static final Set<Long> REMINDER_DAY_MARKS = Set.of(7L, 3L, 1L, 0L);

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    public PulseReminderScheduler(TaskRepository taskRepository, NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
    }

    /** Runs daily at 07:45 server time, ahead of the other reminder scans. */
    @Scheduled(cron = "0 45 7 * * *")
    public void scheduledScan() {
        int created = scanTaskDueDates();
        if (created > 0) log.info("Pulse reminder scan: {} task notification(s) created.", created);
    }

    @Transactional
    public int scanTaskDueDates() {
        LocalDate today = LocalDate.now();
        int created = 0;

        List<Task> active = taskRepository.findByStatusNotOrderByDueDateAsc("Completed");
        for (Task task : active) {
            if ("Cancelled".equalsIgnoreCase(task.getStatus())) continue;
            if (task.getDueDate() == null) continue;

            long daysLeft = ChronoUnit.DAYS.between(today, task.getDueDate());
            String type;
            String title;
            String priority = task.getPriority();

            if (daysLeft < 0) {
                type = "OVERDUE_TASK";
                title = "Overdue: " + task.getTitle();
            } else if (daysLeft == 0) {
                type = "DUE_TODAY";
                title = "Due today: " + task.getTitle();
            } else if (REMINDER_DAY_MARKS.contains(daysLeft)) {
                type = "UPCOMING_TASK";
                title = "Due in " + daysLeft + " day" + (daysLeft == 1 ? "" : "s") + ": " + task.getTitle();
            } else {
                continue; // not a reminder day
            }

            String relatedId = String.valueOf(task.getId());
            if (notificationService.alreadyCreatedToday(type, "TASK", relatedId)) continue;

            NotificationService.Recipients recipients = new NotificationService.Recipients();
            recipients.assigneeEmail = task.getAssigneeEmail();

            String desc = (task.getDescription() != null ? task.getDescription() + " · " : "")
                    + "Due " + task.getDueDate()
                    + (task.getAssigneeName() != null ? " · Assigned to " + task.getAssigneeName() : "");

            notificationService.create(type, "Task", priority, title, desc, "TASK", relatedId, task.getDueDate(), recipients);
            created++;
        }
        return created;
    }
}
