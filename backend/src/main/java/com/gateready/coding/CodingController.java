package com.gateready.coding;

import com.gateready.coding.CodingDtos.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class CodingController {
    private final CodingService coding;

    public CodingController(CodingService coding) {
        this.coding = coding;
    }

    @GetMapping("/topics/{topicId}/coding")
    RoundView round(@AuthenticationPrincipal UUID userId, @PathVariable UUID topicId) {
        return coding.round(userId, topicId);
    }

    @PostMapping("/coding/problems/{problemId}/submissions")
    SubmissionResult submit(@AuthenticationPrincipal UUID userId, @PathVariable UUID problemId, @RequestBody SubmitRequest body) {
        return coding.submit(userId, problemId, body.code(), body.mode());
    }
}
