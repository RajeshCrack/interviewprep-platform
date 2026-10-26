package com.gateready.mcq;

import com.gateready.mcq.McqDtos.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class McqController {
    private final McqService mcq;

    public McqController(McqService mcq) {
        this.mcq = mcq;
    }

    @PostMapping("/topics/{topicId}/mcq/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    AttemptView start(@AuthenticationPrincipal UUID userId, @PathVariable UUID topicId) {
        return mcq.start(userId, topicId);
    }

    @GetMapping("/mcq/attempts/{id}")
    AttemptView get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return mcq.view(userId, id);
    }

    @PutMapping("/mcq/attempts/{id}/answers/{position}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void answer(@AuthenticationPrincipal UUID userId, @PathVariable UUID id, @PathVariable int position,
                @RequestBody AnswerRequest body) {
        mcq.saveAnswer(userId, id, position, body.selected());
    }

    @PostMapping("/mcq/attempts/{id}/submit")
    ResultView submit(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                      @RequestBody(required = false) @Valid SubmitRequest body) {
        return mcq.submit(userId, id, body == null ? null : body.answers());
    }

    @GetMapping("/mcq/attempts/{id}/result")
    ResultView result(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return mcq.result(userId, id);
    }
}
