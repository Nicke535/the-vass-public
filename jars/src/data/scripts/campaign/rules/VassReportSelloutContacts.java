package data.scripts.campaign.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassCampaignUtils;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;

import java.util.List;
import java.util.Map;

/**
 * Triggered when the player sells out their weapons dealer instead of taking the rep hit for owning Vass ships.
 * As such, this doesn't do the normal resetting of sold ships listener: that's a separate problem!
 * @author Nicke535
 */
public class VassReportSelloutContacts extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        CampaignFleetAPI vassFleet;
        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
            vassFleet = (CampaignFleetAPI) dialog.getInteractionTarget();
        } else {
            return false;
        }

        if (VassFamilyTrackerPlugin.getSelloutablePersons() != null) {
            for (PersonAPI target : VassFamilyTrackerPlugin.getSelloutablePersons()) {
                VassFamilyTrackerPlugin.sellOutPerson(target);
            }
        }

        Global.getSector().getPlayerStats().addXP(VassCampaignUtils.getVassMissionXP(VassCampaignUtils.MissionImportance.TRIVIAL, VassUtils.VASS_FAMILY.PERTURBA, false), dialog.getTextPanel(), true);
        vassFleet.getMemory().set("$vass_fleet_should_escape", true);
        vassFleet.getMemory().set("$vass_loot_punish_fleet", false);
        return true;
    }

}