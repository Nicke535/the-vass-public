package data.scripts.campaign.customstart;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.newgame.NGCAddStartingShipsByFleetType;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.campaign.VassFamilyInformationEventIntel;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.customstart.CustomStart;

import java.util.*;

public class VassSchiavonaAccelStart extends CustomStart {
    private List<String> ships = new ArrayList<>(Arrays.asList("vass_schiavona_starter_elite"));

    public VassSchiavonaAccelStart(){}

    @Override
    public void execute(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        ExerelinSetupData.getInstance().freeStart = true;
        PlayerFactionStore.setPlayerFactionIdNGC("player");
        CharacterCreationData data = (CharacterCreationData)(memoryMap.get("local")).get("$characterData");
        NGCAddStartingShipsByFleetType.generateFleetFromVariantIds(dialog, data, "super", this.ships);
        NGCAddStartingShipsByFleetType.addStartingDModScript(memoryMap.get("local"));
        FireBest.fire(null, dialog, memoryMap, "ExerelinNGCStep4");
        data.addScript(new PostSetupScript());
    }


    // Plugins which ensures proper family membership when sector has finalized initialization
    // ... as well as some other things!
    private class PostSetupScript implements Script {
        @Override
        public void run() {
            Global.getSector().addScript(new PostSectorCreationScript());
        }
    }

    private class PostSectorCreationScript implements EveryFrameScript {
        boolean done = false;

        @Override
        public void advance(float amount) {
            // Step 1: make sure we're a member of the Accel family, with appropriate relations
            VassFamilyTrackerPlugin.setFamilyMembership(VassUtils.VASS_FAMILY.ACCEL);
            VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.ACCEL, 999f);
            Global.getSector().getPlayerFaction().setRelationship("vass", RepLevel.COOPERATIVE);

            // Step 2: Rename our starting ship to something... appropriate!
            boolean correctName = false;
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (member.getShipName().contentEquals(getStartingShipname())) {
                    correctName = true;
                    break;
                }
                member.setShipName(getStartingShipname());
            }

            // Step 3: verify that the correct settings have been set, and if so, we are done
            if (correctName
                    && VassUtils.VASS_FAMILY.ACCEL.equals(VassFamilyTrackerPlugin.getFamilyMembership())
                    && VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.ACCEL) >= 100f
                    && Global.getSector().getPlayerFaction().getRelationshipLevel("vass").isAtWorst(RepLevel.COOPERATIVE)) {
                //Also add family information once we're done, once that gets implemented.
                VassFamilyInformationEventIntel.addFactorCreateIfNecessary(new VassPriorKnowledgeFactor(VassFamilyInformationEventIntel.PROGRESS_3), null);
                done = true;
            }
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        // The name of our ship should have also includes some secret references, because why not?
        private String getStartingShipname() {
            FullName name = Global.getSector().getPlayerPerson().getName();
            if (name.getFirst().equals("Lain")) {
                return "Serial Experiment";
            } else if (name.getFirst().equals("Suletta")) {
                return "Aerial";
            } else if (name.getLast().equals("Hakurei")) {
                return "Dichromatic Lotus Butterfly";
            } else if (name.getFirst().equals("Sakuya")
                    || (name.getLast().equals("Sakuya") && name.getFirst().equals("Izayoi"))) {
                return "Clock Corpse";
            } else if (name.getFirst().equals("Violet") && name.getLast().equals("Evergarden")) {
                return "Silver Hand";
            } else if (name.getFirst().equals("Yukari")
                        || (name.getLast().equals("Yukari") && name.getFirst().equals("Yakumo"))) {
                return "Border Between Particle and Wave";
            } else if (name.getFullName().contains("Bucciarati")) {
                return "Sticky Finger";
            } else if ((name.getFirst().equals("Pannacotta") && name.getLast().equals("Fugo"))
                        || (name.getLast().equals("Pannacotta") && name.getFirst().equals("Fugo"))) {
                return "Purple Haze";
            } else if (name.getFirst().equals("Dio")) {
                return "The World Over Heaven";
            } else if (name.getFirst().equals("Diavolo")) {
                return "King in Crimson";
            } else if (name.getFirst().equals("Narancia")
                        || (name.getFirst().equals("Ghirga") && name.getLast().equals("Narancia"))) {
                return "Aerosmith";
            } else if ((name.getFirst().equals("Giorno") && name.getLast().equals("Giovanna"))
                    || (name.getLast().equals("Giorno") && name.getFirst().equals("Giovanna"))) {
                return "Vento Aureo";
            } else if (name.getFirst().equals("Nia") && name.getLast().equals("Tahl")) {
                return "Pink Menace";
            } else if (name.getFirst().contains("Gwyvern")) {
                return "Red Canvas";
            } else if (name.getFullName().contains("Dark Revenant")) {
                return "In Time for the Gala";
            } else if (name.getFullName().contains("Avanitia")) {
                return "A Scalaron and a Dream";
            } else if (name.getFirst().contains("Nicke")) {
                return "Pale Imitation";
            }

            //Fallback, non-reference name
            return "Il Mondo";
        }
    }

    public class VassPriorKnowledgeFactor extends BaseOneTimeFactor {

        public VassPriorKnowledgeFactor(int points) {
            super(points);
        }

        @Override
        public String getDesc(BaseEventIntel intel) {
            return "Prior Knowledge";
        }

        @Override
        public TooltipMakerAPI.TooltipCreator getMainRowTooltip() {
            return new BaseFactorTooltip() {
                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara("Prior knowledge from being a part of the Accel family.",
                            0f);
                }

            };
        }
    }
}
