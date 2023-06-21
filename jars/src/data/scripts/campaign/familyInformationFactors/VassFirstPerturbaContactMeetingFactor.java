package data.scripts.campaign.familyInformationFactors;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;

public class VassFirstPerturbaContactMeetingFactor extends BaseOneTimeFactor {

    public VassFirstPerturbaContactMeetingFactor(int points) {
        super(points);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return "Met Perturba contact";
    }

    @Override
    public TooltipCreator getMainRowTooltip() {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Met and talked to one of Perturba's contacts.",
                        0f);
            }

        };
    }

}