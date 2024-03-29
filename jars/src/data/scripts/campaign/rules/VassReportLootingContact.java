package data.scripts.campaign.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;

import java.util.List;
import java.util.Map;

/**
 * Makes the Vass Hostile to the player, as they've had a relapse
 * Also reset the counter of the "player has sold ships" thingy, as that now gets its 'payback'
 * @author Nicke535
 */
public class VassReportLootingContact extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        CampaignFleetAPI vassFleet;
        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
            vassFleet = (CampaignFleetAPI) dialog.getInteractionTarget();
        } else {
            return false;
        }

        if (Global.getSector().getPlayerFaction().isAtWorst("vass", RepLevel.INHOSPITABLE)) {
            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addPara("Relations with the Vass worsened to Hostile", Misc.getRelColor(-RepLevel.HOSTILE.getMin()), "Hostile");
            Global.getSector().getPlayerFaction().setRelationship("vass", RepLevel.HOSTILE);
            dialog.getTextPanel().setFontInsignia();
        }
        VassFamilyTrackerPlugin.resetSoldShipsListener();

        vassFleet.getMemory().set("$vass_loot_punish_fleet", false);
        return true;
    }

}