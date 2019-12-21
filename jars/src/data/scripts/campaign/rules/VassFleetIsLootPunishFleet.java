package data.scripts.campaign.rules;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Checks if the entity being interacted with is a loot punishing fleet
 * @author Nicke535
 */
public class VassFleetIsLootPunishFleet extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        CampaignFleetAPI vassFleet;
        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
            vassFleet = (CampaignFleetAPI) dialog.getInteractionTarget();
        } else {
            return false;
        }

        if (vassFleet.getMemoryWithoutUpdate().get("$vass_loot_punish_fleet") instanceof Boolean) {
            return (Boolean)vassFleet.getMemoryWithoutUpdate().get("$vass_loot_punish_fleet");
        } else {
            return false;
        }
    }
}