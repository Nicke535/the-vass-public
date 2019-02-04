package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import data.scripts.plugins.VassSpriteRenderManager;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class VassJaculumFireScript implements EveryFrameWeaponEffectPlugin {

	private final float CHARGE_REDUCTION_PER_SECOND = 30f;
	private final int RELOAD_MULT = 17;

    private boolean runOnce = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ShipAPI ship = weapon.getShip();
        if (ship == null) {
        	return;
		}

		//-------------------------------------------------------------------------------------Reload Animation---------------------------------------------------------------------------------------------------
		if (weapon.getCooldownRemaining() <= 4f && weapon.getCooldownRemaining() > 0f) {
        	for (int i = 0; i < 4; i++) {
				float alpha = ((4f - weapon.getCooldownRemaining()) / 4f) * 0.25f;
				Color randomColor = new Color(MathUtils.getRandomNumberInRange(0.7f, 1f), MathUtils.getRandomNumberInRange(0.7f, 1f), MathUtils.getRandomNumberInRange(0.7f, 1f), alpha);
				SpriteAPI spriteToRender = Global.getSettings().getSprite("vass_fx", "jaculum_reversal_image");
				Vector2f posToRender = MathUtils.getRandomPointInCircle(weapon.getLocation(), 3 * weapon.getCooldownRemaining());

				VassSpriteRenderManager.singleFrameRender(spriteToRender, posToRender, new Vector2f(15f, 29f), weapon.getCurrAngle() - 90, randomColor, true);
			}

			//Once per reload, play our reload sound
			if (weapon.getCooldownRemaining() < engine.getTimeMult().getModifiedValue() * ship.getMutableStats().getMissileRoFMult().getModifiedValue() && runOnce) {
				runOnce = false;
				Global.getSoundPlayer().playSound("vass_jaculum_reload", 1f, 1f, weapon.getLocation(), ship.getVelocity());
			}
		} else {
        	runOnce = true;
		}


        //----------------------------------------------------------------------------------Isochronal Multilinker------------------------------------------------------------------------------------------------
		//Don't run the code for the Isochronal Multilinker if our ship is not the Makhaira
		if (!ship.getHullSpec().getHullId().contains("vass_makhaira")) {
			return;
		}

		//...or if we don't have any charge for our ship system
		if (!(Global.getCombatEngine().getCustomData().get("VassIsochronalCharges" + ship.getId()) instanceof Float)) {
			Global.getCombatEngine().getCustomData().put("VassIsochronalCharges" + ship.getId(), 0f);
		}
		float charges = (float)Global.getCombatEngine().getCustomData().get("VassIsochronalCharges" + ship.getId());
		if (charges <= 0f) {
			ship.getMutableStats().getMissileRoFMult().unmodify("VassIsochronalMultilinkerBonusID");
			return;
		} else {
			//All weapons need to keep track of the display for charge level
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().maintainStatusForPlayerShip("VassIsochronalChargesDebugInfoID","graphics/vass/icons/hullsys/isochronal_multilinker.png", "Isochronal Multilinker", "" + (int)((float)Global.getCombatEngine().getCustomData().get("VassIsochronalCharges" + ship.getId())) + "% charge remaining", false);
			}
		}

		//Only runs when our weapon is reloading
		if (weapon.getChargeLevel() > 0f) {
			ship.getMutableStats().getMissileRoFMult().modifyMult("VassIsochronalMultilinkerBonusID", RELOAD_MULT);
			Global.getCombatEngine().getCustomData().put("VassIsochronalCharges" + ship.getId(), charges - (CHARGE_REDUCTION_PER_SECOND * amount));
		} else {
			ship.getMutableStats().getMissileRoFMult().unmodify("VassIsochronalMultilinkerBonusID");
		}
    }
}