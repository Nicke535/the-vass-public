package data.scripts.utils;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;

/**
 * Utility class for various things that are used in the Vass
 * @author Nicke535
 */
public class VassUtils {
    //The families's energy colors (basic is used )
    private static final float[] COLORS_RECIPRO = { 1f, 1f, 1f};
    private static final float[] COLORS_ACCEL = { 1f, 0f, 0f};
    private static final float[] COLORS_TORPOR = { 0.1f, 0.4f, 1f};
    private static final float[] COLORS_PERTURBA = { 0.8f, 1f, 0f};
    //Multa has its own color system, as it's randomized each frame
    private static final float[] COLORS_MULTA_MAX = { 1f, 1f, 1f};
    private static final float[] COLORS_MULTA_MIN = { 0.4f, 0.4f, 0.4f};

    public enum VASS_FAMILY {
        ACCEL,
        RECIPRO,
        TORPOR,
        PERTURBA,
        MULTA
    }

    //Function for getting a faction's color, with a certain opacity
    public static Color getFamilyColor (VASS_FAMILY family, float opacity) {
        switch (family) {
            case ACCEL:
                return new Color(COLORS_ACCEL[0], COLORS_ACCEL[1], COLORS_ACCEL[2], opacity);
            case RECIPRO:
                return new Color(COLORS_RECIPRO[0], COLORS_RECIPRO[1], COLORS_RECIPRO[2], opacity);
            case TORPOR:
                return new Color(COLORS_TORPOR[0], COLORS_TORPOR[1], COLORS_TORPOR[2], opacity);
            case PERTURBA:
                return new Color(COLORS_PERTURBA[0], COLORS_PERTURBA[1], COLORS_PERTURBA[2], opacity);
            case MULTA:
                return new Color(MathUtils.getRandomNumberInRange(COLORS_MULTA_MIN[0], COLORS_MULTA_MAX[0]),
                        MathUtils.getRandomNumberInRange(COLORS_MULTA_MIN[1], COLORS_MULTA_MAX[1]),
                        MathUtils.getRandomNumberInRange(COLORS_MULTA_MIN[2], COLORS_MULTA_MAX[2]), opacity);
        }

        //In case of something going... wrong, we return a horrendous pink since it's easy to see against normal Vass colors
        return new Color(1f, 0.4f, 1f, opacity);
    }


    //Gets whether a target is "shielded" by another ship (IE under its shield) from the perspective of a source location
    public static boolean isTargetUnderOtherShipShield (CombatEntityAPI target, Vector2f sourceLoc) {
        boolean targetShielded = false;
        Vector2f targetLoc = new Vector2f(target.getLocation());
        for (ShipAPI shielder : CombatUtils.getShipsWithinRange(sourceLoc, target.getCollisionRadius())) {
            //If the shielder *is* the target, ignore it
            if ((target instanceof ShipAPI) && (shielder == target)) {
                continue;
            }

            //Ignore if the shielder doesn't have a shield, or it's too far away from our current target to matter
            if (shielder.getShield() == null || MathUtils.getDistance(shielder.getShield().getLocation(), targetLoc) > shielder.getShield().getRadius()) {
                continue;
            }

            //Only run check if the shields are on and facing the source
            if (shielder.getShield().isOn() && shielder.getShield().isWithinArc(sourceLoc)) {
                //If the shielder has all these attributes, we run proper math. First, get some nice points we can work with.
                Vector2f checkPointShield = MathUtils.getPointOnCircumference(shielder.getShield().getLocation(), shielder.getShield().getRadius(),
                        VectorUtils.getAngle(shielder.getShield().getLocation(), sourceLoc));
                Vector2f checkVector = VectorUtils.getDirectionalVector(sourceLoc, targetLoc);
                checkVector.scale(target.getCollisionRadius());
                checkVector = Vector2f.add(checkVector, targetLoc, new Vector2f(0f, 0f));
                Vector2f checkPointHull = CollisionUtils.getCollisionPoint(sourceLoc, checkVector, target);

                //If we didn't get a collision, we can't possibly hit their shield
                if (checkPointHull == null) { continue; }

                //Is the shield closer to us than the target's *actual* hull (no targeting circles here, too imprecise)? If so, don't count this target as a valid target
                if (MathUtils.getDistance(checkPointShield, sourceLoc) <= MathUtils.getDistance(checkPointHull, sourceLoc)) {
                    targetShielded = true;
                    break;
                }
            }
        }
        return targetShielded;
    }

    /**
     * Gets a family's name, with option for capitalization
     */
    public static String getFamilyName(@NotNull VASS_FAMILY family, boolean capitalized) {
        if (capitalized) {
            if (family == VASS_FAMILY.ACCEL) {
                return "Accel";
            } else if (family == VASS_FAMILY.TORPOR) {
                return "Torpor";
            } else if (family == VASS_FAMILY.PERTURBA) {
                return "Perturba";
            } else if (family == VASS_FAMILY.RECIPRO) {
                return "Recipro";
            } else if (family == VASS_FAMILY.MULTA) {
                return "Multa";
            }
        } else {
            if (family == VASS_FAMILY.ACCEL) {
                return "accel";
            } else if (family == VASS_FAMILY.TORPOR) {
                return "torpor";
            } else if (family == VASS_FAMILY.PERTURBA) {
                return "perturba";
            } else if (family == VASS_FAMILY.RECIPRO) {
                return "recipro";
            } else if (family == VASS_FAMILY.MULTA) {
                return "multa";
            }
        }
        //Shouldn't happen, fallback
        return null;
    }

    /**
     * Gets which family membership a ship has, which varies depending on being in a mission, being an AI fleet etc.
     */
    public static VASS_FAMILY getFamilyMembershipOfShip(ShipAPI ship) {
        //If we're a fighter, we get our mothership's bonus
        if (ship.isFighter()) {
            if (ship.getWing() != null && ship.getWing().getSourceShip() != null) {
                return getFamilyMembershipOfShip(ship.getWing().getSourceShip());
            }
        }

        if (Global.getSector() == null || Global.getCurrentState().equals(GameState.TITLE) || (Global.getCombatEngine() != null && !Global.getCombatEngine().isInCampaign())) {
            //Missions: we check for secret hullmods but nothing else
            if (ship.getVariant().hasHullMod("vass_dummymod_accel_membership")) {
                return VASS_FAMILY.ACCEL;
            } else if (ship.getVariant().hasHullMod("vass_dummymod_torpor_membership")) {
                return VASS_FAMILY.TORPOR;
            } else if (ship.getVariant().hasHullMod("vass_dummymod_perturba_membership")) {
                return VASS_FAMILY.PERTURBA;
            } else if (ship.getVariant().hasHullMod("vass_dummymod_recipro_membership")) {
                return VASS_FAMILY.RECIPRO;
            } else if (ship.getVariant().hasHullMod("vass_dummymod_multa_membership")) {
                return VASS_FAMILY.MULTA;
            } else {
                return null;
            }
        } else {
            //In the campaign, we care if the ship belongs to the player's fleet or another fleet
            if (Global.getCombatEngine() != null && Global.getCombatEngine().isSimulation()) {
                //Simulation: just use the player's membership
                return VassFamilyTrackerPlugin.getFamilyMembership();
            }

            if (ship.getFleetMember() == null || ship.getFleetMember().getFleetData() == null || ship.getFleetMember().getFleetData().getFleet() == null) {
                //No fleet data: we can't tell which fleet we're from, so throw an exception
                throw new IllegalStateException("Tried to check family membership status of a ship that has no fleet data!");
            }

            if (ship.getFleetMember().getFleetData().equals(Global.getSector().getPlayerFleet().getFleetData())) {
                //Player-fleet ship; just check our current membership
                return VassFamilyTrackerPlugin.getFamilyMembership();
            }

            else {
                //Non-player fleet: check for a memory flag on the fleet to determine
                Object fleetMembership = ship.getFleetMember().getFleetData().getFleet().getMemoryWithoutUpdate().get("$vass_fleet_family_membership");
                if (fleetMembership instanceof VASS_FAMILY) {
                    return (VASS_FAMILY) fleetMembership;
                } else {
                    return null;
                }
            }
        }
    }

    /**
     * Checks if a ship is an "elite" for certain purposes. Player ships are always elite, as are any in-mission ships.
     * For non-player campaign fleets, only flagships and ships in elite fleets count as elite
     */
    public static boolean isShipAnElite(ShipAPI ship) {
        //If we're a fighter, and not a "unique" one, we get our mothership's bonus
        if (ship.isFighter() && !ship.getHullSpec().getTags().contains("vass_family_unique_ship")) {
            if (ship.getWing() != null && ship.getWing().getSourceShip() != null) {
                return isShipAnElite(ship.getWing().getSourceShip());
            }
        }
        //Elite fighter wings are *always* elite... thus the name
        else if (ship.isFighter()) {
            return true;
        }

        if (Global.getSector() == null || Global.getCurrentState().equals(GameState.TITLE) || (Global.getCombatEngine() != null && !Global.getCombatEngine().isInCampaign())) {
            //Missions: we are always elite
            return true;
        } else {
            //In the campaign, we care if the ship belongs to the player's fleet or another fleet
            if (Global.getCombatEngine() != null && Global.getCombatEngine().isSimulation()) {
                //Simulation; count as elite, it's basically a mission
                return true;
            }

            if (ship.getFleetMember() == null || ship.getFleetMember().getFleetData() == null || ship.getFleetMember().getFleetData().getFleet() == null) {
                //No fleet data: we can't tell which fleet we're from, so throw an exception
                throw new IllegalStateException("Tried to check elite status of a ship that has no fleet data!");
            }

            if (ship.getFleetMember().getFleetData().equals(Global.getSector().getPlayerFleet().getFleetData())) {
                //Player-fleet ship; we're elite
                return true;
            }

            else {
                //Non-player fleet: check for a memory flag on the fleet to determine if the whole fleet is elite
                Object fleetIsElite = ship.getFleetMember().getFleetData().getFleet().getMemoryWithoutUpdate().get("$vass_fleet_family_elite");
                if (fleetIsElite instanceof Boolean) {
                    if ((Boolean) fleetIsElite) {
                        return true;
                    }
                }

                //We're not in an elite fleet: thus, we are only elite if we're the flagship
                if (ship.getFleetMember().equals(ship.getFleetMember().getFleetData().getFleet().getFlagship())) {
                    return true;
                } else {
                    return false;
                }
            }
        }
    }


    //Gets an array of points on a "parabola-esque" arc from startPos to endPos
    public static ArrayList<Vector2f> getFancyArcPoints (Vector2f startPos, Vector2f endPos, float arcHeight, int pointsDesired) {
        ArrayList<Vector2f> returnList = new ArrayList<>();

        //Calculates the "constant" values to use for the rest of the function
        float angleForArc = VectorUtils.getAngle(startPos, endPos) + 90f;
        Vector2f directionVector = VectorUtils.getDirectionalVector(startPos, endPos);
        float totalDistance = MathUtils.getDistance(startPos, endPos);

        //Calculates the position of each individual point and adds it to the return list
        for (float i = 0; i < pointsDesired; i++) {
            Vector2f pointWithNoOffset = Vector2f.add(startPos, VectorUtils.resize(new Vector2f(directionVector), totalDistance * (i/(float)(pointsDesired-1))), new Vector2f(0f, 0f));
            Vector2f pointWithOffset = MathUtils.getPoint(pointWithNoOffset, arcHeight * (float)(FastTrig.sin(Math.PI * (i/(float)(pointsDesired-1)))), angleForArc);
            returnList.add(pointWithOffset);
        }

        //Finally, returns our list
        return returnList;
    }
}
