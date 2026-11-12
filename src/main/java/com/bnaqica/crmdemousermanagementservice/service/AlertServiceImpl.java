package com.bnaqica.crmdemousermanagementservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Service;

/** Logs the alert with the {@code ALERT} marker and counts it in {@code ums.alerts}; monitoring turns both into notifications. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private static final Marker ALERT = MarkerFactory.getMarker("ALERT");

    private final MeterRegistry meters;

    @Override
    public void raise(String type, String message) {
        log.error(ALERT, "[{}] {}", type, message);
        meters.counter("ums.alerts", "type", type).increment();
    }
}
