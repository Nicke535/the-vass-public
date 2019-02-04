//Made by xenoargh, modified by Nicke535
package data.scripts.weapons;  
  
import com.fs.starfarer.api.combat.BeamAPI;  
import com.fs.starfarer.api.combat.WeaponAPI;  
import com.fs.starfarer.api.combat.WeaponAPI.DerivedWeaponStatsAPI;  
import com.fs.starfarer.api.combat.BeamEffectPlugin;  
import com.fs.starfarer.api.combat.CombatEngineAPI;  
import com.fs.starfarer.api.combat.CombatEntityAPI;  
import com.fs.starfarer.api.combat.DamageType;  
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.plugins.VassSpriteRenderManager;

//Causes the a beam to deal additional Hard Flux damage on shields
public class VassFauchardHardFluxDamageScript implements BeamEffectPlugin { 
    public static final float HARD_FLUX_MULT = 0.33f;
	
    @Override  
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (engine.isPaused()) return; //Only runs if we are not paused
		if (!beam.getSource().getVariant().getHullMods().contains("vass_fauchard_timelock_modification")) return; //Only activates if we have the correct hullmod
        float frameTime = engine.getElapsedInLastFrame();
        
        //Get the beam's target  
        CombatEntityAPI target = beam.getDamageTarget();  
  
        //If we have a target, target is a Ship, and shields are being hit
        if (target != null && target instanceof ShipAPI && target.getShield() != null && target.getShield().isWithinArc(beam.getTo())) {
            //Now that we have the target, get the weapon ID and stats 
            WeaponAPI weapon = beam.getWeapon();  
            DamageType damType = weapon.getDamageType();  
            DerivedWeaponStatsAPI stats = weapon.getDerivedStats();

			//Handles the extra damage
            engine.applyDamage(  
                target, //enemy Ship  
                beam.getTo(), //Our 2D vector to the exact world-position  
                600 * frameTime * HARD_FLUX_MULT * beam.getBrightness(), //We're dividing the DPS by the time that's passed here, while also applying the damage multiplier
                damType, //Using the damage type here.  
                0f, //No EMP, as EMP already has specific rules.  However EMP could go through shields this way if we wanted it to.  
                false, //Does not bypass shields.  
                false, //Does not do Soft Flux damage (would kind've defeat the whole point, eh?)  
                beam.getSource()  //Who owns this beam?  
            );
        }  
    }  
}  