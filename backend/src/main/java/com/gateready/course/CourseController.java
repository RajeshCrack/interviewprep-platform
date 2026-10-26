package com.gateready.course;

import com.gateready.course.CourseDtos.*;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseController {
    private final CourseService courses;

    public CourseController(CourseService courses) {
        this.courses = courses;
    }

    @GetMapping("/courses")
    List<CourseSummary> list() {
        return courses.courses();
    }

    @GetMapping("/courses/{slug}/roadmap")
    Roadmap roadmap(@AuthenticationPrincipal UUID userId, @PathVariable String slug) {
        return courses.roadmap(userId, slug);
    }

    @GetMapping("/topics/{id}")
    TopicDetail topic(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return courses.topic(userId, id);
    }
}
