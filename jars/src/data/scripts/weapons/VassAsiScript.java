package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.plugins.MagicTrailPlugin;
import data.scripts.utils.VassTimeDistortionProjScript;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;
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
                engine.addPlugin(new VassTimeDistortionProjScript(proj, MathUtils.getRandomNumberInRange(0.6f, 1.65f), "vass_tizona_detonation"));

                //Re-orient the projectile slightly for a more spread-out look
                proj.getLocation().set(MathUtils.getRandomPointInCircle(proj.getLocation(), 5f));

                //Randomly decrease the projectile's lifetime slightly (if it's a missile)
                if (proj instanceof MissileAPI) {
                    ((MissileAPI) proj).setFlightTime(MathUtils.getRandomNumberInRange(0f, 0.2f));
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
        Vector2f currentOffsetVelocity;

        AsiTrailAccelPlugin (DamagingProjectileAPI proj) {
            this.proj = proj;
            currentTrailID = MagicTrailPlugin.getUniqueID();
            currentOffsetVelocity = proj.getSource().getVelocity();
            estimatedAccelPoint = (proj.getWeapon().getRange() / proj.getWeapon().getProjectileSpeed()) * 0.2f;
            proj.getVelocity().x *= 0.2f;
            proj.getVelocity().y *= 0.2f;
            currentOffsetVelocity.x *= 0.2f;
            currentOffsetVelocity.y *= 0.2f;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            //Advance our timer
            timer += amount;

            if (timer > estimatedAccelPoint && !hasAccelerated) {
                proj.getVelocity().x *= 6f;
                proj.getVelocity().y *= 6f;
                currentOffsetVelocity.x *= 6f;
                currentOffsetVelocity.y *= 6f;
                hasAccelerated = true;
            }

            //Adds a new trail piece to the projectile: do this semi-randomly at low high global time mult
            if (Math.random() < Math.sqrt(Global.getCombatEngine().getTimeMult().getModifiedValue())) {
                Color colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.TORPOR, 1f);
                if (hasAccelerated) {
                    colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.ACCEL, 1f);
                }
                MagicTrailPlugin.AddTrailMemberSimple(proj, currentTrailID, Global.getSettings().getSprite("vass_fx", "projectile_trail_zappy"),
                        proj.getLocation(), 0f, proj.getFacing(), 5f, 3f, colorToUse, 0.3f, 0.3f,
                        true, currentOffsetVelocity, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
            }
        }
    }
}