package gg.alexandre.extended.interact;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Phobia;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import gg.alexandre.extended.effects.PressInteractionEffect;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class VolumeInteractionSystem extends TickingSystem<EntityStore> {

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        VolumeInteractionRunner.tickDelayed(store);

        TriggerVolumeManager manager = store.getResource(TriggerVolumesPlugin.get().getManagerResourceType());
        VolumeInteractionResource resource = store.getResource(VolumeInteractionResource.getResourceType());

        Set<String> volumes = new HashSet<>();
        for (VolumeEntry volume : manager.getVolumes()) {
            if (!volume.isEnabled() || volume.isPendingDestroy()) {
                removeIfPresent(store, resource, volume.getId());
                continue;
            }

            PressInteractionEffect effect = findEffect(volume);
            if (effect == null) {
                continue;
            }

            volumes.add(volume.getId());
            sync(store, resource, volume, effect);
        }

        resource.removeMissing(volumes, ref -> store.removeEntity(ref, RemoveReason.REMOVE));
    }

    @Nullable
    private static PressInteractionEffect findEffect(@Nonnull VolumeEntry volume) {
        for (var effect : volume.getEffects()) {
            if (effect instanceof PressInteractionEffect interactionEffect) {
                return interactionEffect;
            }
        }
        return null;
    }

    private static void removeIfPresent(
            @Nonnull Store<EntityStore> store,
            @Nonnull VolumeInteractionResource resource,
            @Nonnull String volumeId
    ) {
        Ref<EntityStore> existing = resource.get(volumeId);
        if (existing != null) {
            store.removeEntity(existing, RemoveReason.REMOVE);
        }
        resource.remove(volumeId);
    }

    private static void sync(
            @Nonnull Store<EntityStore> store,
            @Nonnull VolumeInteractionResource resource,
            @Nonnull VolumeEntry volume,
            @Nonnull PressInteractionEffect effect
    ) {
        Ref<EntityStore> existing = resource.get(volume.getId());
        InteractionGeometry geometry = InteractionGeometry.from(volume, effect.getHitboxPadding());

        if (existing == null) {
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            addComponents(holder, store, volume, effect, geometry);
            Ref<EntityStore> created = store.addEntity(holder, AddReason.SPAWN);
            if (created != null) {
                resource.put(volume.getId(), created);
            }
            return;
        }

        if (!needsUpdate(store, existing, volume, effect, geometry)) {
            return;
        }

        putComponents(store, existing, volume, effect, geometry);
    }

    private static boolean needsUpdate(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull VolumeEntry volume,
            @Nonnull PressInteractionEffect effect,
            @Nonnull InteractionGeometry geometry
    ) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || !sameVector(transform.getPosition(), geometry.center)) {
            return true;
        }

        BoundingBox boundingBox = store.getComponent(ref, BoundingBox.getComponentType());
        if (boundingBox == null || !sameBox(boundingBox.getBoundingBox(), geometry.localBox)) {
            return true;
        }

        Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
        if (interactions == null
                || !VolumeInteraction.INTERACTION_ID.equals(interactions.getInteractionId(InteractionType.Use))
                || !effect.getHint().equals(interactions.getInteractionHint())) {
            return true;
        }

        VolumeInteractionComponent component = store.getComponent(ref, VolumeInteractionComponent.getComponentType());
        return component == null
                || !volume.getId().equals(component.getVolumeId())
                || effect.getEventType() != component.getEventType()
                || effect.shouldIncludeGroupEffects() != component.shouldIncludeGroupEffects();
    }

    private static boolean sameBox(@Nonnull Box left, @Nonnull Box right) {
        return sameVector(left.min, right.min) && sameVector(left.max, right.max);
    }

    private static boolean sameVector(@Nonnull Vector3dc left, @Nonnull Vector3dc right) {
        return Double.compare(left.x(), right.x()) == 0
                && Double.compare(left.y(), right.y()) == 0
                && Double.compare(left.z(), right.z()) == 0;
    }

    private static void addComponents(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull Store<EntityStore> store,
            @Nonnull VolumeEntry volume,
            @Nonnull PressInteractionEffect effect,
            @Nonnull InteractionGeometry geometry
    ) {
        holder.addComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());
        holder.addComponent(
                NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId())
        );
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(geometry.center, Rotation3f.IDENTITY));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(geometry.localBox));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(createHitboxOnlyModel(geometry.localBox)));
        holder.addComponent(Interactable.getComponentType(), Interactable.INSTANCE);
        holder.addComponent(Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        holder.addComponent(Interactions.getComponentType(), createInteractions(effect));
        holder.addComponent(VolumeInteractionComponent.getComponentType(), createComponent(volume, effect));

        holder.ensureComponent(EntityModule.get().getVisibleComponentType());
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
    }

    private static void putComponents(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull VolumeEntry volume,
            @Nonnull PressInteractionEffect effect,
            @Nonnull InteractionGeometry geometry
    ) {
        store.putComponent(
                ref,
                TransformComponent.getComponentType(),
                new TransformComponent(geometry.center, Rotation3f.IDENTITY)
        );
        store.putComponent(ref, BoundingBox.getComponentType(), new BoundingBox(geometry.localBox));
        store.putComponent(
                ref,
                ModelComponent.getComponentType(),
                new ModelComponent(createHitboxOnlyModel(geometry.localBox))
        );
        store.putComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);
        store.tryRemoveComponent(ref, Intangible.getComponentType());
        store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        store.putComponent(ref, Interactions.getComponentType(), createInteractions(effect));
        store.putComponent(ref, VolumeInteractionComponent.getComponentType(), createComponent(volume, effect));
    }

    @Nonnull
    private static Interactions createInteractions(@Nonnull PressInteractionEffect effect) {
        Interactions interactions = new Interactions();
        interactions.setInteractionId(InteractionType.Use, VolumeInteraction.INTERACTION_ID);
        interactions.setInteractionHint(effect.getHint());
        return interactions;
    }

    @Nonnull
    private static VolumeInteractionComponent createComponent(
            @Nonnull VolumeEntry volume,
            @Nonnull PressInteractionEffect effect
    ) {
        return new VolumeInteractionComponent(
                volume.getId(),
                effect.getEventType(),
                effect.shouldIncludeGroupEffects()
        );
    }

    @Nonnull
    private static Model createHitboxOnlyModel(@Nonnull Box localBox) {
        return new Model(
                null,
                1.0f,
                null,
                null,
                localBox,
                null,
                null,
                null,
                null,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Phobia.None,
                null
        );
    }

    private record InteractionGeometry(@Nonnull Vector3d center, @Nonnull Box localBox) {
        @Nonnull
        private static InteractionGeometry from(@Nonnull VolumeEntry volume, float padding) {
            Vector3d min = new Vector3d();
            Vector3d max = new Vector3d();
            volume.getShape().getWorldAABB(volume.getPosition(), min, max);

            min.sub(padding, padding, padding);
            max.add(padding, padding, padding);

            Vector3d center = new Vector3d(
                    (min.x + max.x) * 0.5,
                    (min.y + max.y) * 0.5,
                    (min.z + max.z) * 0.5
            );
            Box localBox = new Box(
                    new Vector3d(min).sub(center),
                    new Vector3d(max).sub(center)
            );
            return new InteractionGeometry(center, localBox);
        }
    }

}
