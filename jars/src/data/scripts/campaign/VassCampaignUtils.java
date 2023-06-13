package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.DefenderDataOverride;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.utils.VassUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.util.vector.Vector2f;

public class VassCampaignUtils {

    public static final Logger LOGGER = Global.getLogger(VassCampaignUtils.class);

    public enum MissionImportance {
        TRIVIAL,
        STANDARD,
        CRUCIAL,
        CRITICAL
    }
    /** Utility function for getting the XP reward of a mission (of specific difficulty/importance) from a specific family
     * @param family The family which was involved in the mission
     * @param antiVass If true, the mission was against the family in question, otherwise it was in favor of it
     * @return The amount of experience that should be handed out
     */
    public static long getVassMissionXP(MissionImportance importance, VassUtils.VASS_FAMILY family, boolean antiVass) {
        // Base XP value depends on the mission importance
        long xp = 200;
        if (importance == MissionImportance.TRIVIAL) {
            xp = 100;
        } else if (importance == MissionImportance.CRUCIAL) {
            xp = 350;
        } else if (importance == MissionImportance.CRITICAL) {
            xp = 750;
        }

        // When fighting for the vass, you receive bonus XP if you took a particularly important mission for a small Family
        if (!antiVass && (importance == MissionImportance.CRUCIAL || importance == MissionImportance.CRITICAL)) {
            if (VassFamilyTrackerPlugin.getPowerOfFamily(family) < 10) {
                xp *= 2;
            }
        }

        // When fighting the vass, these modifiers are instead inverted: bonus XP is handed out for fighting the high-power families
        if (antiVass && (importance == MissionImportance.CRUCIAL || importance == MissionImportance.CRITICAL)) {
            if (VassFamilyTrackerPlugin.getPowerOfFamily(family) > 90) {
                xp *= 2;
            }
        }

        return xp;
    }

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

        aggressor.getMemoryWithoutUpdate().set(FleetAIFlags.LAST_SEEN_TARGET_LOC, new Vector2f(defendant.getLocation()), interceptDays);

        //Order the fleet to stick to its target until the get beaten up or beat them
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
        private IntervalUtil timer = new IntervalUtil(0.4f, 0.6f);
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
            //Check every half second or so
            interceptDaysRemaining -= Misc.getDays(amount);
            timer.advance(amount);
            if (timer.intervalElapsed()) {
                // If we have despawned, we can just remove the script now
                if (aggressor.isDespawning() || sector.getPlayerFleet().getContainingLocation().getEntityById(aggressor.getId()) == null) {
                    LOGGER.info("Punitive fleet despawned, removing script");
                    sector.removeScript(this);
                    return;
                }

                // Checks AI validity
                if (!(aggressor.getAI() instanceof ModularFleetAIAPI)) {
                    //Invalid AI: cancel the script outright
                    LOGGER.warn("Punitive fleet AI invalid, removing script");
                    sector.removeScript(this);
                    return;
                }
                ModularFleetAIAPI ai = (ModularFleetAIAPI)aggressor.getAI();
                if (ai.getAssignmentModule() == null) {
                    // No assignment module: we can't do anything so return
                    LOGGER.warn("Punitive fleet AI assignment module NULL");
                    return;
                }
                FleetAssignmentDataAPI curr = ai.getAssignmentModule().getCurrentAssignment();


                //Are we heavily damaged, or have run out of time for the intercept? If so, retreat back to the nearest jumppoint. This script has served its purpose.
                // - This also applies if we've manually been ordered to retreat by another script
                Object orderedToEscape = aggressor.getMemoryWithoutUpdate().get("$vass_fleet_should_escape");
                if (aggressor.getFleetPoints() < startingFP*0.5f || interceptDaysRemaining <= 0f
                        || (orderedToEscape instanceof Boolean && (Boolean)orderedToEscape)) {
                    ai.getAssignmentModule().clearAssignments();
                    ai.getAssignmentModule().addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, Misc.findNearestJumpPointTo(aggressor), 999f, null);
                    LOGGER.info("Punitive fleet ordered to escape or damaged, leaving and despawning");
                    sector.removeScript(this);
                    return;
                }

                //Are we already intercepting or following the target? If so, no further order needed
                if (curr != null && curr.getTarget() == defendant &&
                        (curr.getAssignment() == FleetAssignment.INTERCEPT ||
                                curr.getAssignment() == FleetAssignment.FOLLOW)) {
                    return;
                }

                // Are we in the same area, within a reasonable distance, and haven't been lost on sensors?
                // If so, go on the offensive
                if (aggressor.getContainingLocation() == defendant.getContainingLocation() &&
                        defendant.getVisibilityLevelTo(aggressor) != SectorEntityToken.VisibilityLevel.NONE &&
                        aggressor.getVisibilityLevelTo(defendant) != SectorEntityToken.VisibilityLevel.NONE) {
                    ai.getAssignmentModule().addAssignmentAtStart(FleetAssignment.INTERCEPT, defendant, 3f, null);
                    LOGGER.info("Punitive fleet intercepting player");
                    return;
                }

                // Fall-through: we've lost our target, and they've lost us. Head back and despawn, the script is no longer needed
                ai.getAssignmentModule().clearAssignments();
                ai.getAssignmentModule().addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, Misc.findNearestJumpPointTo(aggressor), 999f, null);
                LOGGER.info("Lost target for punitive fleet, leaving and despawning");
                sector.removeScript(this);
            }
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }
    }
}
