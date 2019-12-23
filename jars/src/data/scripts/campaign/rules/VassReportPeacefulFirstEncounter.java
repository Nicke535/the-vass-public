package data.scripts.campaign.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Makes the Vass Inhospitable to the player, since they actually listened to their demands
 * Also flag that the other fleet should flee to a jump point rather than re-engage the player
 * @author Nicke535
 */
public class VassReportPeacefulFirstEncounter extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        CampaignFleetAPI vassFleet;
        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
            vassFleet = (CampaignFleetAPI) dialog.getInteractionTarget();
        } else {
            return false;
        }

        Global.getSector().getPlayerFaction().setRelationship("vass", RepLevel.INHOSPITABLE);
        vassFleet.getMemory().set("$vass_fleet_should_escape", true);

        return true;
    }
}
