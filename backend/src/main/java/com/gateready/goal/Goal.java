package com.gateready.goal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "goals")
public class Goal {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "hours_per_day")
    private BigDecimal hoursPerDay;
    @Column(name = "reminder_time")
    private String reminderTime;
    @Enumerated(EnumType.STRING)
    private Channel channel;

    protected Goal() {}

    public Goal(UUID userId, double hoursPerDay, String reminderTime, Channel channel) {
        this.userId = userId;
        update(hoursPerDay, reminderTime, channel);
    }

    public void update(double hoursPerDay, String reminderTime, Channel channel) {
        this.hoursPerDay = BigDecimal.valueOf(hoursPerDay);
        this.reminderTime = reminderTime;
        this.channel = channel;
    }

    public double getHoursPerDay() { return hoursPerDay.doubleValue(); }
    public String getReminderTime() { return reminderTime; }
    public Channel getChannel() { return channel; }

    public int goalMinutes() {
        return (int) Math.round(getHoursPerDay() * 60);
    }
}
