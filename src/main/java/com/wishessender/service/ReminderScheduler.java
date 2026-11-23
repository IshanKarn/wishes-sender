package com.wishessender.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Background job: every minute it looks for events that are today, or
 * "remind_days_before" days away, and creates in-app notifications.
 * The UNIQUE key on notifications + INSERT IGNORE makes sure we never remind twice.
 */
@Service
public class ReminderScheduler {

    private final JdbcTemplate jdbc;

    public ReminderScheduler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelay = 5000, fixedRate = 60000)   // 5s after start, then every 60s
    public int checkReminders() {
        String sql =
            "INSERT IGNORE INTO notifications (user_id, event_id, for_date, days_before, message) " +
            "SELECT v.user_id, v.id, v.next_date, v.days_left, " +
            "       CASE WHEN v.days_left = 0 " +
            "            THEN CONCAT('Today: ', v.title, ' of ', v.connection_name, '!') " +
            "            ELSE CONCAT('In ', v.days_left, ' day(s): ', v.title, ' of ', v.connection_name) END " +
            "FROM v_event_next v " +
            "JOIN users u ON u.id = v.user_id " +
            "WHERE v.days_left = 0 OR v.days_left = u.remind_days_before";
        int created = jdbc.update(sql);
        if (created > 0) {
            System.out.println("Reminder scheduler created " + created + " notification(s)");
        }
        return created;
    }
}
