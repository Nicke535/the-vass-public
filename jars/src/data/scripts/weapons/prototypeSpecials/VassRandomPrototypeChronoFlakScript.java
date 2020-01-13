package data.scripts.weapons.prototypeSpecials;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import data.scripts.utils.VassPerturbaRandomPrototypeManager;
import data.scripts.utils.VassTimeDistortionProjScript;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.util.LinkedList;

/**
 * Acts as a form of "chrono-flak": used for the Random Prototype script exclusively
 * @author Nicke535
 */
public class VassRandomPrototypeChronoFlakScript implements EveryFrameWeaponEffectPlugin {

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //Keeps our projectile list somewhere we can access it stateless: also access it
        ProjList alreadyTriggeredProjectiles = null;
        Object response = engine.getCustomData().get("VassRandomPrototypeChronoFlakProjList");
        if (response instanceof ProjList) {
            alreadyTriggeredProjectiles = (ProjList)response;
        } else {
            alreadyTriggeredProjectiles = new ProjList();
            engine.getCustomData().put("VassRandomPrototypeChronoFlakProjList", alreadyTriggeredProjectiles);
        }

        //Find all our projectiles that aren't our "fake" projectiles
        for (CombatEntityAPI entity : CombatUtils.getEntitiesWithinRange(weapon.getLocation(), 400f)) {
            if (!(entity instanceof DamagingProjectileAPI)) {
                continue;
            }
            DamagingProjectileAPI proj = (DamagingProjectileAPI)entity;

            //Only run once per projectile
            if (alreadyTriggeredProjectiles.contains(proj)) {
                continue;
            }

            //Ignore our fake projectiles
            if (("vass_fake_prototype_shot").equals(proj.getProjectileSpecId())) {
                continue;
            }

            //If the projectile is our own, we can do something with it
            if (proj.getWeapon() == weapon) {
                //Register that we've triggered on the projectile
                alreadyTriggeredProjectiles.add(proj);

                //And apply our effect
                applyEffectOnProjectile(engine, proj);
            }
        }

        //Also, we clean up our already triggered projectiles when they stop being loaded into the engine
        ProjList toRemove = new ProjList();
        for (DamagingProjectileAPI proj : alreadyTriggeredProjectiles) {
            if (!engine.isEntityInPlay(proj)) {
                toRemove.add(proj);
            }
        }
        alreadyTriggeredProjectiles.removeAll(toRemove);
        toRemove.clear();
    }

    public static void applyEffectOnProjectile(CombatEngineAPI engine, DamagingProjectileAPI proj) {
        engine.addPlugin(new VassTimeDistortionProjScript(proj, MathUtils.getRandomNumberInRange(0.7f, 1.35f), "vass_tizona_detonation"));
    }

    //For type-safety reasons and nothing more
    private class ProjList extends LinkedList<DamagingProjectileAPI> {}

    //For guidance
    public static class Guidance extends VassPerturbaRandomPrototypeManager.GuidanceApplier {
        @Override
        public void applyToProjectile(DamagingProjectileAPI proj) {
            Global.getCombatEngine().addPlugin(new VassRandomPrototypeChronoFlakGuidanceScript(proj, null));
        }
    }
}