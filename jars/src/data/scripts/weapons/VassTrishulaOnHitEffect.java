package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class VassTrishulaOnHitEffect implements OnHitEffectPlugin {

    private final float PARTICLE_SPREAD = 60f;
    private final int PARTICLE_AMOUNT = 70;
    private final float PARTICLE_MIN_SIZE = 5f;
    private final float PARTICLE_MAX_SIZE = 20f;
    private final Color PARTICLE_COLOR = new Color(60,255,18,180);
    private final float PARTICLE_MIN_SPEED = 50f;
    private final float PARTICLE_MAX_SPEED = 350f;
    private final float PARTICLE_MIN_BRIGHTNESS = 15f;
    private final float PARTICLE_MAX_BRIGHTNESS = 25f;
    private final float PARTICLE_MIN_DURATION = 0.7f;
    private final float PARTICLE_MAX_DURATION = 1.5f;

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, CombatEngineAPI engine) {
        float partAngleBase = projectile.getFacing() + 180f;
        for (int i = 0; i < PARTICLE_AMOUNT; i++) {
            float partBrightness = MathUtils.getRandomNumberInRange(PARTICLE_MIN_BRIGHTNESS, PARTICLE_MAX_BRIGHTNESS);
            float partSize = MathUtils.getRandomNumberInRange(PARTICLE_MIN_SIZE, PARTICLE_MAX_SIZE);
            float partDuration = MathUtils.getRandomNumberInRange(PARTICLE_MIN_DURATION, PARTICLE_MAX_DURATION);

            float partAngle = partAngleBase + (float)(Math.random() - 0.5f) * PARTICLE_SPREAD * (float)(Math.random());
            Vector2f partVel = new Vector2f((float)Math.cos(Math.toRadians(partAngle)), (float)Math.sin(Math.toRadians(partAngle)));
            partVel.scale(MathUtils.getRandomNumberInRange(PARTICLE_MIN_SPEED, PARTICLE_MAX_SPEED));

            if (Math.random() < 0.7f) {
                engine.addSmoothParticle(point, partVel, partSize, partBrightness, partDuration, PARTICLE_COLOR);
            } else {
                engine.addHitParticle(point, partVel, partSize, partBrightness, partDuration, PARTICLE_COLOR);
            }
        }
    }
}
