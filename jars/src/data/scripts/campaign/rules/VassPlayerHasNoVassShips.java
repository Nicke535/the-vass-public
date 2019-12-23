package data.scripts.campaign.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Checks if the player has no vass ships or fighters in their fleet
 * @author Nicke535
 */
public class VassPlayerHasNoVassShips extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            if (member.getHullId().startsWith("vass_")) {
                return false;
            }
            for (String wing : member.getVariant().getWings()) {
                if (wing.startsWith("vass_")) {
                    return false;
                }
            }
        }
        for (CargoAPI.CargoItemQuantity<String> fighter : Global.getSector().getPlayerFleet().getCargo().getFighters()) {
            if (fighter.getItem().startsWith("vass_") && fighter.getCount() > 0) {
                return false;
            }
        }

        return true;
    }

}