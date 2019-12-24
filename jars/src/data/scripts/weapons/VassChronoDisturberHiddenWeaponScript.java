package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import data.scripts.shipsystems.VassChronoDisturber;
import data.scripts.util.MagicRender;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

/**
 * @deprecated
 */
public class VassChronoDisturberHiddenWeaponScript implements EveryFrameWeaponEffectPlugin {

    private float rotation = 0f;
    private static final float TURNRATE = 400f;
    private int numberOfTriangles = 9;

    private float lightningChance = 0.97f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (ship == null || ship.isHulk()) {
            return;
        }
        ShipSystemAPI system = ship.getSystem();

        if (system.getEffectLevel() > 0f) {
            //Sets the color for the sprite this frame: we need to do this to adjust sprite opacity all the time
            Color spriteColor1 = new Color((int)(VassChronoDisturber.JITTER_COLOR.getRed() * system.getEffectLevel() * 0.15f), (int)(VassChronoDisturber.JITTER_COLOR.getGreen() * system.getEffectLevel() * 0.15f),
                    (int)(VassChronoDisturber.JITTER_COLOR.getBlue() * system.getEffectLevel() * 0.15f));

            //Runs several times to create a "circle" from the triangles
            for (int i = 0; i < numberOfTriangles; i++) {
                MagicRender.singleframe(Global.getSettings().getSprite("vass_fx", "chrono_disturber_ringpiece"), ship.getLocation(),
                        new Vector2f(VassChronoDisturber.ACTIVE_RANGE * 2f + ship.getCollisionRadius(), VassChronoDisturber.ACTIVE_RANGE * 2f + ship.getCollisionRadius()), rotation, spriteColor1, true, CombatEngineLayers.BELOW_SHIPS_LAYER);
                rotation += (360 / numberOfTriangles);
            }

            //Only rotate/ create lightning when not paused
            if (engine.isPaused()) {
                return;
            }
            rotation += TURNRATE*amount;
            rotation = MathUtils.clampAngle(rotation);

            if (Math.random() > (Math.pow((1 - lightningChance), amount))) {
                Vector2f pos = MathUtils.getRandomPointInCircle(ship.getLocation(), VassChronoDisturber.ACTIVE_RANGE + (ship.getCollisionRadius()/2f));
                CombatEntityAPI target = new SimpleEntity(MathUtils.getRandomPointInCircle(pos, (ship.getCollisionRadius()/2f)));
                engine.spawnEmpArc(ship, pos, null, target, DamageType.ENERGY, 0f, 0f, 0f, "", (float)(Math.random() * 20f), VassChronoDisturber.JITTER_COLOR, Color.WHITE);
            }
        }
    }
}