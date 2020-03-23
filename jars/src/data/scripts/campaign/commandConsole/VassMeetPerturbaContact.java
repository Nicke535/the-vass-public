package data.scripts.campaign.commandConsole;

import com.fs.starfarer.api.Global;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import static data.scripts.campaign.barEvents.VassPerturbaBaseEvent.VASS_PERTURBA_HAS_MET_CONTACT_KEY;

public class VassMeetPerturbaContact implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        Global.getSector().getMemoryWithoutUpdate().set(VASS_PERTURBA_HAS_MET_CONTACT_KEY, true);

        Console.showMessage("Memory flag for Perturba contact set succesfully.");
        return CommandResult.SUCCESS;
    }
}
