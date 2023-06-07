package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicRender;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

/**
 * @deprecated
 */
public class VassJaculumFireScript implements EveryFrameWeaponEffectPlugin {
    private boolean runOnce = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ShipAPI ship = weapon.getShip();
        if (ship == null) {
        	return;
		}

		if (weapon.getCooldownRemaining() <= 4f && weapon.getCooldownRemaining() > 0f) {
        	for (int i = 0; i < 4; i++) {
				float alpha = ((4f - weapon.getCooldownRemaining()) / 4f) * 0.25f;
				Color randomColor = new Color(MathUtils.getRandomNumberInRange(0.7f, 1f), MathUtils.getRandomNumberInRange(0.7f, 1f), MathUtils.getRandomNumberInRange(0.7f, 1f), alpha);
				SpriteAPI spriteToRender = Global.getSettings().getSprite("vass_fx", "jaculum_reversal_image");
				Vector2f posToRender = MathUtils.getRandomPointInCircle(weapon.getLocation(), 3 * weapon.getCooldownRemaining());

				MagicRender.singleframe(spriteToRender, posToRender, new Vector2f(15f, 29f), weapon.getCurrAngle() - 90, randomColor, true);
			}

			//Once per reload, play our reload sound
			if (weapon.getCooldownRemaining() < engine.getTimeMult().getModifiedValue() * ship.getMutableStats().getMissileRoFMult().getModifiedValue() && runOnce) {
				runOnce = false;
				Global.getSoundPlayer().playSound("vass_jaculum_reload", 1f, 1f, weapon.getLocation(), ship.getVelocity());
			}
		} else {
        	runOnce = true;
		}
    }
}