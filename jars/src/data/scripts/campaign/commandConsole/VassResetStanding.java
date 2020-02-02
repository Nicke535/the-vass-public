package data.scripts.campaign.commandConsole;

import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class VassResetStanding implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        for (VassUtils.VASS_FAMILY family : VassUtils.VASS_FAMILY.values()) {
            VassFamilyTrackerPlugin.modifyRelationToFamily(family, -VassFamilyTrackerPlugin.getRelationToFamily(family));
        }

        Console.showMessage("Reset all Vass Family relations succesfully!");
        return CommandResult.SUCCESS;
    }
}
