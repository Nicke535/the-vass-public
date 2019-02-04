package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class VassHastaFireScript implements EveryFrameWeaponEffectPlugin {

	private final float CHARGE_REDUCTION = 5f;
	private final int SHOT_COUNT = 3;
	private final float SPEED_VARIATION = 0.35f;
	private final float ANGLE_VARIATION = 7f;
	private final float DAMAGE_MULT = 1f;

	private final String[] POTENTIAL_WEAPONS = {"vass_hasta_r", "vass_hasta_g", "vass_hasta_b", "vass_hasta_rg", "vass_hasta_gb", "vass_hasta_br", "vass_hasta_rgb", "vass_hasta_black"};

    private boolean runOnce = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // Don't bother with any checks if the game is paused
        if (engine.isPaused()) {
            return;
        }

        ShipAPI ship = weapon.getShip();
		//...or if this ship is not the Makhaira...
		if (!ship.getHullSpec().getHullId().contains("vass_makhaira")) {
			return;
		}

		//...or if we don't have any charge for our ship system
		if (!(Global.getCombatEngine().getCustomData().get("VassIsochronalCharges" + ship.getId()) instanceof Float)) {
			Global.getCombatEngine().getCustomData().put("VassIsochronalCharges" + ship.getId(), 0f);
		}
		float charges = (float)Global.getCombatEngine().getCustomData().get("VassIsochronalCharges" + ship.getId());
		if (charges <= 0f) {
			return;
		} else {
			//All weapons need to keep track of the display for charge level
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().maintainStatusForPlayerShip("VassIsochronalChargesDebugInfoID","graphics/vass/icons/hullsys/isochronal_multilinker.png", "Isochronal Multilinker", "" + (int)((float)Global.getCombatEngine().getCustomData().get("VassIsochronalCharges" + ship.getId())) + "% charge remaining", false);
			}
		}

		if (runOnce) {
		    runOnce = false;
		    return;
        } else {
		    runOnce = true;
        }

		//Otherwise, search for applicable projectiles
		for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(weapon.getLocation(), 30f)) {
			//Saves some memory, and makes the rest of the code slightly more compact
			if (proj.getProjectileSpecId() == null || proj.getSource() != ship || proj.getWeapon() != weapon) {
				continue;
			}

			//Make sure to only count Hasta projectiles
			if (!proj.getProjectileSpecId().contains("vass_hasta_shot")) {
                continue;
            }

            //Once we find a projectile, remove charge from our counter
            Global.getCombatEngine().getCustomData().put("VassIsochronalCharges" + ship.getId(), charges - CHARGE_REDUCTION);

            Vector2f loc = proj.getLocation();
            //Stores the data all projectiles needs anyway
            float projAngle = proj.getFacing();
            float projDamage = proj.getDamageAmount();
            //Spawns the extra shots
            for (int i = 0; i < SHOT_COUNT; i++) {
                String projWeaponName = POTENTIAL_WEAPONS[MathUtils.getRandomNumberInRange(0, POTENTIAL_WEAPONS.length-1)];
                float angleOffset = MathUtils.getRandomNumberInRange(-ANGLE_VARIATION / 2, ANGLE_VARIATION / 2) + MathUtils.getRandomNumberInRange(-ANGLE_VARIATION / 2, ANGLE_VARIATION / 2);
                DamagingProjectileAPI newProj = (DamagingProjectileAPI)engine.spawnProjectile(ship, weapon, projWeaponName, loc, projAngle + angleOffset, ship.getVelocity());
                //Corrects the damage
                newProj.setDamageAmount(projDamage * DAMAGE_MULT);
                //Varies the speed slightly, for a more shrapnel-y look
                float rand = MathUtils.getRandomNumberInRange(1-SPEED_VARIATION, 1+SPEED_VARIATION);
                newProj.getVelocity().x *= rand;
                newProj.getVelocity().y *= rand;
            }
            //Makes a sound when splitting
            Global.getSoundPlayer().playSound("vass_isochronal_split_hasta", 1f, 1f, loc, ship.getVelocity());
            //Removes the original projectile
            engine.removeEntity(proj);
            break;
		}
    }
}