package data.scripts.campaign;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Set;

import data.scripts.utils.VassUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.CharacterStatsRefreshListener;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.EventFactor;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.TimeoutTracker;

/**
 * Experimental attempt at handling the main "Information" mechanic related to the vass families.
 * The main intel seems simple enough, but I'll have to work on the specific implementations at a later time
 *
 * @author Nicke535
 */
public class VassFamilyInformationEventIntel extends BaseEventIntel implements FleetEventListener,
        CharacterStatsRefreshListener,
        CurrentLocationChangedListener {

    public static Color BAR_COLOR = Global.getSettings().getColor("progressBarFleetPointsColor");

    public static int PROGRESS_1 = 150;
    public static int PROGRESS_2 = 300;
    public static int PROGRESS_3 = 350;
    public static int PROGRESS_4 = 500;
    public static int PROGRESS_5 = 550;
    public static int PROGRESS_6 = 750;
    public static int PROGRESS_MAX = 1000;

    public static String KEY = "$vass_familyinformationevent_ref";

    public static enum Stage {
        START,
        BASIC_INFO,
        LEADERSHIP_STRUCTURE,
        ACTIVE_INFORMATION,
        PERTURBA_LOCATIONS,
        POWER_ESTIMATE,
        ALL_LOCATIONS_OPEN,
        LOCATION_SECRET,
    }

    public static void addFactorCreateIfNecessary(EventFactor factor, InteractionDialogAPI dialog) {
        // This mechanic is not yet implemented, and as such nothing happens when we add a factor
        return;

        /* TODO This will be implemented at a later time
        if (get() == null) {
            // We're adding a factor anyway, so it'll show a message - don't need to double up
            new VassFamilyInformationEventIntel(null, false);
        }
        if (get() != null) {
            get().addFactor(factor, dialog);
        }
        */
    }

    public static VassFamilyInformationEventIntel get() {
        return (VassFamilyInformationEventIntel) Global.getSector().getMemoryWithoutUpdate().get(KEY);
    }


    public VassFamilyInformationEventIntel(TextPanelAPI text, boolean withIntelNotification) {
        super();

        Global.getSector().getMemoryWithoutUpdate().set(KEY, this);

        setMaxProgress(PROGRESS_MAX);

        addStage(Stage.START, 0);
        addStage(Stage.BASIC_INFO, PROGRESS_1, StageIconSize.LARGE);
        addStage(Stage.LEADERSHIP_STRUCTURE, PROGRESS_2, StageIconSize.MEDIUM);
        addStage(Stage.ACTIVE_INFORMATION, PROGRESS_3, StageIconSize.SMALL);
        addStage(Stage.PERTURBA_LOCATIONS, PROGRESS_4, StageIconSize.LARGE);
        addStage(Stage.POWER_ESTIMATE, PROGRESS_5, StageIconSize.MEDIUM);
        addStage(Stage.ALL_LOCATIONS_OPEN, PROGRESS_6, false, StageIconSize.LARGE);
        addStage(Stage.LOCATION_SECRET, PROGRESS_MAX, false, StageIconSize.SMALL);

        getDataFor(Stage.BASIC_INFO).keepIconBrightWhenLaterStageReached = true;

        getDataFor(Stage.LEADERSHIP_STRUCTURE).keepIconBrightWhenLaterStageReached = true;

        getDataFor(Stage.ACTIVE_INFORMATION).keepIconBrightWhenLaterStageReached = true;
        getDataFor(Stage.ACTIVE_INFORMATION).sendIntelUpdateOnReaching = false;

        getDataFor(Stage.PERTURBA_LOCATIONS).keepIconBrightWhenLaterStageReached = true;

        getDataFor(Stage.POWER_ESTIMATE).keepIconBrightWhenLaterStageReached = true;

        getDataFor(Stage.ALL_LOCATIONS_OPEN).keepIconBrightWhenLaterStageReached = true;


        // now that the event is fully constructed, add it and send notification
        Global.getSector().getIntelManager().addIntel(this, !withIntelNotification, text);
    }


    @Override
    protected void notifyEnding() {
        super.notifyEnding();
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().getMemoryWithoutUpdate().unset(KEY);
    }

    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                   Color tc, float initPad) {

        if (addEventFactorBulletPoints(info, mode, isUpdate, tc, initPad)) {
            return;
        }

        Color h = Misc.getHighlightColor();
        if (isUpdate && getListInfoParam() instanceof EventStageData) {
            EventStageData esd = (EventStageData) getListInfoParam();
            if (esd.id == Stage.BASIC_INFO) {
                info.addPara("Plans have been devised for further intel acquisition on the Vass families", tc, initPad);
            }
            if (esd.id == Stage.LEADERSHIP_STRUCTURE) {
                info.addPara("Vass leadership structure has been roughly mapped out", initPad, tc);
            }
            if (esd.id == Stage.PERTURBA_LOCATIONS) {
                info.addPara("Perturba main hideouts have been mapped out", initPad, tc,
                        VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f), "Perturba");
            }
            if (esd.id == Stage.POWER_ESTIMATE) {
                info.addPara("Vass families' sector presence report available", tc, initPad);
            }
            if (esd.id == Stage.ALL_LOCATIONS_OPEN) {
                info.addPara("Major hideouts of all Vass families have been identified and localized", tc, initPad);
            }
            if (esd.id == Stage.LOCATION_SECRET) {
                info.addPara("Secret Vass facility located", tc, initPad);
            }
            return;
        }
    }

    @Override
    public void addStageDescriptionText(TooltipMakerAPI info, float width, Object stageId) {
        float opad = 10f;
        float small = 0f;
        Color h = Misc.getHighlightColor();

        EventStageData stage = getDataFor(stageId);
        if (stage == null) return;

        if (isStageActive(stageId)) {
            addStageDesc(info, stageId, small, false);
        }
    }


    public void addStageDesc(TooltipMakerAPI info, Object stageId, float initPad, boolean forTooltip) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        if (stageId == Stage.START) {
            info.addPara("The Vass families operate mostly hidden throughout the sector, making them hard to tackle. "
                            + "Acquiring intelligence regarding their operations could be of great value."
                            + "Acquisition of such knowledge may be tricky, but a good place to start would be "
                            + "getting access to some of their technology, finding some of their operatives and "
                            + "raiding their facilities should you find them. Some of the sector's less savory "
                            + "individuals might also know a thing or two if you know how to ask...",
                    initPad);
        } else if (stageId == Stage.BASIC_INFO) {
            info.addPara("With the prior intel available, your colonies should now be able to begin concentrated "
                            + "intelligence efforts against the Vass families. More stable colonies, close to Vass "
                            + "bases of operation and with large amounts of military presence will be most useful for "
                            + "such intelligence gathering.", initPad,
                    Misc.getHighlightColor(),
                    "stable",
                    "bases of operation",
                    "military presence"
            );
        } else if (stageId == Stage.LEADERSHIP_STRUCTURE) {
            info.addPara("The main leadership structures of the five Vass families become known, allowing " +
                            "more streamlined diplomatic efforts and potentially granting opportunities to " +
                            "disrupt Vass interests more efficiently.",
                    initPad);
        } else if (stageId == Stage.ACTIVE_INFORMATION) {
            info.addPara("Any information obtained beyond this point must be regularly kept up-to-date, " +
                            "requiring continuous information gathering efforts.", initPad);
        } else if (stageId == Stage.PERTURBA_LOCATIONS) {
            info.addPara("Continuously maps out all markets where Perturba maintains a significant presence. "
                            + "These locations should prove highly useful if proprietary technologies or intel "
                            + "need to be acquired, and shutting these facilities down would also cut off "
                            + "their main source of revenue.", initPad,
                    VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f),
                    "Perturba"
            );
        } else if (stageId == Stage.POWER_ESTIMATE) {
            info.addPara("Provides a running estimate of the remaining assets of the five Vass families. "
                            + "The more power a given family can muster, the larger the fights they will be willing to "
                            + "take on and the more aggressive they will be in pursuing their goals.", initPad,
                    VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f),
                    "Perturba"
            );
        } else if (stageId == Stage.ALL_LOCATIONS_OPEN) {
            info.addPara("With this level of intel all major Vass facilities have been mapped out, "
                            + "with only their most secretive projects still alluding detection.", initPad,
                    VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f),
                    "Perturba"
            );
        } else if (stageId == Stage.LOCATION_SECRET) {
            int min = (int)Misc.interpolate(PROGRESS_6, PROGRESS_MAX, 0.25f);
            int max = (int)Misc.interpolate(PROGRESS_6, PROGRESS_MAX, 0.50f);
            info.addPara("Provides valuable intel on one of the Vass families' more secretive facilities, "
                    + "extremely useful both for sabotage and tech acquisition purposes.", initPad);
            info.addPara("Event progress will be reset to between %s and %s points when this outcome is reached.",
                    opad, h, "" + min, "" + max);
        }
    }

    public TooltipCreator getStageTooltipImpl(Object stageId) {
        final EventStageData esd = getDataFor(stageId);

        if (esd != null && EnumSet.of(Stage.BASIC_INFO, Stage.LEADERSHIP_STRUCTURE,
                Stage.ACTIVE_INFORMATION, Stage.PERTURBA_LOCATIONS, Stage.POWER_ESTIMATE,
                Stage.ALL_LOCATIONS_OPEN, Stage.LOCATION_SECRET).contains(esd.id)) {
            return new BaseFactorTooltip() {
                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    float opad = 10f;

                    if (esd.id == Stage.BASIC_INFO) {
                        tooltip.addTitle("[[TEMP]]Basic Information");
                    } else if (esd.id == Stage.LEADERSHIP_STRUCTURE) {
                        tooltip.addTitle("Leadership Structure");
                    } else if (esd.id == Stage.ACTIVE_INFORMATION) {
                        tooltip.addTitle("Active Information");
                    } else if (esd.id == Stage.PERTURBA_LOCATIONS) {
                        tooltip.addTitle("Perturba Locations");
                    } else if (esd.id == Stage.POWER_ESTIMATE) {
                        tooltip.addTitle("Power estimate");
                    } else if (esd.id == Stage.ALL_LOCATIONS_OPEN) {
                        tooltip.addTitle("Full facility mapping");
                    } else if (esd.id == Stage.LOCATION_SECRET) {
                        tooltip.addTitle("Secret Assets");
                    }

                    addStageDesc(tooltip, esd.id, opad, true);

                    esd.addProgressReq(tooltip, opad);
                }
            };
        }

        return null;
    }



    @Override
    public String getIcon() {
        // TODO Change this placeholder
        return Global.getSettings().getSpriteName("events", "hyperspace_topography");
    }

    protected String getStageIconImpl(Object stageId) {
        EventStageData esd = getDataFor(stageId);
        if (esd == null) return null;

        // TODO Change this placeholder
        return Global.getSettings().getSpriteName("events", "hyperspace_topography");
    }


    @Override
    public Color getBarColor() {
        Color color = BAR_COLOR;
        //color = Misc.getBasePlayerColor();
        color = Misc.interpolateColor(color, Color.black, 0.25f);
        return color;
    }

    @Override
    public Color getBarProgressIndicatorColor() {
        return super.getBarProgressIndicatorColor();
    }

    @Override
    protected int getStageImportance(Object stageId) {
        return super.getStageImportance(stageId);
    }


    @Override
    protected String getName() {
        return "Intelligence Efforts - The Vass Families";
    }


    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {}
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
		//if (isEnded() || isEnding()) return;
		//if (!battle.isPlayerInvolved()) return;
        // TODO Decide if vass fleets being defeated should give give any sort of reward. Maybe. Possibly.
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("vass");
        tags.remove(Tags.INTEL_MAJOR_EVENT);
        return tags;
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);

        //TODO Introduce actual mechanics here!
    }

    @Override
    protected void notifyStageReached(EventStageData stage) {
        if (stage.id == Stage.LOCATION_SECRET) {
            int min = (int)Misc.interpolate(PROGRESS_6, PROGRESS_MAX, 0.25f);
            int max = (int)Misc.interpolate(PROGRESS_6, PROGRESS_MAX, 0.50f);
            int resetProgress = min + getRandom().nextInt(max - min + 1);
            setProgress(resetProgress);

            //TODO Introduce actual mechanics here!
        }
    }

    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {}

    public void reportAboutToRefreshCharacterStatEffects() {}

    public void reportRefreshedCharacterStatEffects() {}

    public boolean withMonthlyFactors() {
        return true;
    }
}








