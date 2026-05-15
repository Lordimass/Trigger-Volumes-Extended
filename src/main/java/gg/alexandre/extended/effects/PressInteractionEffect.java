package gg.alexandre.extended.effects;

import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

public class PressInteractionEffect extends TriggerEffect {

    private static final String DEFAULT_HINT = "server.interactionHints.generic";
    private static final String DEFAULT_EVENT = "PRESS";

    @Nonnull
    public static final BuilderCodec<PressInteractionEffect> CODEC = BuilderCodec.builder(
                    PressInteractionEffect.class, PressInteractionEffect::new, BASE_CODEC
            )
            .append(
                    new KeyedCodec<>("Hint", Codec.STRING),
                    (e, v) -> e.hint = v,
                    (e) -> e.hint
            ).add()
            .append(
                    new KeyedCodec<>("IncludeGroupEffects", Codec.BOOLEAN),
                    (e, v) -> e.includeGroupEffects = v,
                    (e) -> e.includeGroupEffects
            ).add()
            .append(
                    new KeyedCodec<>("HitboxPadding", Codec.FLOAT),
                    (e, v) -> e.hitboxPadding = v == null ? 0 : Math.max(0.0f, v),
                    (e) -> e.hitboxPadding > 0.0f ? e.hitboxPadding : null
            ).add()
            .build();

    private String hint = DEFAULT_HINT;
    private boolean includeGroupEffects = true;
    private float hitboxPadding = 0.0f;

    @Override
    public void execute(@Nonnull TriggerContext context) {

    }

    @Nonnull
    public String getHint() {
        return hint == null || hint.isBlank() ? DEFAULT_HINT : hint;
    }

    public boolean shouldIncludeGroupEffects() {
        return includeGroupEffects;
    }

    public float getHitboxPadding() {
        return Math.max(0.0f, hitboxPadding);
    }

    @Override
    public void setEventType(@Nonnull TriggerEventType eventType) {
        // Force PRESS to be the default event type
        if (this.getEventType() == null) {
            eventType = TriggerEventType.valueOf(DEFAULT_EVENT);
        }

        super.setEventType(eventType);
    }
}
