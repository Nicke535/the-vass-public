package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.DefenderDataOverride;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.util.vector.Vector2f;

public class VassCampaignUtils {
    /** Utility function for spawning a derelict in a system and setting varius attributes for it
     * @return The entity token of the derelict just created
     * */
    public static SectorEntityToken addDerelict(StarSystemAPI system, SectorEntityToken focus, String variantId,
                                                ShipRecoverySpecial.ShipCondition condition, float orbitRadius, float daysToOrbit,
                                                float startOrbitAngle, boolean recoverable, @Nullable DefenderDataOverride defenders) {

        DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData(variantId, condition), false);
        SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(system, Entities.WRECK, Factions.NEUTRAL, params);
        ship.setDiscoverable(true);

        ship.setCircularOrbit(focus, startOrbitAngle, orbitRadius, daysToOrbit);

        if (recoverable) {
            SalvageSpecialAssigner.ShipRecoverySpecialCreator creator = new SalvageSpecialAssigner.ShipRecoverySpecialCreator(null, 0, 0, false, null, null);
            Misc.setSalvageSpecial(ship, creator.createSpecial(ship, null));
        }
        if (defenders != null) {
            Misc.setDefenderOverride(ship, defenders);
        }
        return ship;
    }


    /**
     * Utility function for getting a fleet to intercept another fleet
     * NOTE: makeAggressive only works against the player fleet
     */
    public static void makeFleetInterceptOtherFleet(CampaignFleetAPI aggressor, CampaignFleetAPI defendant, boolean makeAggressive, float interceptDays) {
        makeFleetInterceptOtherFleet(aggressor, defendant, makeAggressive, interceptDays, "generic");
    }
    public static void makeFleetInterceptOtherFleet(CampaignFleetAPI aggressor, CampaignFleetAPI defendant, boolean makeAggressive, float interceptDays, String reason) {
        if (aggressor.getAI() == null) {
            aggressor.setAI(Global.getFactory().createFleetAI(aggressor));
            aggressor.setLocation(aggressor.getLocation().x, aggressor.getLocation().y);
        }

        if (makeAggressive) {
            float expire = aggressor.getMemoryWithoutUpdate().getExpire(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
            aggressor.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true, Math.max(expire, interceptDays));
            Misc.setFlagWithReason(aggressor.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, reason, true, Math.max(expire, interceptDays));
            //Misc.setFlagWithReason(aggressor.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_HOSTILE, reason, true, interceptDays);
        }

        aggressor.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE);
        aggressor.getMemoryWithoutUpdate().set(FleetAIFlags.LAST_SEEN_TARGET_LOC, new Vector2f(defendant.getLocation()), interceptDays);

        if (aggressor.getAI() instanceof ModularFleetAIAPI) {
            ((ModularFleetAIAPI)aggressor.getAI()).getTacticalModule().setTarget(defendant);
        }

        //DELIVER_CREW can't be interrupted by other fleets, unlike INTERCEPT or similar
        aggressor.addAssignment(FleetAssignment.DELIVER_CREW, defendant, interceptDays, "Engaging " + defendant.getNameWithFaction(),null);
        Global.getSector().addScript(new RenewAggressionPlugin(Global.getSector(), aggressor, defendant, interceptDays));
    }


    /**
     * Local class for making sure the fleet *keeps* going after the target, alternatively escapes, after any events have happened
     */
    static private class RenewAggressionPlugin implements EveryFrameScript {
        private SectorAPI sector;
        private CampaignFleetAPI aggressor;
        private CampaignFleetAPI defendant;
        private float startingFP;
        private IntervalUtil timer = new IntervalUtil(0.9f, 1.2f);
        private float interceptDaysRemaining;
        RenewAggressionPlugin(SectorAPI sector, CampaignFleetAPI aggressor, CampaignFleetAPI defendant, float interceptDays) {
            this.sector = sector;
            this.aggressor = aggressor;
            this.defendant = defendant;
            this.startingFP = aggressor.getFleetPoints();
            this.interceptDaysRemaining = interceptDays;
        }

        @Override
        public void advance(float amount) {
            //Check every second or so
            interceptDaysRemaining -= Misc.getDays(amount);
            timer.advance(amount);
            if (timer.intervalElapsed()) {
                //Heavily damaged or timed out: retreat to nearest jump point
                if (aggressor.getFleetPoints() < startingFP*0.5f || interceptDaysRemaining <= 0f) {
                    if (!aggressor.isCurrentAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN)) {
                        aggressor.clearAssignments();
                        aggressor.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, Misc.findNearestJumpPointTo(aggressor), 999f);
                        return;
                    }
                }

                //Ordered to flee
                Object shouldEscape = aggressor.getMemoryWithoutUpdate().get("$vass_fleet_should_escape");
                if (shouldEscape instanceof Boolean && (Boolean)shouldEscape) {
                    if (!aggressor.isCurrentAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN)) {
                        aggressor.clearAssignments();
                        aggressor.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, Misc.findNearestJumpPointTo(aggressor), 999f);
                        return;
                    }
                }

                //No other orders or scenarios: re-engage the target!
                aggressor.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE);
                aggressor.getMemoryWithoutUpdate().set(FleetAIFlags.LAST_SEEN_TARGET_LOC, new Vector2f(defendant.getLocation()), interceptDaysRemaining);

                if (aggressor.getAI() instanceof ModularFleetAIAPI) {
                    ((ModularFleetAIAPI)aggressor.getAI()).getTacticalModule().setTarget(defendant);
                }

                //DELIVER_CREW can't be interrupted by other fleets, unlike INTERCEPT or similar
                aggressor.addAssignment(FleetAssignment.DELIVER_CREW, defendant, interceptDaysRemaining, "Engaging " + defendant.getNameWithFaction(),null);
            }
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public boolean isDone() {
            return aggressor.isDespawning();
        }
    }
}
