//By Nicke535
//Causes nearby missiles and projectiles to be "shifted" through time, skipping over anything in the way
package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class VassChronoJump extends BaseShipSystemScript {
    //Percieved time mult when charging the system
    public static final float TIME_MULT_PERCIEVED = 0.25f;

    //Max AoE of the system (not including the ship's collision radius)
    public static final float MAX_RANGE = 300f;

    //How far into the future will each projectile be launched?
    public static final float TIME_SKIP_AMOUNT = 0.5f;

    //The maximum duration the system can be kept active before it is automatically triggered
    public static final float MAX_ACTIVE_DURATION = 0.5f;

    private boolean triggeredOnce = false;
    private float currentActiveDuration = 0f;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        //While active, but not yet triggering, we have a percieved reduced timeflow and see where each projectile will end up
        //after our trigger (only the player experience this)
        if (!state.equals(State.OUT) && effectLevel > 0f) {
            triggeredOnce = false;
            currentActiveDuration += Global.getCombatEngine().getElapsedInLastFrame() * ship.getMutableStats().getTimeMult().getModifiedValue();

            if (player) {
                Global.getCombatEngine().getTimeMult().modifyMult(id, 1f - (1f - TIME_MULT_PERCIEVED) * effectLevel);
                List<DamagingProjectileAPI> allProjs = CombatUtils.getProjectilesWithinRange(ship.getLocation(),ship.getCollisionRadius()+MAX_RANGE);
                allProjs.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(),ship.getCollisionRadius()+MAX_RANGE));
                for (DamagingProjectileAPI proj : allProjs) {
                    spawnFutureIndicator(proj);
                }
            } else {
                Global.getCombatEngine().getTimeMult().unmodify(id);
            }
        }

        //If the system is now being triggered, move all projectiles to their new location (and spawn fancy particles, too)
        else if (!triggeredOnce) {
            Global.getCombatEngine().getTimeMult().unmodify(id);
            List<DamagingProjectileAPI> allProjs = CombatUtils.getProjectilesWithinRange(ship.getLocation(),ship.getCollisionRadius()+MAX_RANGE);
            allProjs.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(),ship.getCollisionRadius()+MAX_RANGE));
            for (DamagingProjectileAPI proj : allProjs) {
                //Teleport to the future
                Vector2f startPos = new Vector2f(proj.getLocation());
                proj.getLocation().x += proj.getVelocity().x * TIME_SKIP_AMOUNT;
                proj.getLocation().y += proj.getVelocity().y * TIME_SKIP_AMOUNT;
                if (proj instanceof MissileAPI) {((MissileAPI) proj).setFlightTime(((MissileAPI) proj).getFlightTime() + TIME_SKIP_AMOUNT);}

                //Spawn a fancy particle trail from current position to future position
                List<Vector2f> pointsToSpawnAt = VassUtils.getFancyArcPoints(startPos,
                        proj.getLocation(),
                        MathUtils.getDistance(startPos, proj.getLocation()) * MathUtils.getRandomNumberInRange(-0.08f, 0.08f),
                        (int)(MathUtils.getDistance(startPos, proj.getLocation()) * 0.06f));
                Color colorToUse = new Color(255, 210, 180, Math.min((int)(proj.getDamageAmount()*4f), 255));
                if (proj.getDamageType() == DamageType.ENERGY) {
                    colorToUse = new Color(170, 150, 255, Math.min((int)(proj.getDamageAmount()*7f), 255));
                } else if (proj.getDamageType() == DamageType.HIGH_EXPLOSIVE) {
                    colorToUse = new Color(255, 140, 120, Math.min((int)(proj.getDamageAmount()*10f), 255));
                } else if (proj.getDamageType() == DamageType.KINETIC) {
                    colorToUse = new Color(230, 240, 255, Math.min((int)(proj.getDamageAmount()*7f), 255));
                }

                for (Vector2f point : pointsToSpawnAt) {
                    Global.getCombatEngine().addSmoothParticle(point, new Vector2f(0f, 0f), (float)Math.sqrt(proj.getDamageAmount()*2f),
                            1f, 0.35f, colorToUse);
                }
            }
        }

        //If neither case happens, we still want rid of the percieved time mult
        else {
            Global.getCombatEngine().getTimeMult().unmodify(id);
        }

        //If we've passed our maximum active duration, de-activate the system at the end of frame
        if (currentActiveDuration > MAX_ACTIVE_DURATION) {
            ship.useSystem();
            currentActiveDuration = 0f;
        }
    }


    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        Global.getCombatEngine().getTimeMult().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            if (!state.equals(State.OUT)) {
                if (currentActiveDuration < MAX_ACTIVE_DURATION *0.75f) {
                    return new StatusData("Priming system...", false);
                } else {
                    return new StatusData("ENERGY LEVELS CRITICAL", true);
                }
            } else {
                return new StatusData("ACTIVATE", false);
            }
        }
        return null;
    }


    //Uility function for spawning a visual indicator indicating a projectile's future. Varies slightly based
    //on damage type and amount
    private void spawnFutureIndicator (DamagingProjectileAPI proj) {

    }
}