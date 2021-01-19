package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemQuantity;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Perturba's special submarket for selling Vass ships and fighters.
 * Mostly taken from Prism Freeport's implementation
 *
 * @author Nicke535
 */
public class VassPerturbaSubmarket extends BaseSubmarketPlugin {

    /* Configurations */
    private static final int NUMBER_OF_SHIPS = 6;
    private static final int NUMBER_OF_FIGHTERS = 8;
    private static final int MIN_DMODS = 1;
    private static final int MAX_DMODS = 2;

    private static final List<String> SHIPS_SOLD = new ArrayList<>();
    static {
        SHIPS_SOLD.add("vass_makhaira");
        SHIPS_SOLD.add("vass_akrafena");
        SHIPS_SOLD.add("vass_schiavona");
        SHIPS_SOLD.add("vass_curtana");
    }

    private static final List<String> FIGHTERS_SOLD = new ArrayList<>();
    static {
        FIGHTERS_SOLD.add("vass_estoc_wing");
        FIGHTERS_SOLD.add("vass_katzbalger_wing");
    }

    //Logger, for... well, logging!
    private static Logger log = Global.getLogger(VassPerturbaSubmarket.class);


    @Override
    public void updateCargoPrePlayerInteraction() {
        if (sinceLastCargoUpdate < 1f) return;
        sinceLastCargoUpdate = 0f;

        CargoAPI cargo = getCargo();

        pruneShips(0f);
        pruneWeapons(0f);
        addShips();
        addWings();
        cargo.sort();
    }

    protected void addWings() {
        CargoAPI cargo = getCargo();
        WeightedRandomPicker<String> fighterPicker = new WeightedRandomPicker<>(itemGenRandom);
        for (String id : FIGHTERS_SOLD) {
            if (Global.getSector().getFaction("vass_perturba").knowsFighter(id)) {
                FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
                fighterPicker.add(id, (10 - spec.getTier()) * spec.getRarity());
            }
        }

        int picks = 0;
        for (CargoItemQuantity<String> quantity : cargo.getFighters()) {
            picks += quantity.getCount();
        }
        while (!fighterPicker.isEmpty() && picks < NUMBER_OF_FIGHTERS) {
            String id = fighterPicker.pick();
            cargo.addItems(CargoAPI.CargoItemType.FIGHTER_CHIP, id, 1);
            picks++;
        }
    }

    //Based on an old vanilla implementation...supposedly
    protected void addShips() {
        CargoAPI cargo = getCargo();

        WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>(itemGenRandom);
        rolePicker.add(ShipRoles.COMBAT_SMALL, 35f);
        rolePicker.add(ShipRoles.COMBAT_MEDIUM, 30f);
        rolePicker.add(ShipRoles.COMBAT_LARGE, 25f);
        rolePicker.add(ShipRoles.CARRIER_SMALL, 25f);
        rolePicker.add(ShipRoles.CARRIER_MEDIUM, 25f);
        rolePicker.add(ShipRoles.CARRIER_LARGE, 25f);


        int tries = 0;
        FactionAPI faction = Global.getSector().getFaction("vass_perturba");
        for (int i = 0; i < NUMBER_OF_SHIPS; i++) {
            //Pick a ship role
            List<ShipRolePick> picks = null;
            int tries2 = 0;
            do {
                tries2++;
                String role = rolePicker.pick();
                try {
                    picks = faction.pickShip(role, ShipPickParams.priority());
                } catch (NullPointerException e) {
                    // likely picker picked a role when faction has no ships for that role; do nothing
                }
            } while (picks == null && tries2 < 40);
            if (picks != null) {
                for (ShipRolePick pick : picks) {
                    FleetMemberType type = FleetMemberType.SHIP;
                    String variantId = pick.variantId;

                    FleetMemberAPI member = Global.getFactory().createFleetMember(type, variantId);
                    String hullId = member.getHullId();
                    variantId = hullId + "_Hull";
                    member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);

                    // Adds D-mods randomly: they are only allowed to sell you "defect" ships
                    int dModCount = MathUtils.getRandomNumberInRange(MIN_DMODS, MAX_DMODS);
                    if (dModCount > 0) {
                        DModManager.setDHull(member.getVariant());
                        DModManager.addDMods(member, true, dModCount, null);
                    }

                    //Disallow ships that we aren't allowed to sell
                    if (SHIPS_SOLD.contains(hullId)) {
                        member.getRepairTracker().setMothballed(true);
                        member.getRepairTracker().setCR(0.5f);
                        getCargo().getMothballedShips().addFleetMember(member);
                    } else {
                        log.warn("Threw away Vass ship : "+hullId);
                        i -= 1;
                    }
                }
            }
            tries++;
            if (tries > 40) {
                log.warn("The Vass ship submarket failed to add ships properly due to some unforseen circumstance");
                break;
            }
        }
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) {
            if (stack.isFighterWingStack() && stack.getFighterWingSpecIfWing().getId().startsWith("vass_")) {
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) return true;
        return false;
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) {
            if (member.getHullId().startsWith("vass_")) {
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    public float getTariff() {
        return 0.5f;
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "Only Vass ships can be sold here";
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        return "Only Vass LPCs can be sold here";
    }

    @Override
    public boolean isBlackMarket() {
        return false;
    }
}