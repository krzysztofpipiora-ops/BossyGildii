package pl.twojserwer.gildie;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class GuildBosses extends JavaPlugin implements Listener, CommandExecutor {

    private final NamespacedKey itemKey = new NamespacedKey(this, "unique_artifact");
    private final Map<String, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        if (getCommand("boss") != null) {
            getCommand("boss").setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        
        // Sprawdzanie Pierscienia co sekunde
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack item = p.getInventory().getItemInMainHand();
                if (isArtifact(item, "wampir") && p.getHealth() <= 10.0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 35, 0, false, false, true));
                }
            }
        }, 20L, 20L);

        getLogger().info("Plugin BossyGildii (Kataklizm 5% pvp) wlaczony!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        if (!p.isOp()) {
            p.sendMessage("§cNie masz uprawnien!");
            return true;
        }

        if (args.length == 0) {
            p.sendMessage("§cUzycie: /boss <kataklizm|burza|niszczyciel|wampir|otchlan>");
            return true;
        }

        spawnBoss(p.getLocation(), args[0].toLowerCase());
        p.sendMessage("§6§l[!] §ePrzywolano bossa (500HP): §f" + args[0]);
        return true;
    }

    private void spawnBoss(Location loc, String type) {
        LivingEntity boss;
        String name;
        switch (type) {
            case "kataklizm": boss = (WitherSkeleton) loc.getWorld().spawnEntity(loc, EntityType.WITHER_SKELETON); name = "§4§lBoss Kataklizmu"; break;
            case "burza": boss = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE); name = "§b§lWladca Burz"; break;
            case "niszczyciel": boss = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON); name = "§6§lNiszczyciel Swiatow"; break;
            case "wampir": boss = (Husk) loc.getWorld().spawnEntity(loc, EntityType.HUSK); name = "§c§lWladca Wampirow"; break;
            case "otchlan": boss = (PiglinBrute) loc.getWorld().spawnEntity(loc, EntityType.PIGLIN_BRUTE); name = "§1§lWladca Otchlani"; break;
            default: return;
        }

        boss.setCustomName(name);
        boss.setCustomNameVisible(true);
        boss.setMetadata("boss_type", new FixedMetadataValue(this, type));
        
        if (boss.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(500.0);
            boss.setHealth(500.0);
        }
        
        boss.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999, 0));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0));
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (!e.getEntity().hasMetadata("boss_type")) return;
        String type = e.getEntity().getMetadata("boss_type").get(0).asString();
        
        ItemStack artifact = null;
        switch (type) {
            case "kataklizm": artifact = createArtifact(Material.NETHERITE_SWORD, "§4§lOstrze Kataklizmu", "kataklizm", "§c+5% obrażeń PvP"); break;
            case "burza": artifact = createArtifact(Material.BLAZE_ROD, "§b§lBerlo Burzy", "burza", "§7Wlada piorunami."); break;
            case "niszczyciel": artifact = createArtifact(Material.BOW, "§6§lLuk Niszczyciela", "niszczyciel", "§7Luk Niszczyciela Swiatow."); break;
            case "wampir": artifact = createArtifact(Material.NETHERITE_SCRAP, "§c§lPierscien Krwawej Furii", "wampir", "§7Pierscien Wladcy Wampirow."); break;
            case "otchlan": artifact = createRelic(); break;
        }
        if (artifact != null) e.getDrops().add(artifact);
    }

    @EventHandler
    public void onCombat(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player p = (Player) e.getDamager();
        ItemStack item = p.getInventory().getItemInMainHand();
        String id = getArtifactId(item);

        if (id == null) return;

        // Ostrze Kataklizmu: Zmienione na +5% (mnoznik 1.05)
        if (id.equals("kataklizm")) {
            if (e.getEntity() instanceof Player) {
                e.setDamage(e.getDamage() * 1.05);
            }
            if (Math.random() < 0.02 && e.getEntity() instanceof LivingEntity) {
                LivingEntity target = (LivingEntity) e.getEntity();
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                target.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, target.getLocation(), 1);
            }
        }

        if (id.equals("burza") && Math.random() < 0.10) {
            e.getEntity().getWorld().strikeLightning(e.getEntity().getLocation());
        }

        if (id.equals("wampir") && p.getHealth() <= 10.0) {
            e.setDamage(e.getDamage() * 1.4);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        String id = getArtifactId(item);
        if (id == null || !e.getAction().name().contains("RIGHT")) return;

        if (id.equals("kataklizm")) {
            if (checkCooldown(p, "kat_active", 180)) {
                p.getNearbyEntities(6, 6, 6).forEach(ent -> {
                    Vector v = ent.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.8);
                    ent.setVelocity(v);
                });
                p.sendMessage("§4§lKataklizm: §fFala energii!");
            }
        }

        if (id.equals("burza")) {
            if (checkCooldown(p, "burza_active", 120)) {
                Location loc = p.getTargetBlock(null, 30).getLocation();
                loc.getWorld().strikeLightning(loc);
                p.sendMessage("§b§lBurza: §fPrzywolano piorun!");
            }
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        String id = getArtifactId(e.getBow());
        if ("niszczyciel".equals(id)) {
            Player p = (Player) e.getEntity();
            if (Math.random() < 0.20) e.getProjectile().setFireTicks(2000);
            if (checkCooldown(p, "luk_aoe", 60)) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    e.getProjectile().getWorld().createExplosion(e.getProjectile().getLocation(), 2.0f, false, false);
                }, 20L);
            }
        }
    }

    private ItemStack createArtifact(Material mat, String name, String id, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(lore));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRelic() {
        ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§1§lRelikt Wladcy Otchlani");
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "otchlan");
            UUID attrUuid = UUID.nameUUIDFromBytes("relic_hp_key".getBytes());
            AttributeModifier modifier = new AttributeModifier(attrUuid, "relic_hp", 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HEAD);
            meta.addAttributeModifier(Attribute.GENERIC_MAX_HEALTH, modifier);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isArtifact(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return id.equals(val);
    }

    private String getArtifactId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    private boolean checkCooldown(Player p, String key, int seconds) {
        String fullKey = p.getUniqueId().toString() + key;
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(fullKey, 0L);
        if (now - last < seconds * 1000L) {
            p.sendMessage("§cOdczekaj " + (seconds - (now - last) / 1000) + "s!");
            return false;
        }
        cooldowns.put(fullKey, now);
        return true;
    }
}
