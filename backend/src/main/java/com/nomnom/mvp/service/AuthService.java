package com.nomnom.mvp.service;

import com.nomnom.mvp.support.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final Map<UUID, DeviceSession> sessionsByInstallationId = new ConcurrentHashMap<>();
    private final Map<String, DeviceSession> sessionsByAccessToken = new ConcurrentHashMap<>();

    public DeviceSession registerDevice(UUID installationId, String deviceName, String appVersion) {
        DeviceSession existing = sessionsByInstallationId.get(installationId);
        if (existing != null) {
            DeviceSession refreshed = existing.refresh(deviceName, appVersion);
            sessionsByInstallationId.put(installationId, refreshed);
            sessionsByAccessToken.put(refreshed.accessToken(), refreshed);
            return refreshed;
        }

        DeviceSession created = new DeviceSession(
                UUID.randomUUID(),
                installationId,
                deviceName,
                appVersion,
                "dev_" + UUID.randomUUID(),
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
        sessionsByInstallationId.put(installationId, created);
        sessionsByAccessToken.put(created.accessToken(), created);
        return created;
    }

    public UUID requireUserId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing " + HttpHeaders.AUTHORIZATION + " header");
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authorization header must use Bearer token");
        }
        String accessToken = authorizationHeader.substring("Bearer ".length()).trim();
        DeviceSession session = sessionsByAccessToken.get(accessToken);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Access token is invalid or expired");
        }
        return session.userId();
    }

    public record DeviceSession(
            UUID userId,
            UUID installationId,
            String deviceName,
            String appVersion,
            String accessToken,
            Instant expiresAt
    ) {
        public DeviceSession refresh(String newDeviceName, String newAppVersion) {
            return new DeviceSession(userId, installationId, newDeviceName, newAppVersion, accessToken, Instant.now().plus(7, ChronoUnit.DAYS));
        }
    }
}
