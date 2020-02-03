package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingIntel;
import data.scripts.utils.VassPerturbaRandomPrototypeManager.PrototypeWeaponData;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static data.scripts.campaign.barEvents.VassPerturbaWeaponTestingIntel.MEM_KEY_PROTOTYPE_WAS_IN_COMBAT;

/**
 * Gives random bonuses to a weapon, depending on current quest setup
 * @author Nicke535
 */
public class VassRandomPrototypeScript implements EveryFrameWeaponEffectPlugin {
    private List<DamagingProjectileAPI> alreadyRegisteredProjectiles = new ArrayList<>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //Failsafe : refit screen and more!
        if (engine == null || !engine.isEntityInPlay(weapon.getShip())) {
            return;
        }

        //Instantly disable in simulation or missions
        if ((engine.isSimulation() || !engine.isInCampaign()) && !weapon.isDisabled()) {
            weapon.disable(true);
            engine.addFloatingText(weapon.getLocation(), "No simulation data available!", 20f, Color.RED,
                    weapon.getShip(), 0.5f, 5f);
            return;
        }

        //Gets the randomized weapon data currently in use
        Object dataObject = Global.getSector().getMemoryWithoutUpdate().get(VassPerturbaWeaponTestingIntel.MEM_KEY_PROTOTYPE_DATA);
        if (!(dataObject instanceof PrototypeWeaponData)) {
            if (!weapon.isDisabled()) {
                weapon.disable(true);
                engine.addFloatingText(weapon.getLocation(), "Critical malfunction!", 20f, Color.RED,
                        weapon.getShip(), 0.5f, 5f);
                engine.applyDamage(weapon.getShip(), weapon.getLocation(), 200f, DamageType.HIGH_EXPLOSIVE,
                        0f, true, false, weapon.getShip(), true);
            }
            return;
        }
        PrototypeWeaponData currentData = (PrototypeWeaponData)dataObject;

        //Register that we have indeed participated in a battle
        Global.getSector().getMemoryWithoutUpdate().set(MEM_KEY_PROTOTYPE_WAS_IN_COMBAT, true);

        //If our current remaining cooldown is above our "actual" cooldown, bring it down to the true cooldown
        if (weapon.getCooldownRemaining() > (weapon.getCooldown()*currentData.reloadMult)) {
            weapon.setRemainingCooldownTo((weapon.getCooldown()*currentData.reloadMult));
        }

        //If we're supposed to be PD, fix that
        weapon.setPD(currentData.pd);

        for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(weapon.getLocation(), 200f)) {
            //Projectile replacement! Only for our "initial" fake projectile, though
            if (("vass_fake_prototype_shot").equals(proj.getProjectileSpecId()) &&
                    proj.getWeapon() == weapon && !alreadyRegisteredProjectiles.contains(proj)
                    && engine.isEntityInPlay(proj) && !proj.didDamage()) {
                float shotgunningLeft = currentData.shotgunFactor;
                while (Math.random() < shotgunningLeft) {
                    shotgunningLeft--;
                    DamagingProjectileAPI newProj = (DamagingProjectileAPI) engine.spawnProjectile(weapon.getShip(), weapon,
                            currentData.projectileWeaponId, proj.getLocation(),
                            proj.getFacing()+ MathUtils.getRandomNumberInRange(-currentData.inaccuracy, currentData.inaccuracy),
                            null);
                    newProj.setDamageAmount(weapon.getDamage().getDamage()*currentData.damageMult/currentData.shotgunFactor);
                    if (newProj.getAI() instanceof ProximityFuseAIAPI) {
                        ((ProximityFuseAIAPI) newProj.getAI()).updateDamage();
                    }
                    float speedMultThisShot = 1f + MathUtils.getRandomNumberInRange(-1f, 1f) * currentData.speedVariation;
                    newProj.getVelocity().x = newProj.getVelocity().x * speedMultThisShot + weapon.getShip().getVelocity().x;
                    newProj.getVelocity().y = newProj.getVelocity().y * speedMultThisShot + weapon.getShip().getVelocity().y;
                    Global.getSoundPlayer().playSound(currentData.sound, 1f, 1f, proj.getLocation(), weapon.getShip().getVelocity());

                    //We use guidance, if supplied
                    if (currentData.guidance != null) {
                        currentData.guidance.applyToProjectile(newProj);
                    }
                    alreadyRegisteredProjectiles.add(newProj);
                }

                alreadyRegisteredProjectiles.add(proj);
                engine.removeEntity(proj);
            }
        }

        List<DamagingProjectileAPI> cloneList = new ArrayList<>(alreadyRegisteredProjectiles);
        for (DamagingProjectileAPI proj : cloneList) {
            if (!engine.isEntityInPlay(proj) || proj.didDamage()) {
                alreadyRegisteredProjectiles.remove(proj);
            }
        }

        //Also, always run our effect script
        if (currentData.weaponEffectPlugin != null) {
            currentData.weaponEffectPlugin.advance(amount, engine, weapon);
        }
    }
}