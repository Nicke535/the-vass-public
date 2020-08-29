package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.campaign.CampaignUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Handles random Vass encounters out "in the wild"
 */
public class VassRandomEncounterPlugin implements EveryFrameScript {

    //A list of factions that will never be attacked by random encounters
    public static final HashSet<String> BLACKLISTED_FACTIONS = new HashSet<>();
    static {
        BLACKLISTED_FACTIONS.add(Factions.DERELICT);
        BLACKLISTED_FACTIONS.add(Factions.REMNANTS);
        BLACKLISTED_FACTIONS.add(Factions.PLAYER);
        BLACKLISTED_FACTIONS.add("templars");
    }

    //A list of factions that will get a Vass ship when attacked
    public static final HashSet<String> STEALING_FACTIONS = new HashSet<>();
    static {
        STEALING_FACTIONS.add(Factions.INDEPENDENT);
        STEALING_FACTIONS.add(Factions.PIRATES);
        STEALING_FACTIONS.add("tiandong");
    }

    //The minimum and maximum size factor compared to the target that the families will ever send
    private static final float MIN_FP_FACTOR = 1.15f;
    private static final float MAX_FP_FACTOR = 1.45f;

    //How many FP of fleet can the families dish out compared to their power?
    private static final float FLEET_FP_PER_POWER = 4f;

    //What is the chance of an elite fleet?
    private static final float ELITE_CHANCE = 0.15f;

    //Determines how often Vass fleets can spawn against nearby fleets
    private IntervalUtil interval = new IntervalUtil(27f, 55f);

    @Override
    public void advance(float amount) {
        if (Global.getSector().isPaused()) { amount = 0f; }
        interval.advance(Misc.getDays(amount));
        if (interval.intervalElapsed()) {
            //Choose one family to trigger with: stronger families are more likely
            WeightedRandomPicker<VassUtils.VASS_FAMILY> familyPicker = new WeightedRandomPicker<>();
            for (VassUtils.VASS_FAMILY family : VassUtils.VASS_FAMILY.values()) {
                familyPicker.add(family, VassFamilyTrackerPlugin.getPowerOfFamily(family));
            }
            VassUtils.VASS_FAMILY family = familyPicker.pick();
            if (family == null) {
                return;
            }

            //Gets all the fleets nearby that are eligable
            WeightedRandomPicker<CampaignFleetAPI> picker = new WeightedRandomPicker<>();
            for (SectorEntityToken entity : Global.getSector().getPlayerFleet().getContainingLocation().getAllEntities()) {
                if (!(entity instanceof CampaignFleetAPI)) {
                    continue;
                }
                CampaignFleetAPI fleet = (CampaignFleetAPI)entity;

                if (BLACKLISTED_FACTIONS.contains(fleet.getFaction().getId())) {
                    continue;
                }

                //Only hostile fleets are targeted
                if (!fleet.getFaction().getRelationshipLevel("vass").isAtBest(RepLevel.HOSTILE))  {
                    continue;
                }

                //Ignore fleets that are mission-critical for some reason
                if (fleet.getMemoryWithoutUpdate().get(MemFlags.ENTITY_MISSION_IMPORTANT) != null) {
                    continue;
                }

                //Ignore fleets too close to the player fleet
                if (MathUtils.getDistance(fleet.getLocation(), Global.getSector().getPlayerFleet().getLocation()) < Global.getSector().getPlayerFleet().getBaseSensorRangeToDetect(fleet.getSensorProfile()*1.5f)) {
                    continue;
                }

                //Ignore fleets with too many FP for us to handle
                if (fleet.getFleetPoints() > VassFamilyTrackerPlugin.getPowerOfFamily(family) * FLEET_FP_PER_POWER * MIN_FP_FACTOR) {
                    continue;
                }

                //Ignore fleets that have *stations*, because that's silly and buggable
                if (fleet.isStationMode() || fleetHasStation(fleet)) {
                    continue;
                }

                //Only fleets that are somewhat isolated from other enemies are targeted
                boolean valid = true;
                for (CampaignFleetAPI otherFleet : CampaignUtils.getNearbyFleets(fleet, 500f)) {
                    if (otherFleet.getFaction().isAtBest("vass", RepLevel.HOSTILE) &&
                            !Global.getSector().getPlayerFleet().equals(otherFleet) && !otherFleet.equals(fleet)) {
                        valid = false;
                        break;
                    }
                }
                if (!valid) { continue; }

                //If everything above passed, add us to the picker, with an inverse weight for fleet size
                picker.add(fleet, 1f / (float)Math.sqrt(fleet.getFleetPoints()));
            }

            //Pick one fleet at random to target. If none is valid, don't do anything
            CampaignFleetAPI target = picker.pick();
            if (target != null) {
                //Have a chance of spawning an elite
                boolean isElite = Math.random() < ELITE_CHANCE;
                spawnHuntFleet(family, target, isElite ? "elite" : "");
                if (STEALING_FACTIONS.contains(target.getFaction().getId())) {
                    giftVassShip(target);
                }
            } else {
                //If no fleet was found, re-pay the interval a bit of its progress
                interval.advance(interval.getMinInterval()*0.7f);
            }
        }
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public boolean isDone() {
        for (VassUtils.VASS_FAMILY family : VassUtils.VASS_FAMILY.values()) {
            if (!VassFamilyTrackerPlugin.isFamilyEliminated(family)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a fleet contains any ship that counts as a station
     */
    private boolean fleetHasStation(CampaignFleetAPI fleet){
        for (FleetMemberAPI member : fleet.getMembersWithFightersCopy()) {
            if (member.isStation()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gifts a Vass ship to a fleet based on luck and their FP
     */
    private static void giftVassShip(CampaignFleetAPI fleet) {
        String baseVariant;
        if (Math.random() * fleet.getFleetPoints() < 40f) {
            baseVariant = "vass_makhaira_support";
        } else if (Math.random() * fleet.getFleetPoints() < 70f) {
            baseVariant = "vass_schiavona_defensive";
        } else {
            baseVariant = "vass_curtana_support";
        }
        FleetMemberAPI newlyAdded = fleet.getFleetData().addFleetMember(baseVariant);
        ShipVariantAPI variant = newlyAdded.getVariant().clone();

        //Gets a whole bunch of Dmods randomly
        WeightedRandomPicker<String> dmodPicker = new WeightedRandomPicker<>();
        for (HullModSpecAPI modSpec : Global.getSettings().getAllHullModSpecs()) {
            if (modSpec.hasTag("damage") || modSpec.hasTag("damageStruct")) {
                dmodPicker.add(modSpec.getId());
            }
        }
        for (int i = MathUtils.getRandomNumberInRange(1, 5); i > 0; i--) {
            variant.addPermaMod(dmodPicker.pick());
        }

        //Remove weapons randomly
        List<String> slotList = new ArrayList<>(variant.getFittedWeaponSlots());
        for (String slot : slotList) {
            if (Math.random() < 0.75f) {
                variant.clearSlot(slot);
            }
        }

        //Adjust final parameters and update stats
        variant.setVariantDisplayName("Looted");
        variant.setSource(VariantSource.REFIT);
        newlyAdded.setVariant(variant, false, true);
        newlyAdded.getRepairTracker().setCR(MathUtils.getRandomNumberInRange(0.1f, 0.35f));
        newlyAdded.updateStats();
    }

    /**
     * Spawns a hunter fleet after a target fleet (assumed to not be the player)
     * @param family vass family sending the hunter fleet
     * @param target target of the hunter fleet
     * @param specialOptions special options for the fleet spawn. Currently supported:
     *                          elite : spawns the fleet as an Elite fleet
     */
    public static void spawnHuntFleet(VassUtils.VASS_FAMILY family, CampaignFleetAPI target, String specialOptions) {
        SectorAPI sector = Global.getSector();
        LocationAPI loc = target.getContainingLocation();
        Vector2f centerPoint = target.getLocation();

        //Determines faction set to pick ships from
        String factionToPickFrom = null;
        if (family == VassUtils.VASS_FAMILY.PERTURBA) {
            //Normal perturba fleet
            factionToPickFrom = "vass_perturba";
        }
        if (factionToPickFrom == null) {
            return;
        }

        //Creates the fleet, with only combat ships
        FleetParamsV3 params = new FleetParamsV3(centerPoint, factionToPickFrom, 5f, "taskForce",
                target.getFleetPoints() * MathUtils.getRandomNumberInRange(MIN_FP_FACTOR, MAX_FP_FACTOR),
                0f, 0f, 0f, 0f, 0f, 1f);
        CampaignFleetAPI newFleet = FleetFactoryV3.createFleet(params);
        newFleet.inflateIfNeeded();
        newFleet.setContainingLocation(loc);
        newFleet.setFaction("vass", true);
        newFleet.setNoFactionInName(true);
        if (specialOptions.contains("elite")) {
            newFleet.setName("Elite " + newFleet.getName());
            newFleet.getMemoryWithoutUpdate().set("$vass_fleet_family_elite", true);
        }
        newFleet.getMemoryWithoutUpdate().set("$vass_fleet_family_membership", family);
        newFleet.setName(VassUtils.getFamilyName(family, true) + " " + newFleet.getName());

        //Chooses a location extremely close to the target
        Vector2f desiredSpawnPoint = MathUtils.getPoint(centerPoint, newFleet.getRadius()*2f ,MathUtils.getRandomNumberInRange(0f, 360f));
        newFleet.setLocation(desiredSpawnPoint.x, desiredSpawnPoint.y);

        //Give it basic abilities, if it somehow lacks it
        newFleet.addAbility("emergency_burn");
        newFleet.addAbility("interdiction_pulse");
        newFleet.addAbility("sustained_burn");
        newFleet.addAbility("sensor_burst");

        //Finally, makes the fleet hostile against the other fleet
        VassCampaignUtils.makeFleetInterceptOtherFleet(newFleet, target, false, 20f, "vassRandomEncounter");
        loc.addEntity(newFleet);
    }
}
