package data.scripts.utils;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;

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
        return getFamilyColor(family, opacity, false);
    }
    public static Color getFamilyColor (VASS_FAMILY family, float opacity, boolean withSafetyOverrides) {
        Color color = null;

        if (family == VASS_FAMILY.ACCEL) {
            color = new Color(COLORS_ACCEL[0], COLORS_ACCEL[1], COLORS_ACCEL[2], opacity);
        } else if (family == VASS_FAMILY.RECIPRO) {
            color = new Color(COLORS_RECIPRO[0], COLORS_RECIPRO[1], COLORS_RECIPRO[2], opacity);
        } else if (family == VASS_FAMILY.TORPOR) {
            color = new Color(COLORS_TORPOR[0], COLORS_TORPOR[1], COLORS_TORPOR[2], opacity);
        } else if (family == VASS_FAMILY.PERTURBA) {
            color = new Color(COLORS_PERTURBA[0], COLORS_PERTURBA[1], COLORS_PERTURBA[2], opacity);
        } else if (family == VASS_FAMILY.MULTA) {
            color = new Color(MathUtils.getRandomNumberInRange(COLORS_MULTA_MIN[0], COLORS_MULTA_MAX[0]),
                    MathUtils.getRandomNumberInRange(COLORS_MULTA_MIN[1], COLORS_MULTA_MAX[1]),
                    MathUtils.getRandomNumberInRange(COLORS_MULTA_MIN[2], COLORS_MULTA_MAX[2]), opacity);
        }

        //In case of something going... wrong, we return a horrendous pink since it's easy to see against normal Vass colors
        if (color == null) {
            return new Color(1f, 0f, 1f, opacity);
        }

        //If we wanted safety overrides, we blend the color with the SO color scheme, otherwise we juts return the color outright
        if (withSafetyOverrides) {
            return (Misc.interpolateColor(color ,new Color(255,100,255, 255), 0.4f));
        } else {
            return color;
        }
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
