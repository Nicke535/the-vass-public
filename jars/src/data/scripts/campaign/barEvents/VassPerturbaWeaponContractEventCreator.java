package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

/**
 * Spawns the bar event for the Perturba weapon contract offer
 */
public class VassPerturbaWeaponContractEventCreator extends BaseBarEventCreator {

    public PortsideBarEvent createBarEvent() {
        return new VassPerturbaWeaponContractEvent();
    }

    @Override
    public float getBarEventAcceptedTimeoutDuration() {
        return 10000000000f; // damnit, Alex; couldn't you've just given us an infinity option?
    }

    //A bit higher frequency than normal; it's a fairly important event after all
    @Override
    public float getBarEventFrequencyWeight() {
        if (VassPerturbaWeaponContractEvent.DEBUG_MODE) {
            return 99999999999f;
        } else {
            return super.getBarEventFrequencyWeight() * 2f;
        }
    }
}