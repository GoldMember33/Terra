package com.dfsek.terra.addons.commands.locate_near;

import com.dfsek.terra.addons.manifest.api.AddonInitializer;
import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.command.CommandSender;
import com.dfsek.terra.api.entity.Entity;
import com.dfsek.terra.api.event.events.platform.CommandRegistrationEvent;
import com.dfsek.terra.api.event.functional.FunctionalEventHandler;
import com.dfsek.terra.api.inject.annotations.Inject;

import com.dfsek.terra.api.world.World;

import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.DefaultValue;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;


public class LocateNearCommandAddon implements AddonInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocateNearCommandAddon.class);

    @Inject
    private Platform platform;

    @Inject
    private BaseAddon addon;

    @Override
    public void initialize() {
        platform.getEventManager()
            .getHandler(FunctionalEventHandler.class)
            .register(addon, CommandRegistrationEvent.class)
            .then(event -> {
                CommandManager<CommandSender> commandManager = event.getCommandManager();
                //PaperCommandManager<Source> commandManager = PaperCommandManager
                //    .builder(PaperSimpleSenderMapper.simpleSenderMapper())
                //    .executionCoordinator(ExecutionCoordinator.<Source>builder().build())
                //    .buildOnEnable(); // Added missing semicolon here

                commandManager.command( // Changed 'manager' to 'commandManager'
                    commandManager.commandBuilder("locate_near", Description.of("Generate a list of the nearest biomes around you (radius = 5000) or inside a specific radius"))
                        //.senderType(PlayerSource.class)

                        // Argument 1: Radius (Optional, default 5000)
                        .optional("radius", IntegerParser.integerParser(100), DefaultValue.constant(5000))
                        // Argument 3: Step/Resolution (Optional, default 16)
                        .optional("step", IntegerParser.integerParser(1), DefaultValue.constant(16))
                        // Flag: Toggle 3D search (e.g., --3d or -3)
                        .flag(commandManager.flagBuilder("3d").withAliases("3").build()) // Changed 'manager' to 'commandManager'
                        // Flag: Auto resolution mode (e.g., --auto or -a)
                        .flag(commandManager.flagBuilder("auto").withAliases("a").build()) // Changed 'manager' to 'commandManager'
                        // Flag: Log the result message list to the server console (e.g., --log-console)
                        .flag(commandManager.flagBuilder("log-console").build()) // Changed 'manager' to 'commandManager'
                        .handler(context -> {

                            Entity sender = context.sender().getEntity().orElseThrow(
                                () -> new Error("Only entities can run this command."));
                            World world = sender.world();

                            int worldPackSeaLevel = 64;

                            // Fetch properties needed for the locator
                            int radius = context.get("radius");
                            boolean search3D = context.flags().hasFlag("3d");
                            boolean autoMode = context.flags().hasFlag("auto");
                            boolean logConsole = context.flags().hasFlag("log-console");

                            // 2. Determine Initial Step
                            // If Auto: Start at radius / 2 (very coarse check).
                            // If Manual: Use provided step.
                            int stepArg = context.get("step");
                            int currentStep = autoMode ? Integer.highestOneBit(radius - 1) : stepArg;

                            //context.sender(); // Note: Ensure valid cast depending on your PlayerSource implementation

                            // Notify player
                            String modeMsg = autoMode ? " (Auto Mode)" : " (Step: " + currentStep + ")";
                            context.sender().sendMessage(
                                "Searching for biomes within " + radius + " blocks" + modeMsg + "...");

                            CompletableFuture<List<String>> results = NearestBiomesLocator.getNearestBiomesListAsync(
                                world.getBiomeProvider(),
                                world,
                                sender.position().getFloorX(),
                                sender.position().getFloorZ(),
                                radius,
                                currentStep,
                                search3D,
                                worldPackSeaLevel
                            );

                            results.thenAccept(rs -> {
                                rs.forEach(context.sender()::sendMessage);

                            });

                            if(logConsole) {
                                results.thenAccept(rs -> {
                                    rs.forEach(LOGGER::info);
                                });
                            }
                        })
                        .permission("terra.locate_near")
                )
                .appendSuggestionMapper(suggestion -> suggestion);
            });
    }
}