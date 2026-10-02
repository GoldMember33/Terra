package com.dfsek.terra.bukkit.nms;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.attribute.AmbientAdditionsSettings;
import net.minecraft.world.attribute.AmbientMoodSettings;
import net.minecraft.world.attribute.AmbientSounds;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.bukkit.nms.config.VanillaBiomeProperties;


public class NMSBiomeInjector {

    public static <T> Optional<Holder.Reference<T>> getEntry(Registry<T> registry, Identifier identifier) {
        return registry.get(identifier);
    }

    public static Biome createBiome(Biome vanilla, VanillaBiomeProperties vanillaBiomeProperties)
    throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Biome.BiomeBuilder builder = new Biome.BiomeBuilder();

        BiomeSpecialEffects vanillaEffects = vanilla.getSpecialEffects();

        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder();

        effects.waterColor(Objects.requireNonNullElse(vanillaBiomeProperties.getWaterColor(), vanillaEffects.waterColor()))
            .grassColorModifier(
                Objects.requireNonNullElse(vanillaBiomeProperties.getGrassColorModifier(), vanillaEffects.grassColorModifier()));

        if(vanillaBiomeProperties.getGrassColor() == null) {
            vanillaEffects.grassColorOverride().ifPresent(effects::grassColorOverride);
        } else {
            effects.grassColorOverride(vanillaBiomeProperties.getGrassColor());
        }

        if(vanillaBiomeProperties.getFoliageColor() == null) {
            vanillaEffects.foliageColorOverride().ifPresent(effects::foliageColorOverride);
        } else {
            effects.foliageColorOverride(vanillaBiomeProperties.getFoliageColor());
        }

        if(vanillaBiomeProperties.getDryFoliageColor() == null) {
            vanillaEffects.dryFoliageColorOverride().ifPresent(effects::dryFoliageColorOverride);
        } else {
            effects.dryFoliageColorOverride(vanillaBiomeProperties.getDryFoliageColor());
        }

        builder.putAttributes(vanilla.getAttributes());

        if(vanillaBiomeProperties.getFogColor() != null) {
            builder.setAttribute(EnvironmentAttributes.FOG_COLOR, vanillaBiomeProperties.getFogColor());
        }

        if(vanillaBiomeProperties.getWaterFogColor() != null) {
            builder.setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, vanillaBiomeProperties.getWaterFogColor());
        }

        if(vanillaBiomeProperties.getSkyColor() != null) {
            builder.setAttribute(EnvironmentAttributes.SKY_COLOR, vanillaBiomeProperties.getSkyColor());
        }

        if(vanillaBiomeProperties.getMusicVolume() != null) {
            builder.setAttribute(EnvironmentAttributes.MUSIC_VOLUME, vanillaBiomeProperties.getMusicVolume());
        }

        if(vanillaBiomeProperties.getParticleConfig() != null) {
            builder.setAttribute(EnvironmentAttributes.AMBIENT_PARTICLES, List.of(vanillaBiomeProperties.getParticleConfig()));
        }

        if(vanillaBiomeProperties.getLoopSound() != null ||
           vanillaBiomeProperties.getMoodSound() != null ||
           vanillaBiomeProperties.getAdditionsSound() != null) {
            AmbientSounds vanillaSounds = vanilla.getAttributes()
                                                 .applyModifier(EnvironmentAttributes.AMBIENT_SOUNDS, AmbientSounds.EMPTY);

            Optional<Holder<SoundEvent>> loop = vanillaBiomeProperties.getLoopSound() == null
                                                ? vanillaSounds.loop()
                                                : RegistryFetcher.soundEventRegistry()
                                                                 .get(vanillaBiomeProperties.getLoopSound().location())
                                                                 .map(reference -> (Holder<SoundEvent>) reference);

            Optional<AmbientMoodSettings> mood = vanillaBiomeProperties.getMoodSound() == null
                                                 ? vanillaSounds.mood()
                                                 : Optional.of(vanillaBiomeProperties.getMoodSound());

            List<AmbientAdditionsSettings> additions = vanillaBiomeProperties.getAdditionsSound() == null
                                                       ? vanillaSounds.additions()
                                                       : List.of(vanillaBiomeProperties.getAdditionsSound());

            builder.setAttribute(EnvironmentAttributes.AMBIENT_SOUNDS, new AmbientSounds(loop, mood, additions));
        }

        if(vanillaBiomeProperties.getMusic() != null) {
            BackgroundMusic vanillaMusic = vanilla.getAttributes()
                                                  .applyModifier(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.EMPTY);

            builder.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC,
                new BackgroundMusic(Optional.of(vanillaBiomeProperties.getMusic()), vanillaMusic.creativeMusic(),
                    vanillaMusic.underwaterMusic()));
        }

        builder.hasPrecipitation(Objects.requireNonNullElse(vanillaBiomeProperties.getPrecipitation(), vanilla.hasPrecipitation()));

        builder.temperature(Objects.requireNonNullElse(vanillaBiomeProperties.getTemperature(), vanilla.getBaseTemperature()));

        builder.downfall(Objects.requireNonNullElse(vanillaBiomeProperties.getDownfall(), vanilla.climateSettings.downfall()));

        builder.temperatureAdjustment(
            Objects.requireNonNullElse(vanillaBiomeProperties.getTemperatureModifier(), vanilla.climateSettings.temperatureModifier()));

        builder.mobSpawnSettings(Objects.requireNonNullElse(vanillaBiomeProperties.getSpawnSettings(), vanilla.getMobSettings()));

        return builder
            .specialEffects(effects.build())
            .generationSettings(new BiomeGenerationSettings.PlainBuilder().build())
            .build();
    }

    public static String createBiomeID(ConfigPack pack, com.dfsek.terra.api.registry.key.RegistryKey biomeID) {
        return pack.getID()
                   .toLowerCase() + "/" + biomeID.getNamespace().toLowerCase(Locale.ROOT) + "/" + biomeID.getID().toLowerCase(Locale.ROOT);
    }
}
