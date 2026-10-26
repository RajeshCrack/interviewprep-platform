package com.gateready.goal;

import com.gateready.user.UserView;

public record DashboardView(UserView user, GoalDto goal, int todayMinutes, int streak, int xp) {}
