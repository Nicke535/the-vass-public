package data.scripts.campaign.familyInformationFactors;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;

public class VassFirstLootingFactor extends BaseOneTimeFactor {

    public VassFirstLootingFactor(int points) {
        super(points);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return "Data from Vass ships";
    }

    @Override
    public TooltipCreator getMainRowTooltip() {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Surface data recovered from unencrypted devices left in recent Vass ship acquisitions.",
                        0f);
            }

        };
    }

}