package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.campaign.CampaignUtils;
import org.lwjgl.util.vector.Vector2f;

public class VassRandomEncounterPlugin implements EveryFrameScript {

    //The minimum and maximum size factor compared to the target that the families will ever send
    private static final float MIN_FP_FACTOR = 1.05f;
    private static final float MAX_FP_FACTOR = 1.35f;

    //How many FP of fleet can the families dish out compared to their power?
    private static final float FLEET_FP_PER_POWER = 4f;

    //What is the chance of an elite fleet?
    private static final float ELITE_CHANCE = 0.15f;

    //Determines how often Vass fleets can spawn against nearby fleets
    private IntervalUtil interval = new IntervalUtil(17f, 34f);

    @Override
    public void advance(float amount) {
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

                //Only hostile fleets are targeted
                if (!fleet.getFaction().getRelationshipLevel("vass").isAtBest(RepLevel.HOSTILE))  {
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

                //If everything above passed, add us to the picker
                picker.add(fleet);
            }

            //Pick one fleet at random to target. If none is valid, don't do anything
            CampaignFleetAPI target = picker.pick();
            if (target != null) {
                //Have a chance of spawning an elite
                boolean isElite = Math.random() < ELITE_CHANCE;
                spawnHuntFleet(family, target, isElite ? "elite" : "");
            }

            //If no fleet was found, re-pay the interval a bit of its progress
            interval.advance(interval.getMinInterval()*0.7f);
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
