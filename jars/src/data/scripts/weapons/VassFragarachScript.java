package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import data.scripts.utils.VassTimeDistortionProjScript;
import data.scripts.utils.VassTimeSplitProjScript;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.util.ArrayList;
import java.util.List;

public class VassFragarachScript implements EveryFrameWeaponEffectPlugin {
    //How far does the Fragarach apply timeline-gracing?
    private final static float GRACE_DISTANCE = 135f;

    //What's the multiplier for damage dealt via timeline-gracing?
    private final static float GRACE_DAMAGE_MULT = 0.7f;

    //Keeps track of already-affected projectiles
    private List<DamagingProjectileAPI> alreadyTriggeredProjectiles = new ArrayList<>();

    //Used for clearing out projectiles we no longer need to care about
    private List<DamagingProjectileAPI> toRemove = new ArrayList<>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        for (CombatEntityAPI entity : CombatUtils.getEntitiesWithinRange(weapon.getLocation(), 400f)) {
            if (!(entity instanceof DamagingProjectileAPI)) {
                continue;
            }
            DamagingProjectileAPI proj = (DamagingProjectileAPI)entity;

            //Only run once per projectile
            if (alreadyTriggeredProjectiles.contains(proj)) {
                continue;
            }

            //If the projectile is our own, we can do something with it
            if (proj.getWeapon() == weapon) {
                //Register that we've triggered on the projectile
                alreadyTriggeredProjectiles.add(proj);

                //Add a new plugin that keeps track of the projectile
                engine.addPlugin(new VassTimeSplitProjScript(proj, GRACE_DISTANCE, GRACE_DAMAGE_MULT));
            }
        }

        //Also, we clean up our already triggered projectiles when they stop being loaded into the engine
        for (DamagingProjectileAPI proj : alreadyTriggeredProjectiles) {
            if (!engine.isEntityInPlay(proj)) {
                toRemove.add(proj);
            }
        }
        alreadyTriggeredProjectiles.removeAll(toRemove);
        toRemove.clear();
    }
}