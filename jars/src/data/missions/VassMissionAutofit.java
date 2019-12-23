package data.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater.AvailableFighterImpl;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater.AvailableWeaponImpl;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.plugins.AutofitPlugin;
import com.fs.starfarer.api.plugins.AutofitPlugin.AvailableFighter;
import com.fs.starfarer.api.plugins.AutofitPlugin.AvailableWeapon;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

/**
 * Taken pretty much wholeheartedly from the Legacy of Arkgneisis implementation
 * Modified slightly to fit better with Vass implementations
 * @author Gwyvern
 */
public class VassMissionAutofit implements AutofitPlugin.AutofitPluginDelegate
{
    public static Logger log = Global.getLogger(VassMissionAutofit.class);

    protected FactionAPI faction;
    protected List<String> hullmods;
    protected List<AvailableWeapon> weapons;
    protected List<AvailableFighter> fighters;
    protected DefaultFleetInflater.SortedWeapons nonPriorityWeapons = new DefaultFleetInflater.SortedWeapons();
    protected DefaultFleetInflater.SortedWeapons priorityWeapons = new DefaultFleetInflater.SortedWeapons();

    public VassMissionAutofit(FactionAPI faction) {
        this.faction = faction;
        init();
    }

    public void init() {
        hullmods = new ArrayList<>(faction.getKnownHullMods());
        weapons = new ArrayList<>();
        for (String weaponId : faction.getKnownWeapons()) {
            WeaponSpecAPI weapon = Global.getSettings().getWeaponSpec(weaponId);
            AvailableWeaponImpl avail = new AvailableWeaponImpl(weapon, 1000);
            weapons.add(avail);
        }
        fighters = new ArrayList<>();
        for (String fighterId : faction.getKnownFighters()) {
            FighterWingSpecAPI wing = Global.getSettings().getFighterWingSpec(fighterId);
            AvailableFighterImpl avail = new AvailableFighterImpl(wing, 1000);
            fighters.add(avail);
        }
    }

    @Override
    public void clearFighterSlot(int index, ShipVariantAPI variant)
    {
        variant.setWingId(index, null);
    }

    @Override
    public void clearWeaponSlot(WeaponSlotAPI slot, ShipVariantAPI variant)
    {
        variant.clearSlot(slot.getId());
    }

    @Override
    public void fitFighterInSlot(int index, AvailableFighter fighter, ShipVariantAPI variant) {
        variant.setWingId(index, fighter.getId());
    }

    @Override
    public void fitWeaponInSlot(WeaponSlotAPI slot, AvailableWeapon weapon, ShipVariantAPI variant) {
        variant.addWeapon(slot.getId(), weapon.getId());
    }

    @Override
    public List<AvailableFighter> getAvailableFighters()
    {
        return fighters;
    }

    @Override
    public List<AvailableWeapon> getAvailableWeapons()
    {
        return weapons;
    }

    @Override
    public List<String> getAvailableHullmods()
    {
        return hullmods;
    }

    @Override
    public ShipAPI getShip()
    {
        return null;
    }

    @Override
    public void syncUIWithVariant(ShipVariantAPI variant) {}

    @Override
    public boolean isPriority(WeaponSpecAPI weapon)
    {
        return faction.isWeaponPriority(weapon.getWeaponId());
    }

    @Override
    public boolean isPriority(FighterWingSpecAPI wing)
    {
        return faction.isFighterPriority(wing.getId());
    }

    @Override
    public FactionAPI getFaction()
    {
        return faction;
    }

    @Override
    public boolean isPlayerCampaignRefit()
    {
        return false;
    }

    @Override
    public boolean canAddRemoveHullmodInPlayerCampaignRefit(String mod)
    {
        return true;
    }
}
