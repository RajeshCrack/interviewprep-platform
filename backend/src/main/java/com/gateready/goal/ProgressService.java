package com.gateready.goal;

import com.gateready.common.ApiException;
import com.gateready.user.User;
import com.gateready.user.UserRepository;
import com.gateready.user.UserStats;
import com.gateready.user.UserStatsRepository;
import com.gateready.user.UserView;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {
    static final int GOAL_BONUS_XP = 50;
    static final int MINUTES_PER_XP = 5;

    private final UserRepository users;
    private final GoalRepository goals;
    private final UserStatsRepository stats;
    private final StudyLogDao logs;
    private final Clock clock;

    public ProgressService(UserRepository users, GoalRepository goals, UserStatsRepository stats,
                           StudyLogDao logs, Clock clock) {
        this.users = users;
        this.goals = goals;
        this.stats = stats;
        this.logs = logs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(UUID userId) {
        return view(userId, today());
    }

    @Transactional
    public GoalDto saveGoal(UUID userId, GoalDto dto) {
        Goal goal = goalOf(userId);
        goal.update(dto.hoursPerDay(), dto.reminderTime(), dto.channel());
        return GoalDto.of(goals.save(goal));
    }

    @Transactional
    public DashboardView logStudy(UUID userId, int minutes) {
        LocalDate today = today();
        int goalMinutes = goalOf(userId).goalMinutes();
        int before = logs.minutesOn(userId, today);
        logs.add(userId, today, minutes);
        UserStats s = statsOf(userId);
        s.addXp(minutes / MINUTES_PER_XP);
        if (before < goalMinutes && before + minutes >= goalMinutes) {
            s.setStreak(StreakCalculator.afterGoalMet(s.getLastGoalDate(), today, s.getStreak()));
            s.setLastGoalDate(today);
            s.addXp(GOAL_BONUS_XP);
        }
        stats.save(s);
        return view(userId, today);
    }

    private DashboardView view(UUID userId, LocalDate today) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in."));
        UserStats s = statsOf(userId);
        return new DashboardView(UserView.of(user), GoalDto.of(goalOf(userId)), logs.minutesOn(userId, today),
                StreakCalculator.current(s.getLastGoalDate(), today, s.getStreak()), s.getXp());
    }

    private Goal goalOf(UUID userId) {
        return goals.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Goal not found."));
    }

    private UserStats statsOf(UUID userId) {
        return stats.findById(userId).orElseGet(() -> new UserStats(userId));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
