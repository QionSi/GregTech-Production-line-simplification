package com.qionsi.simplification.command;

import java.util.List;

import net.minecraft.command.ICommandSender;

import com.qionsi.simplification.machine.MTEPetrochemicalComplex;
import com.qionsi.simplification.recipe.RecipeManager;

import gregtech.commands.GTBaseCommand;

/**
 * {@code /simplification <reload|recipes|structure|info>} - development helper for the Petrochemical Complex.
 * <p>
 * Everything this command touches is data read from the mod's config directory, so it can be changed and applied
 * without restarting the game. Java code cannot be reloaded by Minecraft 1.7.10, so a change to behaviour still needs
 * a restart - but the structure shape and the recipe numbers no longer live in code.
 */
public class SimplificationCommand extends GTBaseCommand {

    public SimplificationCommand() {
        super("simp");
    }

    @Override
    public String getCommandName() {
        return "simplification";
    }

    @Override
    public int getRequiredPermissionLevel() {
        // Level 0 so the local development loop works without opening the world to LAN; on a server this leaves the
        // command available to everyone, which is fine because it can only re-read files that are already there.
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                sendChatToPlayer(sender, describeStructure(RecipeManager.reloadStructure()));
                sendChatToPlayer(sender, RecipeManager.describe(RecipeManager.reload()));
            }
            case "recipes" -> sendChatToPlayer(sender, RecipeManager.describe(RecipeManager.reload()));
            case "structure" -> {
                sendChatToPlayer(sender, describeStructure(RecipeManager.reloadStructure()));
                for (String line : MTEPetrochemicalComplex.currentBlueprint()
                    .describe()
                    .split("\n")) {
                    sendChatToPlayer(sender, line);
                }
            }
            case "info" -> {
                sendChatToPlayer(sender, "Petrochemical Complex: " + RecipeManager.recipeCount() + " recipes loaded.");
                sendChatToPlayer(sender, "Recipe file:    " + RecipeManager.recipeFile());
                sendChatToPlayer(sender, "Structure file: " + RecipeManager.structureFile());
            }
            default -> sendHelp(sender);
        }
    }

    private static String describeStructure(List<String> problems) {
        if (problems.isEmpty()) {
            var shape = MTEPetrochemicalComplex.currentBlueprint();
            return "\u00a7aStructure reloaded: " + shape.width()
                + " wide x "
                + shape.height()
                + " tall x "
                + shape.depth()
                + " deep. Machines that are already built re-check themselves within a tick or two.";
        }
        StringBuilder message = new StringBuilder("\u00a7cThe structure file was not applied. \u00a77Problems:");
        for (String problem : problems) {
            message.append("\n \u00a7e")
                .append(problem);
        }
        return message.toString();
    }

    private static void sendHelp(ICommandSender sender) {
        sendChatToPlayer(sender, "/simplification reload - re-read the recipe and structure files and apply them now");
        sendChatToPlayer(sender, "/simplification recipes - re-read only the recipe file");
        sendChatToPlayer(sender, "/simplification structure - re-read only the structure file and print the shape");
        sendChatToPlayer(sender, "/simplification info - show the file locations and the recipe count");
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload", "recipes", "structure", "info");
        }
        return null;
    }
}
