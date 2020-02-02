package data.scripts.weapons.prototypeSpecials;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashSet;
import java.util.List;

/**
 * Acts as a form of "supercharged gun", SPEWING flux for bonus damage: used for the Random Prototype script exclusively
 * @author Nicke535
 */
public class VassRandomPrototypeSuperchargedScript implements EveryFrameWeaponEffectPlugin {

    private float damagePerFlux;
    private float maxFluxLevelToFire;
    private float fluxLevelToReach;
    private boolean isChronoFlakToo;

    public VassRandomPrototypeSuperchargedScript(float damagePerFlux, float maxFluxLevelToFire, float fluxLevelToReach, boolean isChronoFlakToo) {
        this.damagePerFlux = damagePerFlux;
        this.maxFluxLevelToFire = maxFluxLevelToFire;
        this.fluxLevelToReach = fluxLevelToReach;
        this.isChronoFlakToo = isChronoFlakToo;
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //Never run without a ship
        if (weapon.getShip() == null || weapon.getShip().isHulk()) {
            return;
        }

        //Ensure cooldown is locked to flux level
        if (weapon.getShip().getFluxLevel() <= maxFluxLevelToFire) {
            weapon.setRemainingCooldownTo(0f);
        } else if (weapon.getShip().getFluxLevel() >= fluxLevelToReach) {
            weapon.setRemainingCooldownTo(weapon.getCooldown());
        } else {
            float reloadProgress = (weapon.getShip().getFluxLevel() - maxFluxLevelToFire) / (fluxLevelToReach - maxFluxLevelToFire);
            weapon.setRemainingCooldownTo(reloadProgress);
        }

        //Keeps our projectile list somewhere we can access it stateless: also access it
        ProjList alreadyAffectedProjectiles = null;
        Object response = engine.getCustomData().get("VassRandomPrototypeSuperchargedProjList");
        if (response instanceof ProjList) {
            alreadyAffectedProjectiles = (ProjList)response;
        } else {
            alreadyAffectedProjectiles = new ProjList();
            engine.getCustomData().put("VassRandomPrototypeSuperchargedProjList", alreadyAffectedProjectiles);
        }

        //Find all our projectiles that aren't our "fake" projectiles
        for (CombatEntityAPI entity : CombatUtils.getEntitiesWithinRange(weapon.getLocation(), 400f)) {
            if (!(entity instanceof DamagingProjectileAPI)) {
                continue;
            }
            DamagingProjectileAPI proj = (DamagingProjectileAPI)entity;

            //Only run once per projectile
            if (alreadyAffectedProjectiles.contains(proj)) {
                continue;
            }

            //Ignore our fake projectiles
            if (("vass_fake_prototype_shot").equals(proj.getProjectileSpecId())) {
                continue;
            }

            //If the projectile is our own, we can do something with it
            if (proj.getWeapon() == weapon) {
                //Register that we've triggered on the projectile
                alreadyAffectedProjectiles.add(proj);

                //Adjust damage and spew out flux accordingly
                float fluxToEat = (weapon.getShip().getMaxFlux() * fluxLevelToReach) - (weapon.getShip().getMaxFlux() * weapon.getShip().getFluxLevel());
                weapon.getShip().getFluxTracker().increaseFlux(fluxToEat, false);
                proj.setDamageAmount(proj.getDamageAmount()+fluxToEat*damagePerFlux);
                if (proj.getAI() instanceof ProximityFuseAIAPI) {
                    ((ProximityFuseAIAPI) proj.getAI()).updateDamage();
                }

                //Also, if we are chronoflak, apply that as well and adjust damage/type
                if (isChronoFlakToo) {
                    VassRandomPrototypeChronoFlakScript.applyEffectOnProjectile(engine, proj);
                }
            }
        }

        //Also, we clean up our already triggered projectiles when they stop being loaded into the engine
        ProjList toRemove = new ProjList();
        for (DamagingProjectileAPI proj : alreadyAffectedProjectiles) {
            if (!engine.isEntityInPlay(proj)) {
                toRemove.add(proj);
            }
        }
        alreadyAffectedProjectiles.removeAll(toRemove);
        toRemove.clear();
    }

    //For type-safety reasons and nothing more
    private class ProjList extends HashSet<DamagingProjectileAPI> {}
}