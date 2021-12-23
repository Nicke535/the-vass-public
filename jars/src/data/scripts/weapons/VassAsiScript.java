package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.plugins.MagicTrailPlugin;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Accelerates a projectile after a certain time, and spawns a trail
 * Only works on non-ballistic-as-beam
 * @author Nicke535
 */
public class VassAsiScript implements EveryFrameWeaponEffectPlugin {

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
            if (proj.getWeapon() == weapon && !proj.didDamage() && engine.isEntityInPlay(proj)) {
                //If the projectile is a "slow" projectile (probably has been cloned) we don't need to switch out the projectile, only add the plugin
                if (proj.getProjectileSpecId().equals("vass_asi_shot_slow")) {
                    alreadyTriggeredProjectiles.add(proj);
                    engine.addPlugin(new AsiTrailAccelPlugin(proj));
                } else {
                    DamagingProjectileAPI newProj = (DamagingProjectileAPI) Global.getCombatEngine().spawnProjectile(proj.getSource(), proj.getWeapon(),
                            "vass_asi_fake1", proj.getLocation(), proj.getFacing(), weapon.getShip().getVelocity());
                    Global.getCombatEngine().removeEntity(proj);

                    //Register that we've triggered on the projectile
                    alreadyTriggeredProjectiles.add(proj);
                    alreadyTriggeredProjectiles.add(newProj);

                    //Add a new plugin that keeps track of the projectile
                    engine.addPlugin(new AsiTrailAccelPlugin(newProj));
                }
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

    //Class for tracking the acceleration and trails of a single projectile
    class AsiTrailAccelPlugin extends BaseEveryFrameCombatPlugin {
        DamagingProjectileAPI proj;
        float currentTrailID;
        boolean hasAccelerated = false;
        float timer = 0f;
        float estimatedAccelPoint;
        Vector2f offsetVelocity;

        AsiTrailAccelPlugin (DamagingProjectileAPI proj) {
            this.proj = proj;
            currentTrailID = MagicTrailPlugin.getUniqueID();
            offsetVelocity = new Vector2f(proj.getSource().getVelocity());
            estimatedAccelPoint = (proj.getWeapon().getRange() / proj.getWeapon().getProjectileSpeed()) * 0.7f;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            //Don't run when paused
            if (Global.getCombatEngine().isPaused()) {
                return;
            }

            //Advance our timer
            timer += amount;
            if (proj.didDamage() || !Global.getCombatEngine().isEntityInPlay(proj)) {
                Global.getCombatEngine().removePlugin(this);
            }

            //If past our accel point, spawn a new shot with higher SPEED
            if (timer > estimatedAccelPoint && !hasAccelerated) {
                DamagingProjectileAPI newProj = (DamagingProjectileAPI) Global.getCombatEngine().spawnProjectile(proj.getSource(), proj.getWeapon(),
                        "vass_asi_fake2", proj.getLocation(), proj.getFacing(), offsetVelocity);
                Global.getCombatEngine().removeEntity(proj);
                proj = newProj;
                alreadyTriggeredProjectiles.add(proj);
                currentTrailID = MagicTrailPlugin.getUniqueID();
                hasAccelerated = true;

                //Spawn an extra glow!
                Global.getCombatEngine().addHitParticle(proj.getLocation(), offsetVelocity, 32f,
                        1f, 0.15f, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.ACCEL, 1f));

                //Also play sound, because people love sound cues!
                Global.getSoundPlayer().playSound("vass_asi_acceleration", 1f, 1f, new Vector2f(proj.getLocation()), Misc.ZERO);
            }

            if (!hasAccelerated) {
                //We're not accelerated, so spawn a growing glow, sorta
                Global.getCombatEngine().addHitParticle(proj.getLocation(), proj.getVelocity(), 27f * timer / estimatedAccelPoint,
                                                        1f, amount*2f, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.TORPOR, 1f));
            } else {
                //When accelerated: adds a new trail piece to the projectile. Do this semi-randomly at low global time mult
                if (Math.random() < Math.sqrt(Global.getCombatEngine().getTimeMult().getModifiedValue())) {
                    Color colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.ACCEL, 1f);
                    MagicTrailPlugin.AddTrailMemberSimple(proj, currentTrailID, Global.getSettings().getSprite("vass_fx", "projectile_trail_zappy"),
                            proj.getLocation(), 0f, proj.getFacing(), 15f, 8f, colorToUse, 0.3f,
                            0.6f,true, offsetVelocity, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER); //TODO: Alter to the Advanced version, since the simple version no longer supports engine layers
                }
            }
        }
    }
}