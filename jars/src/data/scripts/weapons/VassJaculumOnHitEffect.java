package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class VassJaculumOnHitEffect implements OnHitEffectPlugin {

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, CombatEngineAPI engine) {
        if (target == null) {
            return;
        }

        engine.applyDamage(target, point, projectile.getDamageAmount() * 0.5f, DamageType.FRAGMENTATION, projectile.getDamageAmount() * 0.5f, false, false, projectile.getSource(), true);
    }
}
