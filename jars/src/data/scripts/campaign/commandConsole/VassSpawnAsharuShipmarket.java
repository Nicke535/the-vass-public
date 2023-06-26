package data.scripts.campaign.commandConsole;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyInformationEventIntel;
import data.scripts.campaign.barEvents.VassPerturbaGetShipSubmarketEvent;
import data.scripts.campaign.familyInformationFactors.VassFirstPerturbaContactMeetingFactor;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import static data.scripts.campaign.barEvents.VassPerturbaBaseEvent.VASS_PERTURBA_HAS_MET_CONTACT_KEY;

public class VassSpawnAsharuShipmarket implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }


        boolean added = false;
        for (MarketAPI market : Misc.getMarketsInLocation(Global.getSector().getPlayerFleet().getContainingLocation())) {
            if (market.getName().contains("Asharu")) {
                if (market.hasSubmarket(VassPerturbaGetShipSubmarketEvent.SUBMARKET_ID)) {
                    Console.showMessage("Asharu already has a submarket for Vass ships!");
                    return CommandResult.ERROR;
                } else {
                    added = true;
                    market.addSubmarket(VassPerturbaGetShipSubmarketEvent.SUBMARKET_ID);
                }
                break;
            }
        }
        if (!added) {
            Console.showMessage("Asharu does not exist in the current system! Either the market has ceased to exist or you are not in the Corvus system.");
            return CommandResult.ERROR;
        }

        Global.getSector().getPlayerFaction().setRelationship("vass", RepLevel.WELCOMING);

        Console.showMessage("Spawned a Perturba ship submarket on Asharu, and set Vass relations to Welcoming.");
        return CommandResult.SUCCESS;
    }
}
