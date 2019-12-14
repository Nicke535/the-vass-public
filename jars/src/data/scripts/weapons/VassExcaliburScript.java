package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import data.scripts.utils.VassTimeDistortionProjScript;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.util.ArrayList;
import java.util.List;

public class VassExcaliburScript implements EveryFrameWeaponEffectPlugin {

    //Keeps track of already-affected projectiles
    private List<MissileAPI> alreadyTriggeredProjectiles = new ArrayList<>();

    //Used for clearing out projectiles we no longer need to care about
    private List<MissileAPI> toRemove = new ArrayList<>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        for (MissileAPI msl : CombatUtils.getMissilesWithinRange(weapon.getLocation(), 400f)) {
            //Only run once per projectile
            if (alreadyTriggeredProjectiles.contains(msl)) {
                continue;
            }

            //If the projectile is our own, we can do something with it
            if (msl.getWeapon() == weapon) {
                //Register that we've triggered on the projectile
                alreadyTriggeredProjectiles.add(msl);

                //Makes projectiles slightly cooler on fade-out
                msl.setFlightTime(MathUtils.getRandomNumberInRange(0f, 0.2f));

                //Add a new plugin that keeps track of the projectile
                engine.addPlugin(new VassTimeDistortionProjScript(msl, MathUtils.getRandomNumberInRange(0.5f, 1.8f), "vass_excalibur_detonation", 0.85f));
            }
        }

        //Also, we clean up our already triggered projectiles when they stop being loaded into the engine
        for (MissileAPI msl : alreadyTriggeredProjectiles) {
            if (!engine.isEntityInPlay(msl)) {
                toRemove.add(msl);
            }
        }
        alreadyTriggeredProjectiles.removeAll(toRemove);
        toRemove.clear();
    }
}