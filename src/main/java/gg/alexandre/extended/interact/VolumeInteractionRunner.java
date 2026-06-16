package gg.alexandre.extended.interact;

import com.hypixel.hytale.builtin.triggervolumes.EntityTargetType;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerCondition;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.builtin.triggervolumes.manager.GroupEntry;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.builtin.triggervolumes.system.DelayedEffectScheduler;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import gg.alexandre.extended.effects.PressInteractionEffect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class VolumeInteractionRunner {

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    @Nonnull
    private static final DelayedEffectScheduler DELAYED_EFFECT_SCHEDULER = new DelayedEffectScheduler();

    public static void tickDelayed(@Nonnull Store<EntityStore> store) {
        if (!DELAYED_EFFECT_SCHEDULER.isEmpty()) {
            DELAYED_EFFECT_SCHEDULER.tick(System.nanoTime(), store);
        }
    }

    public static boolean fire(@Nonnull TriggerEventType eventType, @Nonnull Ref<EntityStore> entityRef,
                               @Nonnull UUID entityUuid, @Nonnull VolumeEntry volume,
                               @Nonnull TriggerVolumeManager manager, @Nonnull Store<EntityStore> store,
                               long nowNanos, boolean includeGroupEffects) {
        if (!conditionsPass(volume.getConditions(), eventType, entityRef, volume, store, null,
                "volume '" + volume.getId() + "'")) {
            return false;
        }

        fireEffects(eventType, entityRef, entityUuid, volume, store, nowNanos);
        if (includeGroupEffects) {
            fireGroupEffects(eventType, entityRef, entityUuid, volume, manager, store, nowNanos);
        }

        return true;
    }

    private static void fireEffects(@Nonnull TriggerEventType eventType, @Nonnull Ref<EntityStore> entityRef,
                                    @Nonnull UUID entityUuid, @Nonnull VolumeEntry volume,
                                    @Nonnull Store<EntityStore> store, long nowNanos) {
        fireVolumeEffects(eventType, entityRef, entityUuid, volume, store, nowNanos, null);
    }

    private static void fireVolumeEffects(@Nonnull TriggerEventType eventType, @Nonnull Ref<EntityStore> entityRef,
                                          @Nonnull UUID entityUuid, @Nonnull VolumeEntry volume,
                                          @Nonnull Store<EntityStore> store, long nowNanos,
                                          @Nullable List<VolumeEntry> spatialVolumes) {
        List<TriggerEffect> effects = volume.getEffects();
        for (int i = 0; i < effects.size(); i++) {
            TriggerEffect effect = effects.get(i);
            if (effect instanceof PressInteractionEffect || effect.getEventType() != eventType) {
                continue;
            }

            VolumeEntry.EffectEntityKey intervalKey = null;
            if (eventType == TriggerEventType.TICK && effect.getInterval() > 0.0f) {
                intervalKey = new VolumeEntry.EffectEntityKey(VolumeEntry.EffectBucket.VOLUME, i, entityUuid);
                Long lastFire = volume.getLastFireTimes().get(intervalKey);
                if (lastFire != null && ((double) (nowNanos - lastFire) / 1.0E9) < effect.getInterval()) {
                    continue;
                }
            }

            float totalDelay = volume.getActivationDelay() + effect.getDelay();
            if (totalDelay > 0.0f) {
                if (spatialVolumes == null) {
                    DELAYED_EFFECT_SCHEDULER.schedule(
                            effect, entityRef, entityUuid, eventType, volume, nowNanos, totalDelay
                    );
                } else {
                    DELAYED_EFFECT_SCHEDULER.schedule(
                            effect, entityRef, entityUuid, eventType, volume, nowNanos, totalDelay, spatialVolumes
                    );
                }
                if (intervalKey != null) {
                    volume.getLastFireTimes().put(intervalKey, nowNanos);
                }
                continue;
            }

            try {
                TriggerContext context = spatialVolumes == null
                        ? new TriggerContext(entityRef, store, eventType, volume)
                        : new TriggerContext(entityRef, store, eventType, volume, spatialVolumes);
                effect.execute(context);
                if (intervalKey != null) {
                    volume.getLastFireTimes().put(intervalKey, nowNanos);
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log(
                        "Error executing effect %s on volume '%s'",
                        effect.getClass().getSimpleName(),
                        volume.getId()
                );
            }
        }
    }

    private static void fireGroupEffects(@Nonnull TriggerEventType eventType, @Nonnull Ref<EntityStore> entityRef,
                                         @Nonnull UUID entityUuid, @Nonnull VolumeEntry volume,
                                         @Nonnull TriggerVolumeManager manager, @Nonnull Store<EntityStore> store,
                                         long nowNanos) {
        if (volume.getGroupId() == null) {
            return;
        }

        GroupEntry group = manager.getGroup(volume.getGroupId());
        if (group == null || !group.isEnabled()) {
            return;
        }

        if (!group.getTargetTypes().isEmpty()) {
            EntityTargetType required = store.getComponent(entityRef, PlayerRef.getComponentType()) != null
                    ? EntityTargetType.PLAYER
                    : EntityTargetType.NPC;
            if (!group.getTargetTypes().contains(required)) {
                return;
            }
        }

        ArrayList<VolumeEntry> spatialVolumes = new ArrayList<>();
        for (String memberId : group.getMemberVolumeIds()) {
            VolumeEntry member = manager.getVolume(memberId);
            if (member != null) {
                spatialVolumes.add(member);
            }
        }
        if (spatialVolumes.isEmpty()) {
            return;
        }

        if (!conditionsPass(group.getConditions(), eventType, entityRef, volume, store, spatialVolumes,
                "group '" + group.getId() + "' via volume '" + volume.getId() + "'")) {
            return;
        }

        List<TriggerEffect> effects = group.getEffects();
        for (int i = 0; i < effects.size(); i++) {
            TriggerEffect effect = effects.get(i);
            if (effect.getEventType() != eventType) {
                continue;
            }

            VolumeEntry.EffectEntityKey intervalKey = null;
            if (eventType == TriggerEventType.TICK && effect.getInterval() > 0.0f) {
                intervalKey = new VolumeEntry.EffectEntityKey(VolumeEntry.EffectBucket.GROUP, -(i + 1), entityUuid);
                Long lastFire = volume.getLastFireTimes().get(intervalKey);
                if (lastFire != null && ((double) (nowNanos - lastFire) / 1.0E9) < effect.getInterval()) {
                    continue;
                }
            }

            float totalDelay = volume.getActivationDelay() + effect.getDelay();
            if (totalDelay > 0.0f) {
                DELAYED_EFFECT_SCHEDULER.schedule(
                        effect, entityRef, entityUuid, eventType, volume, nowNanos, totalDelay, spatialVolumes
                );
                if (intervalKey != null) {
                    volume.getLastFireTimes().put(intervalKey, nowNanos);
                }
                continue;
            }

            try {
                effect.execute(new TriggerContext(entityRef, store, eventType, volume, spatialVolumes));
                if (intervalKey != null) {
                    volume.getLastFireTimes().put(intervalKey, nowNanos);
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log(
                        "Error executing group effect %s on group '%s' via volume '%s'",
                        effect.getClass().getSimpleName(),
                        group.getId(),
                        volume.getId()
                );
            }
        }

        for (VolumeEntry member : spatialVolumes) {
            if (!shouldFireMemberVolume(volume, member)) {
                continue;
            }

            fireVolumeEffects(eventType, entityRef, entityUuid, member, store, nowNanos, spatialVolumes);
        }
    }

    private static boolean shouldFireMemberVolume(@Nonnull VolumeEntry source, @Nonnull VolumeEntry member) {
        return !source.getId().equals(member.getId()) && member.isEnabled() && !member.isPendingDestroy();
    }

    private static boolean conditionsPass(@Nonnull List<TriggerCondition> conditions,
                                          @Nonnull TriggerEventType eventType,
                                          @Nonnull Ref<EntityStore> entityRef,
                                          @Nonnull VolumeEntry volume,
                                          @Nonnull Store<EntityStore> store,
                                          @Nullable List<VolumeEntry> spatialVolumes,
                                          @Nonnull String sourceLabel) {
        if (conditions.isEmpty()) {
            return true;
        }

        TriggerContext context;
        try {
            context = spatialVolumes == null
                    ? new TriggerContext(entityRef, store, eventType, volume)
                    : new TriggerContext(entityRef, store, eventType, volume, spatialVolumes);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Error creating trigger context for %s", sourceLabel);
            return false;
        }

        List<TriggerCondition> acceptedConditions = new ArrayList<>();
        for (TriggerCondition condition : conditions) {
            if (condition == null || condition.getEventType() != eventType) {
                continue;
            }

            try {
                if (!condition.test(context)) {
                    return false;
                }
                acceptedConditions.add(condition);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log(
                        "Error evaluating condition %s on %s",
                        condition.getClass().getSimpleName(),
                        sourceLabel
                );
                return false;
            }
        }

        for (TriggerCondition condition : acceptedConditions) {
            try {
                condition.applyOnAccept(context);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log(
                        "Error applying accepted condition %s on %s",
                        condition.getClass().getSimpleName(),
                        sourceLabel
                );
                return false;
            }
        }

        return true;
    }

}
