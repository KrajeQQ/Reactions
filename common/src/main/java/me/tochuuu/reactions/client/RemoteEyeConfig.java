package me.tochuuu.reactions.client;

import java.util.UUID;

public record RemoteEyeConfig(
    UUID playerId,
    int entityId,
    int leftEyeX,
    int leftEyeY,
    int rightEyeX,
    int rightEyeY,
    int eyelidColorX,
    int eyelidColorY,
    int eyeWidth,
    int eyeHeight
) {
}
