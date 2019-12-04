package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.plugins.MagicTrailPlugin;
import data.scripts.util.MagicAnim;
import data.scripts.util.MagicRender;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Ensures that a weapon can only fire when it has no projectiles mid-flight, and handles animation
 * Uses various tricks I wish i didn't need, so please do yourself a favor and come up with a better solution rather than copying this
 * @author Nicke535
 */
public class VassCaladbolgScript implements EveryFrameWeaponEffectPlugin {

    //Number of frames that's part animation
    private static final int NUMBER_FRAMES = 46;

    //Keeps track of our current in-flight projectile
    private DamagingProjectileAPI currentProj = null;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        //If we already have a projectile, we check if it has disappeared yet
        if (currentProj != null) {
            if (currentProj.didDamage() || !engine.isEntityInPlay(currentProj)) {
                currentProj = null;
            }
        }

        //If we don't, we find all projectiles we might be firing and take the first we found (this thing is only allowed to have one projectile mid-air, after all)
        else {
            for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(weapon.getLocation(), 400f)) {
                if (proj.getWeapon() == weapon && !proj.didDamage() && engine.isEntityInPlay(proj)) {
                    currentProj = proj;
                    //TODO: wonderful place for some TRAILS, wouldn't you say?
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
}