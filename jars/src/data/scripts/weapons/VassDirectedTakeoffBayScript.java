//By Nicke535, causes newly-launched fighters to follow a straight path out from the fighter bay instead of just going "up"
//  ...at least, that's the plan
package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VassDirectedTakeoffBayScript implements EveryFrameWeaponEffectPlugin {

    //Long long should we keep moving our fighters (in seconds)?
    private static final float MOVE_DURATION = 1f;

    //Store how long we've been tracking each fighter.
    private Map<ShipAPI, Float> timers = new HashMap<>();

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
            if (engine.isEntityInPlay(fighter) && !fighter.isHulk() && !fighter.isLanding() && !fighter.isFinishedLanding()) {
                continue;
            }

            //Don't add already-tracked fighters
            if (timers.containsKey(fighter)) {
                continue;
            }

            //Only *our* fighters are affected
            if (fighter.getWing() != null && fighter.getWing().getSourceShip() == ship) {
                timers.put(fighter, 0f);
            }
        }

        //Go through all fighters and handle their actual movement-manipulation
        for (ShipAPI fighter : timers.keySet()) {
            timers.put(fighter, timers.get(fighter)+amount);

            if (timers.get(fighter) < MOVE_DURATION) {
                fighter.setFacing(ship.getFacing());
                fighter.getVelocity().x = fighter.getMaxSpeed() * (float)FastTrig.cos(Math.toRadians(ship.getFacing()));
                fighter.getVelocity().y = fighter.getMaxSpeed() * (float)FastTrig.sin(Math.toRadians(ship.getFacing()));
            }
        }
    }
}