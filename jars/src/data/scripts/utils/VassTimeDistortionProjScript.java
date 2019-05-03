//By Nicke535, causes a projectile to detonate into a time-distortion effect when fading, hitting a target or otherwise disappear
package data.scripts.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.VassModPlugin;
import data.scripts.plugins.MagicTrailPlugin;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.WaveDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

public class VassTimeDistortionProjScript extends BaseEveryFrameCombatPlugin {
	//How long do we apply time distortion effects for?
	private static float TIME_DISTORTION_DURATION = 0.55f;

	//How long the visual distortion lasts (with shaderlib)
	private static float VISUAL_DISTORTION_DURATION = 0.4f;

	//Base AoE size, this is then modified by the square root of the damage
	private static float BASE_AOE_SIZE = 6f;

	//The ID of the effect applied by the script (also gets an unique modifier applied)
	private static String EFFECT_ID = "VassTimeDistortKey_";
	private String effectIdSuffix = "";


	//The projectile we are tracking
	private DamagingProjectileAPI proj;

	//The most extreme time distortion we can apply
	private float maxTimeDistortMult;

	//Whether the projectile has already "detonated" or not
	private boolean hasProjectileDetonated = false;

	//How far after the detonation we are, time-wise
	private float counter = 0f;

	//Last frame's list of affected fighters
	private List<ShipAPI> fightersAffectedLastFrame = new ArrayList<>();

	//Initializer
	public VassTimeDistortionProjScript(DamagingProjectileAPI proj, float maxTimeDistortMult) {
		this.proj = proj;
		this.maxTimeDistortMult = maxTimeDistortMult;
		effectIdSuffix = UUID.randomUUID().toString();
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		//Sanity check
		if (Global.getCombatEngine() == null) {
			return;
		}
		if (Global.getCombatEngine().isPaused()) {
			amount = 0f;
		}

		//Checks if our script should be removed from the combat engine
		if (proj == null || counter >= TIME_DISTORTION_DURATION) {
			for (ShipAPI fighter : fightersAffectedLastFrame) {
				fighter.getMutableStats().getTimeMult().unmodify(EFFECT_ID+effectIdSuffix);
			}
			fightersAffectedLastFrame.clear();
			Global.getCombatEngine().removePlugin(this);
			return;
		}

		//Check if the projectile should detonate, unless it already has
		if (!hasProjectileDetonated) {
			//If the projectile has hit something, it should detonate
			if (proj.didDamage()) {
				detonate();
				return;
			}

			//If the projectile has <=0 health, it should also detonate
			if (proj.getHitpoints() <= 0f) {
				detonate();
				return;
			}

			//Finally, we also detonate if our projectile has vanished somehow
			if (!Global.getCombatEngine().isEntityInPlay(proj)) {
				detonate();
			}
		}

		//If it has already detonated, we start counting our counter and applies time distortion effects to all nearby targets
		else {
			counter += amount;

			//Before we apply any effects, reset last frame's effects
			for (ShipAPI fighter : fightersAffectedLastFrame) {
				fighter.getMutableStats().getTimeMult().unmodify(EFFECT_ID+effectIdSuffix);
			}
			fightersAffectedLastFrame.clear();

			//Then, we apply our time mult to all fighters in range
			float damageMult = (float)Math.sqrt(proj.getDamageAmount());
			float timeMultThisFrame = 1f + (maxTimeDistortMult*(1f-(counter/TIME_DISTORTION_DURATION)));
			for (ShipAPI fighter : CombatUtils.getShipsWithinRange(proj.getLocation(), BASE_AOE_SIZE*damageMult*counter/TIME_DISTORTION_DURATION)) {
				//Only affects fighters
				if (fighter.getHullSize() != ShipAPI.HullSize.FIGHTER) {
					continue;
				}

				fighter.getMutableStats().getTimeMult().modifyMult(EFFECT_ID+effectIdSuffix, timeMultThisFrame);
				fightersAffectedLastFrame.add(fighter);
			}
		}
	}

	//Function for detonating the projectile
	private void detonate() {
		//Intensity and size of effects vary by damage of projectile
		float damageMult = (float)Math.sqrt(proj.getDamageAmount());

		//Spawn a particle
		Global.getCombatEngine().addHitParticle(new Vector2f(proj.getLocation()), Misc.ZERO, BASE_AOE_SIZE*damageMult*1.5f,
				1f, VISUAL_DISTORTION_DURATION, new Color(50,255,50));

		//Spawns a few fancy trails on high-damage blasts for good measure
		SpriteAPI spriteToUse = Global.getSettings().getSprite("vass_fx","projectile_trail_zappy");
		for (float i1 = 1; i1 < 0.65f*damageMult; i1 += MathUtils.getRandomNumberInRange(0.7f, 1.2f)) {
			float id = MagicTrailPlugin.getUniqueID();
			float angle = MathUtils.getRandomNumberInRange(0f, 360f);
			float startSpeed = MathUtils.getRandomNumberInRange(5f, 50f) * damageMult;
			float startAngularVelocity = MathUtils.getRandomNumberInRange(-6f, 6f) * damageMult;
			float startSize = MathUtils.getRandomNumberInRange(1.4f, 3.7f) * damageMult;
			float lifetime = MathUtils.getRandomNumberInRange(0.8f, 1.4f) * VISUAL_DISTORTION_DURATION;
			for (int i2 = 0; i2 < 12; i2++) {
				MagicTrailPlugin.AddTrailMemberAdvanced(null, id, spriteToUse, new Vector2f(proj.getLocation()), startSpeed * ((float)i2 / 12f), 0f,
						angle, startAngularVelocity * ((float)i2 / 12f), 0f, startSize, 0f,
						VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f), VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f),
						0.45f, 0f, 0.3f * ((float)i2 / 12f) * lifetime, 0.7f * ((float)i2 / 12f) * lifetime, GL_SRC_ALPHA, GL_ONE,
						500f, 0f, new Vector2f(0f, 0f), null);
			}
		}

		//If we use GraphicsLib, spawn a distortion
		if (VassModPlugin.hasShaderLib) {
			WaveDistortion wave = new WaveDistortion(new Vector2f(proj.getLocation()), Misc.ZERO);
			wave.setIntensity(3f*damageMult);

			//We want it to be a "dip" rather than a bulge
			wave.flip(true);

			//Ensure the effect fades out properly
			wave.setLifetime(VISUAL_DISTORTION_DURATION);
			wave.fadeOutIntensity(VISUAL_DISTORTION_DURATION);

			//We want the size to work a bit wierdly, namely to fade in, but start at 25%. So we add that
			wave.setSize(BASE_AOE_SIZE*damageMult*0.75f);
			wave.fadeInSize(VISUAL_DISTORTION_DURATION);
			wave.setSize(BASE_AOE_SIZE*damageMult*0.25f);

			//And finally ensure the distortion is tracked
			DistortionShader.addDistortion(wave);
		}

		//Then, simply register that we've detonated
		hasProjectileDetonated = true;
	}
}