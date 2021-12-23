package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.plugins.MagicAutoTrails;
import data.scripts.plugins.MagicTrailPlugin;
import data.scripts.util.MagicAnim;
import data.scripts.util.MagicRender;
import data.scripts.util.MagicTrailTracker;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Ensures that a weapon can only fire when it has no projectiles mid-flight, and handles animation
 * Uses various tricks I wish i didn't need, so please do yourself a favor and come up with a better solution rather than copying this
 * @author Nicke535
 */
public class VassCaladbolgScript implements EveryFrameWeaponEffectPlugin {

    //Number of frames that's part animation
    private static final int NUMBER_FRAMES = 36;

    //Keeps track of our current in-flight projectile
    private DamagingProjectileAPI currentProj = null;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        //If we already have a projectile, we check if it has disappeared yet
        if (currentProj != null) {
            if (currentProj.didDamage() || !engine.isEntityInPlay(currentProj)) {
                //If the projectile has disappeared, we want to spawn a trail going back to the weapon
                spawnRecallTrail(weapon);
                currentProj = null;
            }
        }

        //If we don't, we find all projectiles we might be firing and take the first we found (this thing is only allowed to have one projectile mid-air, after all)
        //Also add a trail tracker, because I like me some trails
        else {
            for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(weapon.getLocation(), 400f)) {
                if (proj.getWeapon() == weapon && !proj.didDamage() && engine.isEntityInPlay(proj)) {
                    currentProj = proj;
                    engine.addPlugin(new CaladbolgTrailTracker(proj, weapon.getShip().getVelocity()));
                    break;
                }
            }
        }


        //Either way, we also handle animation and maybe prevent shooting, depending on state
        if (currentProj != null) {
            //Wait-for-reload state: don't render ammo, slide our bolt back, and don't allow shooting
            weapon.getAnimation().setFrame(1);
            weapon.setRemainingCooldownTo(weapon.getCooldown());
        }

        else {
            float reloadProgressLeft = weapon.getCooldownRemaining()/weapon.getCooldown();
            //Either reloaded or reloading: animate reload sequence
            if (reloadProgressLeft > 0f) {
                int frameThisFrame = (int)Math.floor((1f - reloadProgressLeft) * NUMBER_FRAMES) + 2; //Offset by 2, first two frames are special resting frames
                weapon.getAnimation().setFrame(frameThisFrame);
            } else {
                weapon.getAnimation().setFrame(0);
            }
        }
    }

    //Spawns the "recall" trail to the projectile
    private void spawnRecallTrail(WeaponAPI weapon) {
        float distanceToTarget = MathUtils.getDistance(weapon.getLocation(), currentProj.getLocation());
        List<Vector2f> pointsForArc =
                VassUtils.getFancyArcPoints(
                        currentProj.getLocation(),
                        weapon.getLocation(),
                        MathUtils.getRandomNumberInRange(-distanceToTarget*0.05f, distanceToTarget*0.05f),
                        (int)Math.floor(Math.max(36f, distanceToTarget/10f)));

        SpriteAPI spriteToUse = Global.getSettings().getSprite("vass_fx", "caladbolg_recall_proj");

        //Start actually rendering the trail : note that we render one point shorter than the actual trail, to always have a valid direction to next point
        for (int i = 0; i < pointsForArc.size(); i++) {
            float opacity = 0.18f;
            if (i < pointsForArc.size()/2) {
                opacity *= ((pointsForArc.size()/2) - i)/(pointsForArc.size()/2f);
            } else {
                opacity *= (i - (pointsForArc.size()/2))/(pointsForArc.size()/2f);
            }
            float progress = (i/(float)pointsForArc.size());
            float lifetimeThisImage = 0.15f + 0.3f * progress;
            Vector2f velocity = Misc.interpolateVector(new Vector2f(0f, 0f), weapon.getShip().getVelocity(), progress);
            float angle = Misc.interpolate(currentProj.getFacing(), weapon.getCurrAngle(), progress);
            MagicRender.battlespace(spriteToUse, pointsForArc.get(i), velocity,
                    new Vector2f(spriteToUse.getWidth(), spriteToUse.getHeight()),Misc.ZERO,
                    angle-90f, 0f, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.RECIPRO, opacity),
                    true,0f, 0f, lifetimeThisImage);
        }
    }

    private static class CaladbolgTrailTracker extends BaseEveryFrameCombatPlugin {
        private DamagingProjectileAPI proj;
        private Vector2f offsetVelocity;
        private float trailID;
        private final float MAX_TRAIL_SPAWN_FREQUENCY = 0.005f;
        private float tracker = 0f;
        private static final float FADE_OUT_TIME = 0.1f;
        private float fadeTracker = 0f;

        CaladbolgTrailTracker(DamagingProjectileAPI proj, Vector2f offsetVelocity) {
            this.proj = proj;
            this.offsetVelocity = offsetVelocity;
            trailID = MagicTrailPlugin.getUniqueID();
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (Global.getCombatEngine().isPaused()) {
                return;
            }
            if (!Global.getCombatEngine().isEntityInPlay(proj)) {
                Global.getCombatEngine().removePlugin(this);
                return;
            }
            tracker += amount;
            if (proj.isFading()) {fadeTracker += amount;}
            if (tracker > MAX_TRAIL_SPAWN_FREQUENCY) {
                tracker = 0f;

                float opacity = 0.7f * (1f - Math.min(1f, fadeTracker/FADE_OUT_TIME)) + 0.3f;
                Vector2f offsetPoint = new Vector2f((float)Math.cos(Math.toRadians(proj.getFacing())) * 8f, (float)Math.sin(Math.toRadians(proj.getFacing())) * 8f);
                Vector2f spawnLocation = Vector2f.add(offsetPoint, proj.getLocation(), new Vector2f(0f, 0f));

                MagicTrailPlugin.AddTrailMemberAdvanced(proj, trailID, Global.getSettings().getSprite("fx", "base_trail_smooth"),
                        spawnLocation,0f, MathUtils.getRandomNumberInRange(50f, 250f), proj.getFacing(), 0f, MathUtils.getRandomNumberInRange(-250f, 250f),
                        18f, 10f, new Color(255f/255f, 150f/255f, 70f/255f, 1f),
                        new Color(120f/255f, 120f/255f, 120f/255f, 0f), opacity,
                        0f, 0f, 0.3f, GL_SRC_ALPHA, GL_ONE,
                        256f, 0f, -1f, offsetVelocity, null,
                        CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER, 1f); //TODO: Check if the texture offset messes with something
            }
        }
    }
}