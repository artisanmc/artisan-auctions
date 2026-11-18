package e2e;

import net.artisanmc.auctions.api.AuctionCurrency;
import net.artisanmc.auctions.api.AuctionsService;
import net.artisanmc.modules.api.ArtisanAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Test harness: a "coins" currency, and commands bots use to drive and inspect the house. */
public final class Harness extends JavaPlugin {
    static final Map<UUID, BigDecimal> COINS = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        Bukkit.getServicesManager().register(AuctionCurrency.class, new AuctionCurrency() {
            public String id() { return "coins"; }
            public BigDecimal balance(OfflinePlayer p) { return COINS.getOrDefault(p.getUniqueId(), BigDecimal.ZERO); }
            public boolean withdraw(OfflinePlayer p, BigDecimal a) {
                synchronized (COINS) {
                    BigDecimal b = balance(p);
                    if (b.compareTo(a) < 0) return false;
                    COINS.put(p.getUniqueId(), b.subtract(a));
                    return true;
                }
            }
            public boolean deposit(OfflinePlayer p, BigDecimal a) {
                synchronized (COINS) { COINS.put(p.getUniqueId(), balance(p).add(a)); return true; }
            }
        }, this, ServicePriority.Normal);
    }

    private static void out(CommandSender s, String line) {
        Bukkit.getLogger().info("E2E " + line);
        s.sendMessage("E2E " + line);
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0]) {
            case "act" -> {
                // e2e act <run_command text...>: runs a module command as a menu button would.
                Player p = (Player) sender;
                String command = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                ArtisanAPI api = Bukkit.getServicesManager().load(ArtisanAPI.class);
                if (api == null) { out(sender, "no-artisan-api"); return true; }
                api.getActions().run(p, List.of(Map.of("run_command", command)), Map.of(), "auctions");
                out(sender, "acted " + command);
            }
            case "setmoney" -> {
                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                COINS.put(t.getUniqueId(), new BigDecimal(args[2]));
                out(sender, "money " + args[1] + " " + COINS.get(t.getUniqueId()).toPlainString());
            }
            case "money" -> {
                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                out(sender, "money " + args[1] + " " + COINS.getOrDefault(t.getUniqueId(), BigDecimal.ZERO).stripTrailingZeros().toPlainString());
            }
            case "listings" -> {
                AuctionsService s = Bukkit.getServicesManager().load(AuctionsService.class);
                var all = s.activeListings();
                out(sender, "listings " + all.size());
                all.forEach(l -> out(sender, "listing " + l.id() + " " + l.type() + " " + l.sellerName()));
            }
            case "count" -> {
                Player p = Bukkit.getPlayerExact(args[1]);
                org.bukkit.Material m = org.bukkit.Material.valueOf(args[2]);
                int n = Arrays.stream(p.getInventory().getStorageContents()).filter(i -> i != null && i.getType() == m).mapToInt(i -> i.getAmount()).sum();
                out(sender, "count " + args[1] + " " + args[2] + " " + n);
            }
            default -> { return false; }
        }
        return true;
    }
}
