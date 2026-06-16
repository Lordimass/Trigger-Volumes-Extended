package gg.alexandre.extended.util;

import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerCondition;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderField;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.*;

public class EnumReflectionUtil {

    @Nonnull
    public static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    public static void addTriggerEvent(@Nonnull Unsafe unsafe, @Nonnull String name)
            throws ReflectiveOperationException {
        for (TriggerEventType eventType : TriggerEventType.values()) {
            if (eventType.name().equals(name)) {
                return;
            }
        }

        TriggerEventType[] values = TriggerEventType.values();
        TriggerEventType added = newTriggerEvent(unsafe, name, values.length);
        TriggerEventType[] updatedValues = Arrays.copyOf(values, values.length + 1);
        updatedValues[values.length] = added;

        Field valuesField = findEnumValuesField(TriggerEventType.class);
        unsafe.putObject(unsafe.staticFieldBase(valuesField), unsafe.staticFieldOffset(valuesField), updatedValues);
        clearEnumCache(unsafe, TriggerEventType.class);
    }

    @Nonnull
    private static TriggerEventType newTriggerEvent(@Nonnull Unsafe unsafe, @Nonnull String name, int ordinal)
            throws ReflectiveOperationException {
        TriggerEventType eventType = (TriggerEventType) unsafe.allocateInstance(TriggerEventType.class);

        Field nameField = Enum.class.getDeclaredField("name");
        unsafe.putObject(eventType, unsafe.objectFieldOffset(nameField), name);

        Field ordinalField = Enum.class.getDeclaredField("ordinal");
        unsafe.putInt(eventType, unsafe.objectFieldOffset(ordinalField), ordinal);

        return eventType;
    }

    @Nonnull
    private static Field findEnumValuesField(@Nonnull Class<?> enumClass) throws NoSuchFieldException {
        for (Field field : enumClass.getDeclaredFields()) {
            if (field.getType().isArray() && field.getType().getComponentType() == enumClass) {
                return field;
            }
        }

        throw new NoSuchFieldException(enumClass.getName() + ".$VALUES");
    }

    private static void clearEnumCache(@Nonnull Unsafe unsafe, @Nonnull Class<?> enumClass)
            throws ReflectiveOperationException {
        Field enumConstants = Class.class.getDeclaredField("enumConstants");
        unsafe.putObject(enumClass, unsafe.objectFieldOffset(enumConstants), null);

        Field enumConstantDirectory = Class.class.getDeclaredField("enumConstantDirectory");
        unsafe.putObject(enumClass, unsafe.objectFieldOffset(enumConstantDirectory), null);
    }

    public static void patchTriggerEffectCodec(@Nonnull Unsafe unsafe) throws ReflectiveOperationException {
        Set<Codec<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        patchTriggerEventCodecs(unsafe, TriggerEffect.BASE_CODEC, visited);
        patchTriggerEventCodecs(unsafe, TriggerCondition.BASE_CODEC, visited);
    }

    private static void patchTriggerEventCodecs(@Nonnull Unsafe unsafe, @Nonnull Codec<?> codec,
                                                @Nonnull Set<Codec<?>> visited) throws ReflectiveOperationException {
        if (!visited.add(codec)) {
            return;
        }

        if (codec instanceof EnumCodec<?> enumCodec) {
            patchTriggerEventCodec(unsafe, enumCodec);
            return;
        }

        if (codec instanceof BuilderCodec<?> builderCodec) {
            BuilderCodec<?> parent = builderCodec.getParent();
            if (parent != null) {
                patchTriggerEventCodecs(unsafe, parent, visited);
            }

            for (List<? extends BuilderField<?, ?>> fields : builderCodec.getEntries().values()) {
                for (BuilderField<?, ?> field : fields) {
                    KeyedCodec<?> keyedCodec = field.getCodec();
                    Codec<?> childCodec = keyedCodec.getChildCodec();
                    patchTriggerEventCodecs(unsafe, childCodec, visited);
                }
            }
        }
    }

    private static void patchTriggerEventCodec(@Nonnull Unsafe unsafe, @Nonnull EnumCodec<?> codec)
            throws ReflectiveOperationException {
        Field clazzField = EnumCodec.class.getDeclaredField("clazz");
        Object clazz = unsafe.getObject(codec, unsafe.objectFieldOffset(clazzField));
        if (clazz != TriggerEventType.class) {
            return;
        }

        TriggerEventType[] values = TriggerEventType.values();

        Field enumConstantsField = EnumCodec.class.getDeclaredField("enumConstants");
        unsafe.putObject(codec, unsafe.objectFieldOffset(enumConstantsField), values);

        Field enumKeysField = EnumCodec.class.getDeclaredField("enumKeys");
        unsafe.putObject(codec, unsafe.objectFieldOffset(enumKeysField), triggerEventKeys(values));

        Field documentationField = EnumCodec.class.getDeclaredField("documentation");
        EnumMap<TriggerEventType, String> documentation = new EnumMap<>(TriggerEventType.class);
        unsafe.putObject(codec, unsafe.objectFieldOffset(documentationField), documentation);
    }

    @Nonnull
    private static String[] triggerEventKeys(@Nonnull TriggerEventType[] values) {
        String[] keys = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            keys[i] = values[i].name();
        }
        return keys;
    }

}
