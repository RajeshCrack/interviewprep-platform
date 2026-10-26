package com.gateready.goal;

import com.gateready.common.ApiException;
import com.gateready.user.UserRepository;
import com.gateready.user.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Every handler takes the user id from the verified token, never from the request. */
@RestController
public class MeController {
    public record StudyLogRequest(
            @Min(value = 1, message = "Log at least 1 minute.")
            @Max(value = 240, message = "Log at most 240 minutes at a time.") int minutes) {}

    private final ProgressService progress;
    private final UserRepository users;

    public MeController(ProgressService progress, UserRepository users) {
        this.progress = progress;
        this.users = users;
    }

    @GetMapping("/me")
    Map<String, UserView> me(@AuthenticationPrincipal UUID userId) {
        return Map.of("user", users.findById(userId)
                .map(UserView::of)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in.")));
    }

    @GetMapping("/dashboard")
    DashboardView dashboard(@AuthenticationPrincipal UUID userId) {
        return progress.dashboard(userId);
    }

    @PutMapping("/me/goal")
    GoalDto saveGoal(@AuthenticationPrincipal UUID userId, @Valid @RequestBody GoalDto goal) {
        return progress.saveGoal(userId, goal);
    }

    @PostMapping("/me/study-log")
    DashboardView log(@AuthenticationPrincipal UUID userId, @Valid @RequestBody StudyLogRequest req) {
        return progress.logStudy(userId, req.minutes());
    }
}
