package data.scripts.campaign.commandConsole;

import com.fs.starfarer.api.Global;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.campaign.barEvents.VassPerturbaBaseEvent;
import data.scripts.campaign.barEvents.VassPerturbaWeaponContractEvent;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingEvent;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingIntel;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class VassResetCompletely implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        //Resets all standings and power levels
        VassFamilyTrackerPlugin.reinitializeFamilyPower();
        VassFamilyTrackerPlugin.reinitializeFamilyRelations();
        VassFamilyTrackerPlugin.resetSoldShipsListener();

        //Resets almost all memory keys
        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaBaseEvent.VASS_PERTURBA_HAS_MET_CONTACT_KEY);
        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaBaseEvent.VASS_PERTURBA_CONTACT_KEY);
        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaBaseEvent.CURRENT_EVENT_ALLOWED_KEY);
        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaBaseEvent.LAST_MARKET_ALLOW_CHECK_KEY);

        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaWeaponContractEvent.VASS_PERTURBA_WEAPON_CONTRACT_KEY);

        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaWeaponTestingEvent.COLLATERAL_MEM_KEY);
        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaWeaponTestingIntel.MEM_KEY_PROTOTYPE_DATA);
        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaWeaponTestingIntel.MEM_KEY_PROTOTYPE_IN_COMBAT_SCORE_HANDLER);

        Global.getSector().getMemoryWithoutUpdate().unset("$vass_firstTimeVassShipLooted");

        Console.showMessage("Most info about the Vass families successfully scrubbed from memory!");
        return CommandResult.SUCCESS;
    }
}
