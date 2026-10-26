package com.gateready.user;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_stats")
public class UserStats {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    private int streak;
    @Column(name = "last_goal_date")
    private LocalDate lastGoalDate;
    private int xp;

    protected UserStats() {}

    public UserStats(UUID userId) { this.userId = userId; }

    public UUID getUserId() { return userId; }
    public int getStreak() { return streak; }
    public LocalDate getLastGoalDate() { return lastGoalDate; }
    public int getXp() { return xp; }
    public void setStreak(int streak) { this.streak = streak; }
    public void setLastGoalDate(LocalDate d) { this.lastGoalDate = d; }
    public void addXp(int amount) { this.xp += amount; }
}
