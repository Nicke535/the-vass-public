package data.scripts.campaign.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassCampaignUtils;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.campaign.barEvents.VassPerturbaBaseEvent;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingEvent;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingIntel;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Finishes the Perturba Weapon Testing quest, by handing in weapons and adding the right text to the dialog
 * Not triggered if all prototypes are lost: see VassPerturbaWeaponTestingHandinBad for that
 *
 * @author Nicke535
 */
public class VassPerturbaWeaponTestingHandin extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        TextPanelAPI text = dialog.getTextPanel();
        Color h = Misc.getHighlightColor();

        //Prototype removal - text
        text.addPara("Lost all perturba prototypes", Misc.getNegativeHighlightColor());

        //Credit payment
        int creditsEarned = Math.round((Integer) Global.getSector().getMemoryWithoutUpdate().get(VassPerturbaWeaponTestingEvent.COLLATERAL_MEM_KEY)
                * MathUtils.getRandomNumberInRange(VassPerturbaWeaponTestingEvent.REWARD_PERCENTAGE_MIN, VassPerturbaWeaponTestingEvent.REWARD_PERCENTAGE_MAX));
        text.addPara("Gained " + creditsEarned + " credits", Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), "" + creditsEarned);
        playerFleet.getCargo().getCredits().add(creditsEarned);

        //Relations modification
        float currentPerturbaRelations = VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA);
        if (currentPerturbaRelations < VassPerturbaWeaponTestingEvent.RELATIONS_MAX_PERTURBA) {
            float relationsBoost = Math.min(VassPerturbaWeaponTestingEvent.RELATIONS_MAX_PERTURBA - currentPerturbaRelations, VassPerturbaWeaponTestingEvent.RELATIONS_BOOST_PERTURBA);
            VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA, relationsBoost);
            text.addPara("Relations with Perturba improved by " + Math.round(relationsBoost), Misc.getPositiveHighlightColor(), h, "Perturba", "" + Math.round(relationsBoost));
        }
        if (Global.getSector().getPlayerFaction().getRelationshipLevel("vass").isAtBest(RepLevel.WELCOMING)) {
            text.addPara("Relations with the Vass Families improved by " + Math.round(VassPerturbaWeaponTestingEvent.RELATIONS_BOOST_VASS * 100),
                    Misc.getPositiveHighlightColor(), h, "Vass Families", "" + VassPerturbaWeaponTestingEvent.RELATIONS_BOOST_VASS);
            Global.getSector().getPlayerFaction().adjustRelationship("vass", VassPerturbaWeaponTestingEvent.RELATIONS_BOOST_VASS);
        }
        VassFamilyTrackerPlugin.modifyPowerOfFamily(VassUtils.VASS_FAMILY.PERTURBA, VassPerturbaWeaponTestingEvent.FAMILY_POWER_BOOST_PERTURBA);

        //XP Gain
        Global.getSector().getPlayerStats().addXP(VassCampaignUtils.getVassMissionXP(VassCampaignUtils.MissionImportance.STANDARD, VassUtils.VASS_FAMILY.PERTURBA, false), text, true);

        //Prototype removal and cleanup
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(VassPerturbaWeaponTestingIntel.class)) {
            if (intel instanceof VassPerturbaWeaponTestingIntel) {
                VassPerturbaWeaponTestingIntel intelConverted = (VassPerturbaWeaponTestingIntel) intel;
                intelConverted.removeAllPrototypes();
                intelConverted.endImmediately();
            }
        }

        //Run the random event picker from the base event script, in case a special event should appear
        VassPerturbaBaseEvent.checkAndSetAllowedEvent();

        //Unlocks so new Perturba events may spawn again
        Global.getSector().getMemoryWithoutUpdate().set(VassPerturbaBaseEvent.PERTURBA_EVENTS_BLOCKED_KEY, false);

        return true;
    }

}