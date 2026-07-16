package gg.alexandre.extended;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import gg.alexandre.extended.commands.RunTriggerVolumeCommand;
import gg.alexandre.extended.display.ShapeColorDisplayResource;
import gg.alexandre.extended.display.ShapeColorDisplaySystem;
import gg.alexandre.extended.effects.*;
import gg.alexandre.extended.interact.*;
import gg.alexandre.extended.util.EnumReflectionUtil;
import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import java.util.List;

public class TriggerExtendedPlugin extends JavaPlugin {

    private static final String PRESS_EVENT = "PRESS";
    private static final String COMMAND_EVENT = "COMMAND";

    public TriggerExtendedPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        setupTriggerEvents();
        setupCommands();
        setupEffects();
        setupInteraction();
    }

    private void setupCommands() {
        getCommandRegistry().registerCommand(new RunTriggerVolumeCommand());
    }

    private void setupTriggerEvents() {
        try {
            Unsafe unsafe = EnumReflectionUtil.getUnsafe();
            EnumReflectionUtil.addTriggerEvent(unsafe, PRESS_EVENT);
            EnumReflectionUtil.addTriggerEvent(unsafe, COMMAND_EVENT);
            EnumReflectionUtil.patchTriggerEffectCodec(unsafe);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register extended trigger events", e);
        }
    }

    private void setupEffects() {
        TriggerVolumesPlugin.get().registerEffectType("Command", CommandEffect.class, CommandEffect.CODEC);
        TriggerVolumesPlugin.get().registerEffectType("WorldTeleport", WorldTPEffect.class, WorldTPEffect.CODEC);
        TriggerVolumesPlugin.get().registerEffectType(
                "DestroyOtherVolume", DestroyOtherVolumeEffect.class, DestroyOtherVolumeEffect.CODEC
        );
        TriggerVolumesPlugin.get().registerEffectType(
                "PressInteraction", PressInteractionEffect.class, PressInteractionEffect.CODEC
        );
        TriggerVolumesPlugin.get().registerEffectType(
                "ShapeColor", ShapeColorEffect.class, ShapeColorEffect.CODEC
        );
    }

    private void setupInteraction() {
        this.getCodecRegistry(Interaction.CODEC).register(
                "VolumeInteraction", VolumeInteraction.class, VolumeInteraction.CODEC
        );
        Interaction.getAssetStore().loadAssets(
                "TriggerVolumesExtended:TriggerVolumesExtended",
                List.of(new VolumeInteraction(VolumeInteraction.INTERACTION_ID))
        );
        RootInteraction.getAssetStore().loadAssets(
                "TriggerVolumesExtended:TriggerVolumesExtended",
                List.of(VolumeInteraction.DEFAULT_ROOT)
        );

        VolumeInteractionComponent.setComponentType(
                this.getEntityStoreRegistry().registerComponent(
                        VolumeInteractionComponent.class, VolumeInteractionComponent::new
                )
        );
        VolumeInteractionResource.setResourceType(
                this.getEntityStoreRegistry().registerResource(
                        VolumeInteractionResource.class, VolumeInteractionResource::new
                )
        );
        this.getEntityStoreRegistry().registerSystem(new VolumeInteractionCleanupSystem());
        this.getEntityStoreRegistry().registerSystem(new VolumeInteractionSystem());
        ShapeColorDisplayResource.setResourceType(
                this.getEntityStoreRegistry().registerResource(
                        ShapeColorDisplayResource.class, ShapeColorDisplayResource::new
                )
        );
        this.getEntityStoreRegistry().registerSystem(new ShapeColorDisplaySystem());
    }

}
