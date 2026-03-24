package com.nomnom.mvp.api;

import com.nomnom.mvp.service.AuthService;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/device")
    public DeviceAuthResponse registerDevice(@RequestBody DeviceAuthRequest request) {
        AuthService.DeviceSession session = authService.registerDevice(request.installationId(), request.deviceName(), request.appVersion());
        return new DeviceAuthResponse(session.userId(), session.accessToken(), session.expiresAt());
    }

    public record DeviceAuthRequest(
            @NotNull UUID installationId,
            String deviceName,
            String appVersion
    ) {
    }

    public record DeviceAuthResponse(
            UUID userId,
            String accessToken,
            Instant expiresAt
    ) {
    }
}
