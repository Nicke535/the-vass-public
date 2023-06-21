package data.scripts.campaign.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;

import java.util.List;
import java.util.Map;

/**
 * Checks if the player has sold Vass ships without proper permission
 * @author Nicke535
 */
public class VassPlayerHasSoldVassShips extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        if ((VassFamilyTrackerPlugin.hasSoldVassShipMinor() && !VassFamilyTrackerPlugin.playerAllowedToSellMinor())
            || (VassFamilyTrackerPlugin.hasSoldVassShipMajor() && !VassFamilyTrackerPlugin.playerAllowedToSellMajor())) {
            return true;
        }

        return false;
    }

}