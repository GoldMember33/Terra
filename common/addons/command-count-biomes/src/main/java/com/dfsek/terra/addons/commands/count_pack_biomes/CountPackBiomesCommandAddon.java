package com.dfsek.terra.addons.commands.count_pack_biomes;

import com.dfsek.terra.addons.manifest.api.AddonInitializer;
import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.command.CommandSender;
import com.dfsek.terra.api.command.arguments.RegistryArgument;
import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.api.event.events.platform.CommandRegistrationEvent;
import com.dfsek.terra.api.event.functional.FunctionalEventHandler;
import com.dfsek.terra.api.inject.annotations.Inject;

import com.dfsek.terra.api.registry.Registry;

import com.dfsek.terra.api.util.reflection.TypeKey;
import com.dfsek.terra.api.world.biome.Biome;

import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;


public class CountPackBiomesCommandAddon implements AddonInitializer {

    private static final Logger logger = LoggerFactory.getLogger(CountPackBiomesCommandAddon.class);

    @Inject
    private Platform platform;

    @Inject
    private BaseAddon addon;

    private Function<CommandContext<CommandSender>, Registry<ConfigPack>> getConfigPackRegistryFunction() {
        return _ -> platform.getConfigRegistry();
    }

    @Override
    public void initialize() {
        platform.getEventManager()
            .getHandler(FunctionalEventHandler.class)
            .register(addon, CommandRegistrationEvent.class)
            .then(event -> {
                CommandManager<CommandSender> commandManager = event.getCommandManager();

                commandManager.command(
                    commandManager.commandBuilder("countpackbiomes", Description.of("Count the total amount of biomes registered in a specific Terra pack."))

                    // Argument 1: Terra pack ID
                    .literal("pack", Description.of("The Terra pack ID to count biomes for."))

                        .argument(RegistryArgument.builder("pack",
                            getConfigPackRegistryFunction(),
                            TypeKey.of(ConfigPack.class))

                            // Applying tab completion suggestions to pack argument.
                            .suggestionProvider(SuggestionProvider.blockingStrings((context, input) -> {

                                String typed = input.lastRemainingToken().toLowerCase(Locale.ROOT);
                                List<String> suggestions = new ArrayList<>();

                                for(ConfigPack configPack : platform.getConfigRegistry().entries()) {
                                    String id = configPack.getID();
                                    if(id.toLowerCase(Locale.ROOT).startsWith(typed)) {
                                        suggestions.add(id);
                                    }
                                }

                                return suggestions;
                            }))
                        )

                    // Flag: Log the result message list to the server console (e.g., --log-console)
                    .flag(commandManager.flagBuilder("log-console").build())

                    .handler(context -> {

                        ConfigPack biomeConfigPack = context.get("pack");
                        if(biomeConfigPack == null) {
                            context.sender().sendMessage("Please, provide a pack ID to retrieve the biome count.");
                            return;
                        }

                        Collection<Biome> biomes = Collections.checkedCollection(new ArrayList<>(), Biome.class);
                        biomes.addAll((Collection<? extends Biome>) biomeConfigPack.getBiomeProvider().getBiomes());

                        int biomesCount = new HashSet<>(biomes).size();

                        String packID = biomeConfigPack.getID();

                        String message = "Biomes count for pack: " + packID + " : " + biomesCount + " entries.";

                        context.sender().sendMessage(message);

                        boolean logConsole = context.flags().hasFlag("log-console");

                        if(logConsole) {
                            logger.info(message);
                        }

                    })
                    .permission("terra.count_pack_biomes")
                );
            });
    }
}
