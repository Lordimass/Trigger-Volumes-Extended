package gg.alexandre.extended.interact;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class VolumeInteraction extends SimpleInstantInteraction {

    public static final String INTERACTION_ID = "*VolumeInteraction";
    public static final RootInteraction DEFAULT_ROOT = new RootInteraction(INTERACTION_ID, INTERACTION_ID);

    @Nonnull
    public static final BuilderCodec<VolumeInteraction> CODEC = BuilderCodec.builder(
                    VolumeInteraction.class,
                    VolumeInteraction::new,
                    SimpleInstantInteraction.CODEC
            )
            .build();

    public VolumeInteraction(@Nonnull String id) {
        super(id);
    }

    protected VolumeInteraction() {
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> playerRef = context.getEntity();
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (commandBuffer == null || !playerRef.isValid() || targetRef == null || !targetRef.isValid()) {
            fail(context);
            return;
        }

        VolumeInteractionComponent component = commandBuffer.getComponent(
                targetRef, VolumeInteractionComponent.getComponentType()
        );
        if (component == null) {
            fail(context);
            return;
        }

        TriggerVolumeManager manager = commandBuffer.getResource(TriggerVolumesPlugin.get().getManagerResourceType());
        if (manager == null) {
            fail(context);
            return;
        }

        VolumeEntry volume = manager.getVolume(component.getVolumeId());
        if (volume == null || !volume.isEnabled() || volume.isPendingDestroy()) {
            fail(context);
            return;
        }

        UUIDComponent uuid = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuid == null) {
            fail(context);
            return;
        }

        long nowNanos = System.nanoTime();
        if (volume.isOnCooldown(uuid.getUuid(), nowNanos)) {
            fail(context);
            return;
        }

        boolean fired = VolumeInteractionRunner.fire(
                component.getEventType(),
                playerRef,
                uuid.getUuid(),
                volume,
                manager,
                commandBuffer.getStore(),
                nowNanos,
                component.shouldIncludeGroupEffects()
        );
        if (!fired) {
            fail(context);
            return;
        }

        volume.recordActivation(uuid.getUuid(), nowNanos);
    }

    private static void fail(@Nonnull InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }

}
