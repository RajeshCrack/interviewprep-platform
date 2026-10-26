package com.gateready.engage;

import com.gateready.engage.BadgeService.BadgeView;
import com.gateready.engage.NotificationDao.Item;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class EngageController {
    public record NotificationList(int unread, List<Item> items) {}

    private final NotificationDao notifications;
    private final BadgeService badges;
    private final Clock clock;

    public EngageController(NotificationDao notifications, BadgeService badges, Clock clock) {
        this.notifications = notifications;
        this.badges = badges;
        this.clock = clock;
    }

    @GetMapping("/me/notifications")
    NotificationList list(@AuthenticationPrincipal UUID userId) {
        return new NotificationList(notifications.unread(userId), notifications.latest(userId, 20));
    }

    @PostMapping("/me/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@AuthenticationPrincipal UUID userId) {
        notifications.markAllRead(userId, clock.instant());
    }

    @GetMapping("/me/badges")
    List<BadgeView> badges(@AuthenticationPrincipal UUID userId) {
        return badges.badges(userId);
    }
}
