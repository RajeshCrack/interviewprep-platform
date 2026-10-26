package com.gateready.goal;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudyLogDao {
    private final JdbcTemplate jdbc;

    public StudyLogDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int minutesOn(UUID userId, LocalDate day) {
        Integer v = jdbc.query("SELECT minutes FROM study_logs WHERE user_id = ? AND day = ?",
                rs -> rs.next() ? rs.getInt(1) : 0, userId, day);
        return v == null ? 0 : v;
    }

    /** Adds minutes to the student's total for the day. */
    public void add(UUID userId, LocalDate day, int minutes) {
        int updated = jdbc.update("UPDATE study_logs SET minutes = minutes + ? WHERE user_id = ? AND day = ?",
                minutes, userId, day);
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO study_logs (user_id, day, minutes) VALUES (?, ?, ?)", userId, day, minutes);
            } catch (DuplicateKeyException race) {
                jdbc.update("UPDATE study_logs SET minutes = minutes + ? WHERE user_id = ? AND day = ?",
                        minutes, userId, day);
            }
        }
    }
}
