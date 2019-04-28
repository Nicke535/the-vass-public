//By Nicke535, causes newly-launched fighters to follow a straight path out from a hidden weapon instead of just going "up"
package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VassDirectedTakeoffBayScript implements EveryFrameWeaponEffectPlugin {
    //The maximum "speed boost" the fighter gets when taking off; 1f means a 100% speed boost initially
    //  This bonus then fades quadratically over the duration
    private static final float MAX_SPEED_BOOST = 2.5f;

    //Long long should we keep moving our fighters (in seconds)?
    private static final float MOVE_DURATION = 0.5f;

    //Store how long we've been tracking each fighter.
    private Map<ShipAPI, Float> timers = new HashMap<>();

    //How big the "bay area" is, as a circle's radius
    //  Fighters will appear to randomly come from within this circle
    private static final float SPAWN_RADIUS = 10f;

    //We occasionally clean our timer map
    private static final float CLEAN_WAIT = 10f;
    private float cleanTimer = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (ship == null || ship.isHulk()) {
            return;
        }

        //Handles cleanup of our fighter map now and then
        cleanTimer += amount;
        if (cleanTimer > CLEAN_WAIT) {
            cleanTimer = 0f;
            List<ShipAPI> toRemove = new ArrayList<>();
            for (ShipAPI fighter : timers.keySet()) {
                if (!engine.isEntityInPlay(fighter) || fighter.isHulk() || fighter.isLanding() || fighter.isFinishedLanding()) {
                    toRemove.add(fighter);
                }
            }
            for (ShipAPI fighter : toRemove) {
                timers.remove(fighter);
            }
        }

        //Gets all our nearby fighters, and if we haven't grabbed them before, grab them as they're most likely taking off for the first time
        for (ShipAPI fighter : CombatUtils.getShipsWithinRange(ship.getLocation(), ship.getCollisionRadius()*1.5f)) {
            //Check for ignored/removed fighters to avoid infinity loops of re-adding
            if (!engine.isEntityInPlay(fighter) || fighter.isHulk() || fighter.isLanding() || fighter.isFinishedLanding()) {
                continue;
            }

            //Don't add already-tracked fighters
            if (timers.containsKey(fighter)) {
                continue;
            }

            //Only *our* fighters are affected
            if (fighter.getWing() != null && fighter.getWing().getSourceShip() == ship) {
                //The first time we touch the fighter, we also teleport it to our launch bay
                Vector2f targetSpawnPoint = MathUtils.getRandomPointInCircle(weapon.getLocation(), SPAWN_RADIUS);
                fighter.getLocation().x = targetSpawnPoint.x;
                fighter.getLocation().y = targetSpawnPoint.y;
                timers.put(fighter, 0f);
            }
        }

        //Go through all fighters and handle their actual movement-manipulation
        for (ShipAPI fighter : timers.keySet()) {
            //Compensates for time-mult
            float trueAmount = amount * fighter.getMutableStats().getTimeMult().getModifiedValue() / ship.getMutableStats().getTimeMult().getModifiedValue();
            timers.put(fighter, timers.get(fighter)+trueAmount);

            if (timers.get(fighter) < MOVE_DURATION) {
                fighter.setFacing(weapon.getCurrAngle());
                fighter.getVelocity().x = fighter.getMaxSpeed() * (float)FastTrig.cos(Math.toRadians(weapon.getCurrAngle()))
                        * (1f + MAX_SPEED_BOOST * ((float)Math.pow(1f - (timers.get(fighter) / MOVE_DURATION), 2f)));
                fighter.getVelocity().y = fighter.getMaxSpeed() * (float)FastTrig.sin(Math.toRadians(weapon.getCurrAngle()))
                        * (1f + MAX_SPEED_BOOST * ((float)Math.pow(1f - (timers.get(fighter) / MOVE_DURATION), 2f)));
            }
        }
    }
}