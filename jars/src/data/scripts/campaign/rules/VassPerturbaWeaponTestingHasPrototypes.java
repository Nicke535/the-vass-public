package data.scripts.campaign.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingEvent;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingIntel;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Checks if the player has any Perturba Prototypes in their fleet
 * @author Nicke535
 */
public class VassPerturbaWeaponTestingHasPrototypes extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        //Weapons on ships
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            for (String slot : member.getVariant().getFittedWeaponSlots()) {
                for (String id : VassPerturbaWeaponTestingEvent.TESTABLE_WEAPON_IDS.getItems()) {
                    if (member.getVariant().getWeaponId(slot).equals(id)) {
                        return true;
                    }
                }
            }
        }

        //Loose weapons
        for (String id : VassPerturbaWeaponTestingEvent.TESTABLE_WEAPON_IDS.getItems()) {
            if (playerFleet.getCargo().getNumWeapons(id) > 0) {
                return true;
            }
        }

        return true;
    }

}