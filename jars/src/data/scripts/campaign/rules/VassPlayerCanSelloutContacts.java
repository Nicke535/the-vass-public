package data.scripts.campaign.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;

import java.util.List;
import java.util.Map;

/**
 * Checks if the player has any arms dealer contacts they can sell out for selling Vass ships.
 * @author Nicke535
 */
public class VassPlayerCanSelloutContacts extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        if (VassFamilyTrackerPlugin.getSelloutablePersons() != null) {
            return !VassFamilyTrackerPlugin.getSelloutablePersons().isEmpty();
        }

        return false;
    }

}