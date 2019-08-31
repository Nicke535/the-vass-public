//By Nicke535
//Accelerates a projectile after a certain time, and spawns a trail
//Only works on non-ballistic-as-beam
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
            if (proj.getWeapon() == weapon) {
                //Register that we've triggered on the projectile
                alreadyTriggeredProjectiles.add(proj);

                //Add a new plugin that keeps track of the projectile
                engine.addPlugin(new AsiTrailAccelPlugin(proj));
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
            proj.getVelocity().x -= offsetVelocity.x;
            proj.getVelocity().x *= 0.2f;
            proj.getVelocity().x += offsetVelocity.x;
            proj.getVelocity().y -= offsetVelocity.y;
            proj.getVelocity().y *= 0.2f;
            proj.getVelocity().y += offsetVelocity.y;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            //Advance our timer
            timer += Global.getCombatEngine().isPaused() ? 0f : amount;
            if (proj.didDamage() || !Global.getCombatEngine().isEntityInPlay(proj)) {
                Global.getCombatEngine().removePlugin(this);
            }

            //If past our accel point, *accelerate!*
            if (timer > estimatedAccelPoint && !hasAccelerated) {
                proj.getVelocity().x -= offsetVelocity.x;
                proj.getVelocity().x *= 14.3f;
                proj.getVelocity().x += offsetVelocity.x;
                proj.getVelocity().y -= offsetVelocity.y;
                proj.getVelocity().y *= 14.3f;
                proj.getVelocity().y += offsetVelocity.y;
                currentTrailID = MagicTrailPlugin.getUniqueID();
                hasAccelerated = true;

                //Also play sound, because people love sound cues!
                Global.getSoundPlayer().playSound("vass_asi_acceleration", 1f, 1f, new Vector2f(proj.getLocation()), Misc.ZERO);
            }

            //Adds a new trail piece to the projectile: do this semi-randomly at low global time mult
            if (Math.random() < Math.sqrt(Global.getCombatEngine().getTimeMult().getModifiedValue())) {
                Color colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.TORPOR, 1f);
                if (hasAccelerated) {
                    colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.ACCEL, 1f);
                }
                MagicTrailPlugin.AddTrailMemberSimple(proj, currentTrailID, Global.getSettings().getSprite("vass_fx", "projectile_trail_zappy"),
                        proj.getLocation(), 0f, proj.getFacing(), hasAccelerated ? 10f : 20f, hasAccelerated ? 6f : 12f, colorToUse, hasAccelerated ? 0.3f : 0.7f, hasAccelerated ? 0.3f : 0.7f,
                        true, offsetVelocity, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
            }
        }
    }
}