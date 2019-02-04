package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import data.scripts.utils.VassFakeBeam;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class VassFauchardFireScript implements EveryFrameWeaponEffectPlugin {

	private final float CHARGE_REDUCTION = 20f;
	private final int BEAM_COUNT = 5;
	private final float ANGLE_VARIATION = 4f;
	private final float DAMAGE_MULT_BONUS = 3f;

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

		//Only runs once per fire sequence
		if (runOnce && weapon.getChargeLevel() > 0.9f) {
		    runOnce = false;
			Global.getCombatEngine().getCustomData().put("VassIsochronalCharges" + ship.getId(), charges - CHARGE_REDUCTION);
			Global.getSoundPlayer().playSound("vass_isochronal_split_fauchard", 1f, 1f, weapon.getLocation(), ship.getVelocity());
        } else if (weapon.getChargeLevel() < 0.5f){
		    runOnce = true;
		    return;
        } else {
			return;
		}

		//Main script: shoots a bunch of bonus lasers
		for (int i = 0; i < BEAM_COUNT; i++) {
			Color  randomColor = new Color(0.8f + (float)(Math.random() * 0.2f), 0.8f + (float)(Math.random() * 0.2f), 0.8f + (float)(Math.random() * 0.2f));
			float randomAngle = (float)MathUtils.getRandomNumberInRange(-ANGLE_VARIATION/2, ANGLE_VARIATION/2);
			Vector2f shotOffset = new Vector2f(0f, 0f);
			for (Vector2f offset : weapon.getSpec().getTurretFireOffsets()) {
				shotOffset = VectorUtils.rotate(offset, weapon.getCurrAngle(), new Vector2f(0f, 0f));
			}

			VassFakeBeam.applyFakeBeamEffect(engine, Vector2f.add(weapon.getLocation(), shotOffset, new Vector2f(0f, 0f)), weapon.getRange(), weapon.getCurrAngle() + randomAngle, 5f, 0.3f,
											(weapon.getDamage().getDamage() * 0.1f * DAMAGE_MULT_BONUS / BEAM_COUNT), DamageType.HIGH_EXPLOSIVE, 0f, weapon.getShip(), 8f, 0.1f, randomColor);
		}
    }
}