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
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        getCommand("boss").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    // --- KOMENDA ADMINISTRATORA ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) || !sender.isOp()) return true;
        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage("§cUżycie: /boss <kataklizm|burza|niszczyciel|wampir|otchlan>");
            return true;
        }

        spawnBoss(p.getLocation(), args[0].toLowerCase());
        p.sendMessage("§aPrzywołano bossa: " + args[0]);
        return true;
    }

    private void spawnBoss(Location loc, String type) {
        LivingEntity boss;
        String name;
        switch (type) {
            case "kataklizm": boss = (WitherSkeleton) loc.getWorld().spawnEntity(loc, EntityType.WITHER_SKELETON); name = "§4§lBoss Kataklizmu"; break;
            case "burza": boss = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE); name = "§b§lWładca Burz"; break;
            case "niszczyciel": boss = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON); name = "§6§lNiszczyciel Światów"; break;
            case "wampir": boss = (Husk) loc.getWorld().spawnEntity(loc, EntityType.HUSK); name = "§c§lWładca Wampirów"; break;
            case "otchlan": boss = (PiglinBrute) loc.getWorld().spawnEntity(loc, EntityType.PIGLIN_BRUTE); name = "§1§lWładca Otchłani"; break;
            default: return;
        }
        boss.setCustomName(name);
        boss.setCustomNameVisible(true);
        boss.setMetadata("boss_type", new FixedMetadataValue(this, type));
        boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(500.0);
        boss.setHealth(500.0);
    }

    // --- LOGIKA DROPÓW (Jeden na serwer) ---
    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (!e.getEntity().hasMetadata("boss_type")) return;
        String type = e.getEntity().getMetadata("boss_type").get(0).asString();
        
        ItemStack artifact = null;
        switch (type) {
            case "kataklizm": artifact = createArtifact(Material.NETHERITE_SWORD, "§4§lOstrze Kataklizmu", "kataklizm", "§7Legendarny miecz zagłady."); break;
            case "burza": artifact = createArtifact(Material.BLAZE_ROD, "§b§lBerło Burzy", "burza", "§7Włada błyskawicami."); break;
            case "niszczyciel": artifact = createArtifact(Material.BOW, "§6§lŁuk Niszczyciela", "niszczyciel", "§7Niszczy całe światy."); break;
            case "wampir": artifact = createArtifact(Material.NETHERITE_SCRAP, "§c§lPierścień Krwawej Furii", "wampir", "§7Wyssij życie z wrogów."); break;
            case "otchlan": artifact = createRelic(); break;
        }
        if (artifact != null) e.getDrops().add(artifact);
    }

    // --- MECHANIKA PRZEDMIOTÓW ---
    @EventHandler
    public void onCombat(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player p = (Player) e.getDamager();
        ItemStack item = p.getInventory().getItemInMainHand();
        String id = getArtifactId(item);

        if (id == null) return;

        // 1. Ostrze Kataklizmu (+40% dmg & Wither)
        if (id.equals("kataklizm") && e.getEntity() instanceof LivingEntity) {
            e.setDamage(e.getDamage() * 1.4);
            if (Math.random() < 0.02) {
                LivingEntity target = (LivingEntity) e.getEntity();
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                target.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, target.getLocation(), 1);
                target.getNearbyEntities(4, 4, 4).forEach(ent -> {
                    if (ent instanceof LivingEntity && ent != p) ((LivingEntity) ent).damage(5.0);
                });
            }
        }

        // 2. Berło Burzy (Pasywka 10% na piorun)
        if (id.equals("burza") && Math.random() < 0.10) {
            e.getEntity().getWorld().strikeLightning(e.getEntity().getLocation());
        }

        // 3. Pierścień Krwawej Furii (Pasywka < 5 serc)
        if (id.equals("wampir") && p.getHealth() <= 10.0) {
            e.setDamage(e.getDamage() * 1.4);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getItemInHand();
        String id = getArtifactId(item);
        if (id == null || !e.getAction().name().contains("RIGHT")) return;

        long now = System.currentTimeMillis();

        // 1. Aktywka: Ostrze Kataklizmu (Odpychanie)
        if (id.equals("kataklizm")) {
            if (checkCooldown(p, "kat_active", 180)) {
                p.getNearbyEntities(6, 6, 6).forEach(ent -> {
                    Vector v = ent.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(2);
                    ent.setVelocity(v);
                });
                p.sendMessage("§4Fala energii odpycha wrogów!");
            }
        }

        // 2. Aktywka: Berło Burzy (3 Pioruny)
        if (id.equals("burza")) {
            if (checkCooldown(p, "burza_active", 120)) {
                Location loc = p.getTargetBlock(null, 30).getLocation();
                for (int i = 0; i < 3; i++) loc.getWorld().strikeLightning(loc);
                loc.getWorld().getNearbyEntities(loc, 4, 4, 4).forEach(ent -> {
                    if (ent instanceof LivingEntity) ((LivingEntity) ent).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 1));
                });
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

    // --- METODY POMOCNICZE ---
    private ItemStack createArtifact(Material mat, String name, String id, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Collections.singletonList(lore));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRelic() {
        ItemStack item = createArtifact(Material.GOLDEN_HELMET, "§1§lRelikt Władcy Otchłani", "otchlan", "§7Zwiększa siły witalne.");
        ItemMeta meta = item.getItemMeta();
        meta.addAttributeModifier(Attribute.GENERIC_MAX_HEALTH, new AttributeModifier(UUID.randomUUID(), "relikthp", 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HEAD));
        item.setItemMeta(meta);
        return item;
    }

    private String getArtifactId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    private boolean checkCooldown(Player p, String key, int seconds) {
        String fullKey = p.getUniqueId() + key;
        long time = cooldowns.getOrDefault(fullKey, 0L);
        if (System.currentTimeMillis() - time < seconds * 1000L) {
            p.sendMessage("§cMusisz poczekać " + (seconds - (System.currentTimeMillis() - time) / 1000) + "s!");
            return false;
        }
        cooldowns.put(fullKey, System.currentTimeMillis());
        return true;
    }
}
