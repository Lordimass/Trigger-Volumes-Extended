package gg.alexandre.extended.effects;

import com.hypixel.hytale.builtin.teleport.components.TeleportHistory;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3d;

import javax.annotation.Nonnull;

public class WorldTPEffect extends TriggerEffect {
    @Nonnull
    public static final BuilderCodec<WorldTPEffect> CODEC = BuilderCodec.builder(
            WorldTPEffect.class, WorldTPEffect::new, BASE_CODEC
        )
        .append(
            new KeyedCodec<>("World", Codec.STRING, false),
            (e, v) -> e.world = v,
            (e) -> e.world
        ).add()
        .build();

    private String world = "";

    @Override
    public void execute(@NonNullDecl TriggerContext context) {
        Ref<EntityStore> ref = context.getEntityRef();
        Store<EntityStore> store = context.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        assert player != null;
        World currentWorld = player.getWorld();
        assert currentWorld != null;

        World targetWorld = Universe.get().getWorld(world);
        if (targetWorld == null) return;
        ISpawnProvider spawnProvider = targetWorld.getWorldConfig().getSpawnProvider();
        assert spawnProvider != null;
        Transform spawnPoint = spawnProvider.getSpawnPoint(ref, store);
        if (spawnPoint != null) {
            TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
            HeadRotation headRotationComponent = store.getComponent(ref, HeadRotation.getComponentType());
            if (transformComponent != null && headRotationComponent != null) {
                Vector3d previousPos = new Vector3d(transformComponent.getPosition());
                Rotation3f previousRotation = new Rotation3f(headRotationComponent.getRotation());
                TeleportHistory teleportHistoryComponent = store.ensureAndGetComponent(ref, TeleportHistory.getComponentType());

                teleportHistoryComponent.append(currentWorld, previousPos, previousRotation, "World " + targetWorld.getName());
            }
            Teleport teleportComponent = Teleport.createForPlayer(targetWorld, spawnPoint);
            store.addComponent(ref, Teleport.getComponentType(), teleportComponent);
        }
    }
}
