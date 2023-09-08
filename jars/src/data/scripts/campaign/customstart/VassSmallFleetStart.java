package data.scripts.campaign.customstart;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.newgame.NGCAddStartingShipsByFleetType;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyInformationEventIntel;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.campaign.barEvents.VassPerturbaGetShipSubmarketEvent;
import data.scripts.utils.VassUtils;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.customstart.CustomStart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class VassSmallFleetStart extends CustomStart {
    private List<String> ships = new ArrayList<>(Arrays.asList("vass_curtana_strike", "vass_schiavona_multipurpose", "vass_akrafena_hailstorm", "vass_makhaira_defensive", "vass_makhaira_support"));

    public VassSmallFleetStart(){}

    @Override
    public void execute(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        ExerelinSetupData.getInstance().freeStart = true;
        ExerelinSetupData.getInstance().dModLevel = 4;
        PlayerFactionStore.setPlayerFactionIdNGC("player");
        CharacterCreationData data = (CharacterCreationData)(memoryMap.get("local")).get("$characterData");
        NGCAddStartingShipsByFleetType.generateFleetFromVariantIds(dialog, data, null, this.ships);
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
            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

            // Step 1: make sure we're starting with Friendly Vass relations, since otherwise things will quickly turn sour...
            Global.getSector().getPlayerFaction().setRelationship("vass", RepLevel.WELCOMING);

            // Step 2: Rename our starting ships
            int correctNames = 0;
            for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
                if (member.getShipName().contentEquals(getStartingShipname(member.getVariant().getDisplayName()))) {
                    correctNames++;
                } else {
                    member.setShipName(getStartingShipname(member.getVariant().getDisplayName()));
                }
            }

            // Step 3: try to add the special submarket to the correct planet (Asharu)
            boolean hasSubmarket = false;
            for (MarketAPI market : Misc.getFactionMarkets(Factions.INDEPENDENT)) {
                if (market.getName().contains("Asharu")) {
                    if (market.hasSubmarket(VassPerturbaGetShipSubmarketEvent.SUBMARKET_ID)) {
                        hasSubmarket = true;
                    } else {
                        market.addSubmarket(VassPerturbaGetShipSubmarketEvent.SUBMARKET_ID);
                    }
                    break;
                }
            }

            // Step 4: ensure we have at least enough crew to run skeleton crew on all ships
            if (playerFleet.getFleetData().getMinCrew() > playerFleet.getCargo().getCrew()) {
                int diff = (int)(playerFleet.getFleetData().getMinCrew() - playerFleet.getCargo().getCrew());
                playerFleet.getCargo().addCrew(diff);
            }

            // Step 5: verify that the correct settings have been set, and if so, we are done
            if (correctNames >= ships.size()
                    && hasSubmarket
                    && Global.getSector().getPlayerFaction().getRelationshipLevel("vass").isAtWorst(RepLevel.WELCOMING)
                    && playerFleet.getFleetData().getMinCrew() <= playerFleet.getCargo().getCrew()) {
                //Also add family information once we're done, once that gets implemented.
                VassFamilyInformationEventIntel.addFactorCreateIfNecessary(new VassPriorKnowledgeFactor(VassFamilyInformationEventIntel.PROGRESS_1), null);
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

        // The name of our ships are set to be a bunch of jojo references, because why not?
        private String getStartingShipname(String variantDisplayName) {
            if (variantDisplayName.contains("Defensive")) {
                return "Silver Experience";
            } else if (variantDisplayName.contains("Support")) {
                return "Moody Reds";
            } else if (variantDisplayName.contains("Strike")) {
                return "Aerocarpenter";
            } else if (variantDisplayName.contains("Hailstorm")) {
                return "Five Pistols";
            } else {
                return "Sickly Finger";
            }
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
                    tooltip.addPara("Prior knowledge from dealings with the Vass families.",
                            0f);
                }

            };
        }
    }
}
