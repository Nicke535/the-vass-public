package data.scripts.campaign.commandConsole;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.campaign.rules.VassFamilyPowerIsAtLeast;
import data.scripts.utils.VassUtils;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import static data.scripts.campaign.barEvents.VassPerturbaBaseEvent.VASS_PERTURBA_HAS_MET_CONTACT_KEY;

public class VassMaxRelationsWithPerturba implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA, 999f);
        Global.getSector().getFaction("vass").setRelationship(Factions.PLAYER, RepLevel.COOPERATIVE);

        Console.showMessage("Relations with Perturba and the Vass maxed out.");
        return CommandResult.SUCCESS;
    }
}
