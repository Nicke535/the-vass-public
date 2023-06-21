package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomProductionPickerDelegateImpl;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import org.apache.log4j.Logger;

import java.util.List;

public class VassSectorSetupScript {
    private final static Logger LOGGER = Global.getLogger(VassSectorSetupScript.class);

    /**
     * Runs all setup for the vass during sector generation. Pretty empty right now, actually
     * @param sector sector to generate in
     */
    public static void runSetup(SectorAPI sector) {
        //TODO: actually add stuff here
    }

    /**
     * Spawns a test derelict, to more easily test that campaign features are working as intended
     *
     */
    public static void testSpawnDerelict(SectorAPI sector) {
        StarSystemAPI system = sector.getStarSystem("Corvus");

        if (system == null) {
            throw new RuntimeException("Corvus could not be found!");
        }

        PlanetAPI star = system.getStar();
        if (star == null) {
            throw new RuntimeException("Corvus has no star!");
        }

        VassCampaignUtils.addDerelict(system,
                star,
                "vass_schiavona_hailstorm",
                ShipRecoverySpecial.ShipCondition.WRECKED,
                1000,
                5,
                0f,
                true,
                null);

        LOGGER.info("Successfully added Schiavona to Corvus for test purposes");
    }
}
