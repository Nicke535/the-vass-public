package data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.shipsystems.VassIntegratedMountpointSystem;
import data.scripts.shipsystems.VassIntegratedMountpoints.*;
import data.scripts.utils.VassTimeDistortionProjScript;
import data.scripts.utils.VassUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Gives specific bonuses depending on which weapon is mounted in the main medium slot
 * ...this is going to be one HELL of a headache, isn't it?
 * @author Nicke535
 */
public class VassIntegratedMountpoint extends BaseHullMod {
	public static final Logger LOGGER = Global.getLogger(VassIntegratedMountpoint.class);

	public static enum BonusID {
		// Represents no bonus being available, for whatever reason (no weapon in mount?)
		NONE,

		// Generics
		GENERIC_BALLISTIC,
		GENERIC_ENERGY,
		GENERIC_BEAM,
		GENERIC_MISSILE,

		// Unique - Vanilla
		GRAVITON_BEAM,
		TYPHOON_REAPER,

		// Unique - Vass
		FRAGARACH,
		CALADBOLG,
		CYLLEL_FARCHOG,

		// Unique - Modded
	}

	//ID for most effects the hullmod applies
	private static final String ID = "VassIntegratedMountpointID";

	//The slot ID that we consider our "integrated" slot
	public static final String SPECIAL_SLOT_ID = "WS0002";

	// Some necessary internal script variables
	private Set<DamagingProjectileAPI> alreadyTriggeredProjectiles = new HashSet<>(); // For tracking which projectiles we've already applied our effect on

	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		// Gets which BonusID we have, depending on which weapon is mounted
		BonusID bonusID = getBonusID(ship);
		WeaponAPI weapon = getWeaponInSpecialSlot(ship);

		// Add a damage listener to ourselves, if we need one
		if (bonusID == BonusID.GRAVITON_BEAM ||
				bonusID == BonusID.CALADBOLG ||
				bonusID == BonusID.GENERIC_BALLISTIC) {
			ship.addListener(new IntegratedMountpointDamageListener(bonusID, weapon, ship));
		}

		// Change our cooldown, since we have a base cooldown of 10 and some bonusIDs will not have the same one
		float mult = 1f;
		if (bonusID == BonusID.GRAVITON_BEAM) {
			mult = DefensiveAutotargetingSuite.getSystemCooldownMult();
		} else if (bonusID == BonusID.CALADBOLG) {
			mult = ChamberOverpressure.getSystemCooldownMult();
		} else if (bonusID == BonusID.GENERIC_BALLISTIC) {
			mult = RapidFeedMechanism.getSystemCooldownMult();
		} else if (bonusID == BonusID.TYPHOON_REAPER) {
			mult = AuxiliaryLoadingMechanism.getSystemCooldownMult();
		}
		ship.getMutableStats().getSystemCooldownBonus().modifyMult(id, mult);
	}

	//Changes the ship's time mult at every "advanceInCombat", in order to make sure the global time mult is correct in relation to the player ship
	@Override
	public void advanceInCombat(ShipAPI ship, float amount) {
		CombatEngineAPI engine = Global.getCombatEngine();
		if (engine == null) return;

		// Gets which BonusID we have, depending on which weapon is mounted
		BonusID bonusID = getBonusID(ship);

		// Ensure we apply all bonuses we have to our projectiles
		applyBonusesOnProjectiles(ship, amount, bonusID);
	}

	private void applyBonusesOnProjectiles(ShipAPI ship, float amount, BonusID bonusID) {
		WeaponAPI weapon = getWeaponInSpecialSlot(ship);
		if (weapon == null) return;
		CombatEngineAPI engine = Global.getCombatEngine();

		// Don't bother running this stuff if our bonus ID doesn't need it
		if (bonusID != BonusID.TYPHOON_REAPER) {
			return;
		}

		// Find all projectiles we've fired, and run the correct function on it
		// Only runs once per projectile, and only on projectiles our weapon owns
		for (CombatEntityAPI entity : CombatUtils.getEntitiesWithinRange(weapon.getLocation(), 400f)) {
			if (!(entity instanceof DamagingProjectileAPI)) {
				continue;
			}
			DamagingProjectileAPI proj = (DamagingProjectileAPI)entity;

			//Only run once per projectile
			if (alreadyTriggeredProjectiles.contains(proj)) {
				continue;
			}

			//If the projectile is our own, we can do something with it
			if (proj.getWeapon() == weapon) {
				//Register that we've triggered on the projectile
				alreadyTriggeredProjectiles.add(proj);

				//And add our plugin to the engine to track the effects
				applyBonusOnOneProjectile(ship, weapon, proj, amount, bonusID);
			}
		}

		//Also, we clean up our already triggered projectiles when they stop being loaded into the engine
		List<DamagingProjectileAPI> toRemove = new ArrayList<>();
		for (DamagingProjectileAPI proj : alreadyTriggeredProjectiles) {
			if (!engine.isEntityInPlay(proj)) {
				toRemove.add(proj);
			}
		}
		alreadyTriggeredProjectiles.removeAll(toRemove);
		toRemove.clear();
	}
	private void applyBonusOnOneProjectile(ShipAPI ship, WeaponAPI weapon, DamagingProjectileAPI proj, float amount, BonusID bonusID) {
		// Typhoon reaper launcher: add our Chrono Warhead script to the projectile
		if (bonusID == BonusID.TYPHOON_REAPER) {
			List<VassTimeDistortionProjScript.DetonationListener> listeners = new ArrayList<>();
			listeners.add(new ChronoWarheadScript(proj));
			Global.getCombatEngine().addPlugin(new VassTimeDistortionProjScript(proj, MathUtils.getRandomNumberInRange(0.2f, 1.4f), "vass_excalibur_detonation", 0.7f, listeners));
		}
	}

	//Prevents the hullmod from being put on ships
	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		return false;
	}

	//A whole bunch of descriptions, most unused for now
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) {
			return "medium";
		} else if (index == 1) {
			return "passive benefits";
		} else if (index == 2) {
			return "shipsystem";
		} else {
			return null;
		}
	}

	//For the cool extra description detailing what your mounted weapon is getting from the hullmod
	@Override
	public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
		if (!isForModSpec) {
			addPostDescriptionMountBonus(tooltip, hullSize, ship, width);
		}
	}

	//For bonuses gotten from being a member of a Vass family; not really used now except for debugging
	private void addPostDescriptionMountBonus (TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width) {
		//If we don't mount a weapon we only describe our default system
		BonusID bonusID = getBonusID(ship);
		if (bonusID == BonusID.NONE) {
			tooltip.addSectionHeading("No Integrated Weapon", Alignment.MID, 10f);
			TooltipMakerAPI text2 = tooltip.beginImageWithText("graphics/hullmods/extended_shields.png", 36);
			text2.addPara("Active - Shield Overcharge", 0, Misc.getHighlightColor(), "Active");
			text2.addPara("Short-duration secondary emitters allows the shield to take "+(int)((1f-ShieldOvercharge.SHIELD_DAMAGE_MULT)*100f)+"%% less damage and both unfold and rotate significantly faster. Does not disable weapons.", 2, Misc.getHighlightColor(),(int)((1f-ShieldOvercharge.SHIELD_DAMAGE_MULT)*100f)+"%", "Does not disable weapons");
			tooltip.addImageWithText(10f);
			return;
		}

		float pad = 10f;
		tooltip.addSectionHeading("Integrated Weapon Bonus", Alignment.MID, pad);

		//Typhoon Reaper Launcher
		if (bonusID.equals(BonusID.TYPHOON_REAPER)) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/hullmods/phase_anchor.png", 36);
			text.addPara("Passive - Chrono-warheads", 0, Misc.getHighlightColor(), "Passive");
			text.addPara("Loads all the missiles fired by this weapon with Chrono-disrupting warheads, giving them a disruptive effect against weapons, engines, fighters and missiles near the blast zone.", 2, Misc.getHighlightColor(),"Chrono-disrupting warheads");
			tooltip.addImageWithText(pad);
			TooltipMakerAPI text2 = tooltip.beginImageWithText("graphics/hullmods/expanded_missile_racks.png", 36);
			text2.addPara("Active - Auxiliary Loading Mechanism", 0, Misc.getHighlightColor(), "Active");
			text2.addPara("Weapon gains +600%% extra firerate for the next 2 shots.", 2, Misc.getHighlightColor(),"600%", "2");
			tooltip.addImageWithText(pad);
		}

		//Graviton Beam
		if (bonusID.equals(BonusID.GRAVITON_BEAM)) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/hullmods/extended_shields.png", 36);
			text.addPara("Passive - Graviton Resonator", 0, Misc.getHighlightColor(), "Passive");
			text.addPara("Increases the beam's shield damage by up to 100%% depending on target's flux level.", 2, Misc.getHighlightColor(),"100%");
			tooltip.addImageWithText(pad);
			TooltipMakerAPI text2 = tooltip.beginImageWithText("graphics/hullmods/integrated_point_defense_ai.png", 36);
			text2.addPara("Active - Defensive Autotarget Suite", 0, Misc.getHighlightColor(), "Active");
			text2.addPara("Weapon gains +"+(int)((DefensiveAutotargetingSuite.TURNRATE_MULT-1f)*100f)+"%% turning rate, +"+(int)((DefensiveAutotargetingSuite.DAMAGE_MULT-1f)*100f)+"%% damage against missiles/fighters and counts as PD for "+(int)(DefensiveAutotargetingSuite.DURATION)+" seconds.", 2,
					Misc.getHighlightColor(),(int)((DefensiveAutotargetingSuite.TURNRATE_MULT-1f)*100f)+"%",
					(int)((DefensiveAutotargetingSuite.DAMAGE_MULT-1f)*100f)+"%",
					"PD",
					(int)(DefensiveAutotargetingSuite.DURATION)+"");
			tooltip.addImageWithText(pad);
		}

		//Caladbolg
		if (bonusID.equals(BonusID.CALADBOLG)) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/hullmods/ablative_armor.png", 36);
			text.addPara("Passive - Breaching Rounds", 0, Misc.getHighlightColor(), "Passive");
			text.addPara("Up to half of the projectile's damage is dealt directly to armor, ignoring armor reduction.", 2, Misc.getHighlightColor(),"half", "ignoring armor reduction");
			tooltip.addImageWithText(pad);
			TooltipMakerAPI text2 = tooltip.beginImageWithText("graphics/hullmods/ballistics_integration.png", 36);
			text2.addPara("Active - Chamber Overpressure", 0, Misc.getHighlightColor(), "Active");
			text2.addPara("Next shot gains "+(int)((ChamberOverpressure.RANGE_MULT-1f)*100f)+"%% bonus range, "+(int)((ChamberOverpressure.DAMAGE_MULT-1f)*100f)+"%% bonus damage and "+(int)((ChamberOverpressure.PROJSPEED_MULT-1f)*100f)+"%% bonus projectile speed. Stores up to "+(ChamberOverpressure.CHARGES)+" charges", 2, Misc.getHighlightColor(),
					(int)((ChamberOverpressure.RANGE_MULT-1f)*100f)+"%",
					(int)((ChamberOverpressure.DAMAGE_MULT-1f)*100f)+"%",
					(int)((ChamberOverpressure.PROJSPEED_MULT-1f)*100f)+"%",
					ChamberOverpressure.CHARGES+"");
			tooltip.addImageWithText(pad);
		}

		//Generic: ballistic weapon
		if (bonusID.equals(BonusID.GENERIC_BALLISTIC)) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/hullmods/integrated_targeting_unit.png", 36);
			text.addPara("Passive - Weakpoint Scrying Suite", 0, Misc.getHighlightColor(), "Passive");
			text.addPara("+10/20/30%% damage against destroyers/cruisers/capital ships respectively.", 2, Misc.getHighlightColor(),"10", "20", "30%", "destroyers", "cruisers", "capital ships");
			tooltip.addImageWithText(pad);
			TooltipMakerAPI text2 = tooltip.beginImageWithText("graphics/hullmods/expanded_magazines2.png", 36);
			text2.addPara("Active - Rapid Feed Mechanism", 0, Misc.getHighlightColor(), "Active");
			text2.addPara("Weapon gains "+(int)((RapidFeedMechanism.FIRERATE_MULT-1f)*100f)+"%% extra firerate and -"+(int)((RapidFeedMechanism.FLUX_COST_MULT-1f)*-100f)+"%% flux cost for "+((int)RapidFeedMechanism.DURATION)+" seconds.", 2, Misc.getHighlightColor(),
					(int)((RapidFeedMechanism.FIRERATE_MULT-1f)*100f)+"%",
					"-"+(int)((RapidFeedMechanism.FLUX_COST_MULT-1f)*-100f)+"%",
					((int)RapidFeedMechanism.DURATION)+"");
			tooltip.addImageWithText(pad);
		}
	}

	// Gets which bonus ID we currently fulfill. Unless we have a unique effect for a given weapon ID, it falls back based on base weapon characteristics
	public static BonusID getBonusID(ShipAPI ship) {
		String weaponID = ship.getVariant().getWeaponId(SPECIAL_SLOT_ID);

		if (weaponID == null || "".equals(weaponID)) {
			return BonusID.NONE;
		}

		// Unique vanilla weapons
		if (weaponID.equals("typhoon")) {
			return BonusID.TYPHOON_REAPER;
		}
		if (weaponID.equals("gravitonbeam")) {
			return BonusID.GRAVITON_BEAM;
		}

		// Unique Vass weapons
		if (weaponID.equals("vass_caladbolg")) {
			return BonusID.CALADBOLG;
		}

		// Fallbacks, as no unique effect was found
		WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponID);
		if (spec.getType().equals(WeaponAPI.WeaponType.BALLISTIC)) {
			return BonusID.GENERIC_BALLISTIC;
		}
		if (spec.getType().equals(WeaponAPI.WeaponType.ENERGY)) {
			if (spec.isBeam()) {
				return BonusID.GENERIC_BEAM;
			} else {
				return BonusID.GENERIC_ENERGY;
			}
		}
		if (spec.getType().equals(WeaponAPI.WeaponType.MISSILE)) {
			return BonusID.GENERIC_MISSILE;
		}

		// We should never reach this point, so throw an error
		LOGGER.fatal("[VassIntegratedMountpoint] Weapon "+spec.getWeaponId()+" failed to find a valid BonusID");
		throw new IllegalStateException("Please contact Nicke535 with details of the crash: Error in VassIntegratedMountpoint.java");
	}

	public static WeaponAPI getWeaponInSpecialSlot(ShipAPI ship) {
		for (WeaponAPI wep : ship.getAllWeapons()) {
			if (wep.getSlot() != null) {
				if (wep.getSlot().getId().equals(SPECIAL_SLOT_ID)) {
					return wep;
				}
			}
		}
		return null;
	}

	// Listener for damage-dealing, to make certain effects possible with the hullmod
	public static class IntegratedMountpointDamageListener implements DamageDealtModifier, AdvanceableListener {
		private BonusID bonusID;
		private WeaponAPI specialWeapon;
		private ShipAPI ship;

		public IntegratedMountpointDamageListener(BonusID bonusID, WeaponAPI weapon, ShipAPI ship) {
			this.bonusID = bonusID;
			this.specialWeapon = weapon;
			this.ship = ship;
		}

		public void advance(float amount) {

		}

		public String modifyDamageDealt(Object param,
										CombatEntityAPI target, DamageAPI damage,
										Vector2f point, boolean shieldHit) {
			Vector2f from = null;
			WeaponAPI weapon = null;
			if (param instanceof DamagingProjectileAPI) {
				from = ((DamagingProjectileAPI)param).getSpawnLocation();
				weapon = ((DamagingProjectileAPI)param).getWeapon();
			} else if (param instanceof BeamAPI) {
				from = ((BeamAPI)param).getFrom();
				weapon = ((BeamAPI)param).getWeapon();
			} else {
				return null;
			}

			if (weapon == null || weapon != specialWeapon || ship == null) return null;

			if (bonusID == BonusID.GRAVITON_BEAM) {
				// Graviton beam: if we hit a shield, we deal bonus damage depending on the target's current flux
				if (shieldHit && (target instanceof ShipAPI)) {
					float fluxLevel = ((ShipAPI)target).getFluxLevel();
					damage.getModifier().modifyMult(ID, fluxLevel + 1f);
				}
			}

			if (bonusID == BonusID.CALADBOLG) {
				// Caladbolg: up to half of the damage is dealt directly to armor instead of being reduced.
				//  - Note that the damage calculation is affected by damage type
				if (!shieldHit && (target instanceof ShipAPI)) {
					float damageAmount = damage.getDamage();
					float damageDealt = VassUtils.dealArmorDamage(ship, (ShipAPI)target, point, damageAmount * 0.5f * damage.getType().getArmorMult(), true);
					float damageMult = (damageAmount-(damageDealt/damage.getType().getArmorMult()))/damageAmount;
					damage.getModifier().modifyMult(ID, damageMult);
				}
			}

			if (bonusID == BonusID.GENERIC_BALLISTIC) {
				// Generic ballistic effect: if we hit a ship, get bonus damage based on its hullsize
				if (target instanceof ShipAPI) {
					HullSize size = ((ShipAPI)target).getHullSize();
					float bonus = 0f;
					if (size == HullSize.DESTROYER) {
						bonus = 0.1f;
					} else if (size == HullSize.CRUISER) {
						bonus = 0.2f;
					} else if (size == HullSize.CAPITAL_SHIP) {
						bonus = 0.3f;
					}
					if (bonus > 0f) {
						damage.getModifier().modifyMult(ID, bonus + 1f);
					}
				}
			}


			return ID;
		}
	}

	//Listener for the detonation event of the standard Time Distortion Projectile Script, but adapted for the Chrono Warhead effect
	private class ChronoWarheadScript implements VassTimeDistortionProjScript.DetonationListener {
		private DamagingProjectileAPI sourceProj;
		private ChronoWarheadScript(DamagingProjectileAPI proj) {
			sourceProj = proj;
		}

		@Override
		public void detonate(Vector2f loc, float damageMult, float basicAoESize) {
			// Find us all ships near the detonation (note that this intentionally hits phased ships, too!)
			for (ShipAPI target : CombatUtils.getShipsWithinRange(loc, basicAoESize)) {
				//Does not affect fighters (those are already hit by the base effect
				if (target.getHullSize() == ShipAPI.HullSize.FIGHTER) {
					continue;
				}

				//We never target things with NONE collision class
				if (target.getCollisionClass() == CollisionClass.NONE) {
					continue;
				}

				// Gets all weapons on the target
				for (WeaponAPI wep : target.getAllWeapons()) {
					// Ignore deco weapons
					if (wep.isDecorative()) {
						continue;
					}

					// If the weapon is close enough to be hit by the AoE, apply some very random EMP damage to it
					// Note that 33% of the EMP penetrates shields, modified by target EMP penetration mult
					float distanceMult = (basicAoESize - MathUtils.getDistance(loc, wep.getLocation())) / (basicAoESize);
					float extraEMPMult = target.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);
					float piercedMult = (1f/3f) * extraEMPMult;
					float nonPiercedMult = 1f - piercedMult;
					if (distanceMult > 0f) {
						float randomFactor = (float)Math.random();
						Global.getCombatEngine().applyDamage(target, wep.getLocation(), 0f, DamageType.ENERGY,
								randomFactor * sourceProj.getDamageAmount() * distanceMult * piercedMult,
								true, true, sourceProj.getSource(), false);
						Global.getCombatEngine().applyDamage(target, wep.getLocation(), 0f, DamageType.ENERGY,
								randomFactor * sourceProj.getDamageAmount() * distanceMult * nonPiercedMult,
								false, true, sourceProj.getSource(), false);
					}
				}
			}
		}
	}
}
