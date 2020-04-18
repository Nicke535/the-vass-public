package data.scripts.campaign.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Transfers all Vass ships to the other fleet in the interaction. Also removes all vass fighters from the player fleet
 * Loosely based on the Cabal interaction of similar effect, by Dark Revenant
 * @author Nicke535
 */
public class VassRemoveVassShips extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        CampaignFleetAPI vassFleet;
        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
            vassFleet = (CampaignFleetAPI) dialog.getInteractionTarget();
        } else {
            return false;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        HashSet<FleetMemberAPI> toTransfer = new HashSet<>();
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            if (member.getHullId().startsWith("vass_")) {
                toTransfer.add(member);
            }

            //We ALSO take fighters from ships
            else {
                for (int i = 0; i < member.getStats().getNumFighterBays().getModifiedValue(); i++) {
                    if (member.getVariant().getWingId(i) != null && member.getVariant().getWingId(i).startsWith("vass_")) {
                        member.getVariant().setWingId(i, null);
                    }
                }
            }
        }
        for (FleetMemberAPI member : toTransfer) {
            member.setCaptain(null);
            playerFleet.getFleetData().removeFleetMember(member);
            vassFleet.getFleetData().addFleetMember(member);
        }

        //...also, remove Vass fighters in storage
        HashSet<CargoAPI.CargoItemQuantity<String>> toRemove = new HashSet<>();
        for (CargoAPI.CargoItemQuantity<String> fighter : playerFleet.getCargo().getFighters()) {
            if (fighter.getItem().startsWith("vass_") && fighter.getCount() > 0) {
                toRemove.add(fighter);
            }
        }
        for (CargoAPI.CargoItemQuantity<String> fighter : toRemove) {
            playerFleet.getCargo().removeItems(CargoAPI.CargoItemType.FIGHTER_CHIP, fighter.getItem(), fighter.getCount());
        }

        Global.getSector().getPlayerFleet().forceSync();
        Global.getSector().getPlayerFleet().updateCounts();
        vassFleet.forceSync();
        vassFleet.updateCounts();

        return true;
    }

}