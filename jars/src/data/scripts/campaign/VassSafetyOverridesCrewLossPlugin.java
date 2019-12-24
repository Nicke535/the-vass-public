package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.hullmods.VassPeriodicPlating;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Brutally punishes the player (or rather, their crew) when using Vass tech without reading the manual first
 * @author Nicke535
 */
public class VassSafetyOverridesCrewLossPlugin implements EveryFrameScript {

    IntervalUtil interval = new IntervalUtil(0.5f, 1.2f);

    @Override
    public void advance( float amount ) {
        SectorAPI sector = Global.getSector();
        if (sector == null) {
            return;
        }

        //Only runs every few days
        interval.advance(Misc.getDays(amount));
        if (interval.intervalElapsed()) {
            //Gets the player fleet, and checks each ship in our hullmod's stored static list to see if it matches
            float crewLossTotal = 0f;
            for (FleetMemberAPI member : sector.getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (VassPeriodicPlating.timeInCombatMap.keySet().contains(member.getId())) {
                    float crewLossThisShip = (VassPeriodicPlating.timeInCombatMap.get(member.getId()) / 60f) *
                            VassPeriodicPlating.CREW_LOST_FRACTION_PER_MINUTE * member.getMinCrew();

                    //Don't drain more crew than we could realistically lose (50% of mounted crew)
                    crewLossThisShip = Math.min(member.getMinCrew() * member.getCrewFraction() * 0.5f, crewLossThisShip);

                    crewLossTotal += crewLossThisShip;
                }
            }
            VassPeriodicPlating.timeInCombatMap.clear();
            crewLossTotal *= MathUtils.getRandomNumberInRange(0.5f, 1f);

            //Some sanity adjustment: notably, we can't loose *all* crew, no matter how dire the situation. Let's say we at most loose half of them
            crewLossTotal = Math.min(sector.getPlayerFleet().getCargo().getCrew()*0.5f, crewLossTotal);

            //If the crew loss is higher than 1, we tell the player and drain their crew
            if (Math.floor(crewLossTotal) > 1) {
                sector.getPlayerFleet().getCargo().removeCrew((int)Math.floor(crewLossTotal));
                int crewLossForFlavorText = (int)Math.floor(crewLossTotal);
                int crewCasualties = (int)MathUtils.getRandomNumberInRange(crewLossForFlavorText*0.3f, crewLossForFlavorText*0.7f);
                crewLossForFlavorText -= crewCasualties;
                int crewComas = (int)MathUtils.getRandomNumberInRange(0f, crewLossForFlavorText*0.05f);
                crewLossForFlavorText -= crewComas;
                String flavorText = "Lack of safety protocols surrounding your ships' Periodic Plating resulted in ";
                boolean and = false;
                if (crewCasualties > 0) {
                    flavorText += crewCasualties + " casualt";
                    if (crewCasualties == 1) {
                        flavorText += "y";
                    } else {
                        flavorText += "ies";
                    }
                    and = true;
                }
                if (crewComas > 0) {
                    if (and) {
                        flavorText += ", ";
                    } else {
                        and = true;
                    }
                    flavorText += crewComas + " coma";
                    if (crewComas != 1) {
                        flavorText += "s";
                    }
                }
                if (and) {
                    flavorText += " and ";
                }
                flavorText += crewLossForFlavorText + " crewmember";
                if (crewLossForFlavorText != 1) {
                    flavorText += "s";
                }
                flavorText += " being rendered unfit for ship-bound deployment during the last battle.";
                sector.getCampaignUI().addMessage(flavorText, Misc.getTextColor(), "Lack of safety protocols", "unfit for ship-bound deployment", Misc.getNegativeHighlightColor(), Misc.getHighlightColor());
            }
        }
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
