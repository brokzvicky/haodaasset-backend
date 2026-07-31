package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AttendanceRecordDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps track of every admin browser tab currently watching the Attendance
 * Management page (via Server-Sent Events) and pushes new punches to all
 * of them the moment they're saved — this is what makes the live feed
 * update automatically without the frontend having to poll. Same pattern
 * as {@link NotificationService}'s SSE emitter list for Haoda Pulse.
 */
@Service
public class AttendanceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AttendanceEventPublisher.class);

    /** No expiry timeout (0L) — the frontend keeps one connection open indefinitely and we prune dead ones on write failure. */
    private static final long EMITTER_TIMEOUT = 0L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));

        try {
            // Initial "connected" ping so the frontend's EventSource fires onopen promptly.
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ex) {
            emitters.remove(emitter);
        }

        log.info("New attendance stream subscriber connected. Active subscribers: {}", emitters.size());
        return emitter;
    }

    public void publish(AttendanceRecordDTO record) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("attendance").data(record));
            } catch (IOException | IllegalStateException ex) {
                emitter.complete();
                emitters.remove(emitter);
            }
        }
    }
}
