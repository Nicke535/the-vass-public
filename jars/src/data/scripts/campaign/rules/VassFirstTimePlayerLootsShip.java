package data.scripts.campaign.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;

import java.util.List;
import java.util.Map;

public class VassFirstTimePlayerLootsShip extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        if (Global.getSector().getMemoryWithoutUpdate().get("$vass_firstTimeVassShipLooted") instanceof Boolean) {
            return (boolean)Global.getSector().getMemoryWithoutUpdate().get("$vass_firstTimeVassShipLooted");
        } else {
            return false;
        }
    }

}