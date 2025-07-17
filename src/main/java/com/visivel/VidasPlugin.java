package vidaplugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.BanList.Type;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class VidasPlugin extends JavaPlugin implements Listener, TabExecutor {
   private static final int MAX_VIDAS = 10;
   private static final int START_VIDAS = 5;
   private static final String VIDA_KEY = "plugin.vidas";
   private static final String BANNED_KEY = "plugin.banned";
   private NamespacedKey vidaItemKey;
   private NamespacedKey totemKey;
   private Map vidas;
   private Map bannedVidasRevive;
   private Map lastKiller;
   private static final String GUI_TITLE;

   public void onEnable() {
      this.vidas = new HashMap();
      this.bannedVidasRevive = new HashMap();
      this.lastKiller = new HashMap();
      this.getServer().getPluginManager().registerEvents(this, this);
      this.getCommand("vidas").setExecutor(this);
      this.getCommand("vidas").setTabCompleter(this);
      this.getCommand("vidastotem").setExecutor(this);
      this.vidaItemKey = new NamespacedKey(this, "vida_item");
      this.totemKey = new NamespacedKey(this, "totem_redencao");
      this.loadBannedVidas();
      Iterator var1 = Bukkit.getOnlinePlayers().iterator();

      while(var1.hasNext()) {
         Player p = (Player)var1.next();
         this.loadPlayerVida(p);
      }

      this.registerRecipes();
      this.getLogger().info(String.valueOf(ChatColor.GREEN) + "Vidas Plugin habilitado!");
   }

   public void onDisable() {
      Iterator var1 = Bukkit.getOnlinePlayers().iterator();

      while(var1.hasNext()) {
         Player p = (Player)var1.next();
         this.savePlayerVida(p);
      }

      this.saveBannedVidas();
      this.getLogger().info(String.valueOf(ChatColor.RED) + "Vidas Plugin desabilitado!");
   }

   private void loadBannedVidas() {
      FileConfiguration config = this.getConfig();
      if (config.isConfigurationSection("plugin.banned")) {
         Iterator var2 = config.getConfigurationSection("plugin.banned").getKeys(false).iterator();

         while(var2.hasNext()) {
            String key = (String)var2.next();

            try {
               UUID uuid = UUID.fromString(key);
               int vidasCount = config.getInt("plugin.banned." + key);
               this.bannedVidasRevive.put(uuid, vidasCount);
            } catch (IllegalArgumentException var6) {
            }
         }
      }

   }

   private void saveBannedVidas() {
      FileConfiguration config = this.getConfig();
      config.set("plugin.banned", (Object)null);
      Iterator var2 = this.bannedVidasRevive.entrySet().iterator();

      while(var2.hasNext()) {
         Map.Entry entry = (Map.Entry)var2.next();
         config.set("plugin.banned." + ((UUID)entry.getKey()).toString(), entry.getValue());
      }

      this.saveConfig();
   }

   private void loadPlayerVida(Player player) {
      FileConfiguration config = this.getConfig();
      UUID uid = player.getUniqueId();
      int vida = config.getInt("plugin.vidas." + uid.toString(), 5);
      if (vida < 1) {
         vida = 5;
      }

      this.vidas.put(uid, vida);
   }

   private void savePlayerVida(Player player) {
      FileConfiguration config = this.getConfig();
      UUID uid = player.getUniqueId();
      Integer vida = (Integer)this.vidas.get(uid);
      if (vida != null) {
         config.set("plugin.vidas." + uid.toString(), vida);
         this.saveConfig();
      }

   }

   private int getVida(Player player) {
      return (Integer)this.vidas.getOrDefault(player.getUniqueId(), 5);
   }

   private void setVida(Player player, int qtd) {
      qtd = Math.min(10, Math.max(0, qtd));
      this.vidas.put(player.getUniqueId(), qtd);
      this.savePlayerVida(player);
      if (qtd == 0) {
         UUID uuid = player.getUniqueId();
         this.bannedVidasRevive.put(uuid, 0);
         this.saveBannedVidas();
         UUID killerUUID = (UUID)this.lastKiller.get(uuid);
         String killerName = "Desconhecido";
         if (killerUUID != null) {
            Player killerPlayer = Bukkit.getPlayer(killerUUID);
            if (killerPlayer != null) {
               killerName = killerPlayer.getName();
               String var10001 = String.valueOf(ChatColor.RED);
               killerPlayer.sendMessage(var10001 + "Você baniu " + player.getName() + " por falta de vidas.");
            }
         }

         String var10000 = String.valueOf(ChatColor.RED);
         Bukkit.broadcastMessage(Component.text(var10000 + player.getName() + " foi banido por ficar sem vidas! Morto por: " + killerName).toString());
         player.kickPlayer(Component.text(String.valueOf(ChatColor.RED) + "Você foi banido por ficar sem vidas!").toString());
         this.getServer().getBanList(Type.NAME).addBan(player.getName(), "Sem mais vidas", (Date)null, (String)null);
         this.lastKiller.remove(uuid);
      } else if (player.isOnline() && !player.isDead()) {
         double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
         if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
         }
      }

   }

   private void addVida(Player player, int qtd) {
      int atual = this.getVida(player);
      int nova = atual + qtd;
      if (nova > 10) {
         nova = 10;
         player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Você atingiu o limite máximo de 10 vidas!");
      } else {
         String var10001 = String.valueOf(ChatColor.GREEN);
         player.sendMessage(var10001 + "Você ganhou +1 vida! Agora tem " + nova + " vidas.");
         player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
      }

      this.setVida(player, nova);
   }

   private void removeVida(Player player, int qtd) {
      int atual = this.getVida(player);
      this.setVida(player, atual - qtd);
   }

   private ItemStack createVidaItem() {
      ItemStack item = new ItemStack(Material.APPLE);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(String.valueOf(ChatColor.RED) + "Item de Vida");
      meta.setLore(Collections.singletonList(String.valueOf(ChatColor.WHITE) + "Clique com botão direito para +1 vida"));
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
      meta.addEnchant(Enchantment.UNBREAKING, 1, true);
      meta.getPersistentDataContainer().set(this.vidaItemKey, PersistentDataType.BYTE, (byte)1);
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createTotemRedencao() {
      ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(String.valueOf(ChatColor.GOLD) + "Totem da Redenção");
      meta.setLore(Collections.singletonList(String.valueOf(ChatColor.WHITE) + "Clique para abrir o menu de reviver"));
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
      meta.addEnchant(Enchantment.UNBREAKING, 1, true);
      meta.getPersistentDataContainer().set(this.totemKey, PersistentDataType.BYTE, (byte)1);
      item.setItemMeta(meta);
      return item;
   }

   private void registerRecipes() {
      ShapedRecipe vidaRecipe = new ShapedRecipe(new NamespacedKey(this, "vida_item_recipe"), this.createVidaItem());
      vidaRecipe.shape(new String[]{" G ", "GAG", " G "});
      vidaRecipe.setIngredient('G', Material.GOLD_INGOT);
      vidaRecipe.setIngredient('A', Material.APPLE);
      this.getServer().addRecipe(vidaRecipe);
      ShapedRecipe totemRecipe = new ShapedRecipe(new NamespacedKey(this, "totem_redencao_recipe"), this.createTotemRedencao());
      totemRecipe.shape(new String[]{"GFG", "FTF", "GFG"});
      totemRecipe.setIngredient('G', Material.GOLD_INGOT);
      totemRecipe.setIngredient('F', Material.FEATHER);
      totemRecipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
      this.getServer().addRecipe(totemRecipe);
   }

   @EventHandler
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Player p = event.getPlayer();
         ItemStack item = event.getItem();
         if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.getPersistentDataContainer().has(this.vidaItemKey, PersistentDataType.BYTE)) {
               event.setCancelled(true);
               if (this.getVida(p) >= 10) {
                  p.sendMessage(String.valueOf(ChatColor.RED) + "Você já tem o máximo de vidas.");
                  return;
               }

               this.addVida(p, 1);
               item.setAmount(item.getAmount() - 1);
               p.sendMessage(String.valueOf(ChatColor.GREEN) + "Você usou um item de vida!");
               p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
               p.updateInventory();
            }

            if (meta.getPersistentDataContainer().has(this.totemKey, PersistentDataType.BYTE)) {
               event.setCancelled(true);
               this.openBanidosGUI(p);
            }

         }
      }
   }

   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player morto = event.getEntity();
      Player assassino = morto.getKiller();
      if (assassino != null && assassino instanceof Player) {
         this.lastKiller.put(morto.getUniqueId(), assassino.getUniqueId());
         this.removeVida(morto, 1);
         morto.sendMessage(String.valueOf(ChatColor.RED) + "Você perdeu 1 vida por morte para jogador.");
         if (this.getVida(assassino) >= 10) {
            ItemStack vidaItem = this.createVidaItem();
            Map sobra = assassino.getInventory().addItem(new ItemStack[]{vidaItem});
            if (!sobra.isEmpty()) {
               assassino.getWorld().dropItemNaturally(assassino.getLocation(), vidaItem);
            }

            assassino.sendMessage(String.valueOf(ChatColor.YELLOW) + "Você já tem 10 vidas, então ganhou 1 item de vida.");
         } else {
            this.addVida(assassino, 1);
            assassino.sendMessage(String.valueOf(ChatColor.GREEN) + "Você ganhou +1 vida por matar!");
         }
      }

   }

   private void openBanidosGUI(Player p) {
      List banidos = new ArrayList(this.bannedVidasRevive.keySet());
      if (banidos.isEmpty()) {
         p.sendMessage(String.valueOf(ChatColor.YELLOW) + "Não há jogadores banidos para reviver.");
      } else {
         int size = ((banidos.size() - 1) / 9 + 1) * 9;
         Inventory inv = Bukkit.createInventory((InventoryHolder)null, size, GUI_TITLE);

         for(int i = 0; i < banidos.size(); ++i) {
            UUID uid = (UUID)banidos.get(i);
            OfflinePlayer offp = Bukkit.getOfflinePlayer(uid);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta sm = (SkullMeta)head.getItemMeta();
            sm.setOwningPlayer(offp);
            String var10001 = String.valueOf(ChatColor.RED);
            sm.setDisplayName(var10001 + offp.getName());
            sm.setLore(Collections.singletonList(String.valueOf(ChatColor.GRAY) + "Clique para reviver este jogador"));
            head.setItemMeta(sm);
            inv.setItem(i, head);
         }

         p.openInventory(inv);
      }
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getView().getTitle().equals(GUI_TITLE)) {
         event.setCancelled(true);
         if (event.getCurrentItem() != null) {
            if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
               Player clicador = (Player)event.getWhoClicked();
               ItemStack item = event.getCurrentItem();
               SkullMeta sm = (SkullMeta)item.getItemMeta();
               if (sm != null) {
                  String nomeBanido = ChatColor.stripColor(sm.getDisplayName());
                  OfflinePlayer off = Bukkit.getOfflinePlayer(nomeBanido);
                  if (!this.bannedVidasRevive.containsKey(off.getUniqueId())) {
                     clicador.sendMessage(String.valueOf(ChatColor.RED) + "Este jogador não está banido via sistema.");
                  } else {
                     this.bannedVidasRevive.remove(off.getUniqueId());
                     this.saveBannedVidas();
                     Bukkit.getBanList(Type.NAME).pardon(off.getName());
                     this.getConfig().set("plugin.vidas." + off.getUniqueId().toString(), 2);
                     this.saveConfig();
                     if (off.isOnline()) {
                        Player online = off.getPlayer();
                        this.vidas.put(online.getUniqueId(), 2);
                        this.savePlayerVida(online);
                        online.sendMessage(String.valueOf(ChatColor.GREEN) + "Você foi revivido com 2 vidas via Totem da Redenção!");
                        online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);
                     }

                     String reviverName = clicador.getName();
                     UUID revivedUUID = off.getUniqueId();
                     UUID killerUUID = (UUID)this.lastKiller.get(revivedUUID);
                     String killerName = "Desconhecido";
                     if (killerUUID != null) {
                        Player killerPlayer = Bukkit.getPlayer(killerUUID);
                        if (killerPlayer != null) {
                           killerName = killerPlayer.getName();
                        }
                     }

                     Bukkit.broadcastMessage(Component.text(String.valueOf(ChatColor.GOLD) + reviverName + " reviveu " + nomeBanido + "! Morto por: " + killerName).toString());
                     String var10001 = String.valueOf(ChatColor.GREEN);
                     clicador.sendMessage(var10001 + "Você reviveu " + nomeBanido + " com 2 vidas!");
                     clicador.playSound(clicador.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);
                     this.removeTotemRedencao(clicador);
                     clicador.closeInventory();
                  }
               }
            }
         }
      }
   }

   private void removeTotemRedencao(Player p) {
      for(int i = 0; i < p.getInventory().getSize(); ++i) {
         ItemStack item = p.getInventory().getItem(i);
         if (item != null && item.getItemMeta() != null && item.getItemMeta().getPersistentDataContainer().has(this.totemKey, PersistentDataType.BYTE)) {
            int amt = item.getAmount();
            if (amt > 1) {
               item.setAmount(amt - 1);
               p.getInventory().setItem(i, item);
            } else {
               p.getInventory().setItem(i, (ItemStack)null);
            }

            p.updateInventory();
            return;
         }
      }

   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      Player p;
      if (command.getName().equalsIgnoreCase("vidastotem")) {
         if (sender instanceof Player) {
            p = (Player)sender;
            if (!p.isOp()) {
               p.sendMessage(String.valueOf(ChatColor.RED) + "Você precisa ser OP para usar este comando.");
               return true;
            } else {
               p.getInventory().addItem(new ItemStack[]{this.createTotemRedencao()});
               p.sendMessage(String.valueOf(ChatColor.GREEN) + "Você recebeu um Totem da Redenção.");
               return true;
            }
         } else {
            sender.sendMessage(String.valueOf(ChatColor.RED) + "Apenas jogadores podem usar esse comando.");
            return true;
         }
      } else if (command.getName().equalsIgnoreCase("vidas")) {
         if (sender instanceof Player) {
            p = (Player)sender;
            String var10001;
            if (args.length == 0) {
               int vidasCount = this.getVida(p);
               var10001 = String.valueOf(ChatColor.GREEN);
               p.sendMessage(var10001 + "Você tem " + vidasCount + " vidas.");
               return true;
            } else {
               switch (args[0].toLowerCase()) {
                  case "ver":
                     if (!p.hasPermission("vidas.admin")) {
                        p.sendMessage(String.valueOf(ChatColor.RED) + "Sem permissão.");
                        return true;
                     } else if (args.length < 2) {
                        p.sendMessage(String.valueOf(ChatColor.RED) + "Uso: /vidas ver <jogador>");
                        return true;
                     } else {
                        Player targetVer = Bukkit.getPlayerExact(args[1]);
                        if (targetVer == null) {
                           p.sendMessage(String.valueOf(ChatColor.RED) + "Jogador não encontrado.");
                           return true;
                        }

                        int vidasVer = this.getVida(targetVer);
                        var10001 = String.valueOf(ChatColor.GREEN);
                        p.sendMessage(var10001 + targetVer.getName() + " tem " + vidasVer + " vidas.");
                        return true;
                     }
                  case "set":
                     if (!p.hasPermission("vidas.admin")) {
                        p.sendMessage(String.valueOf(ChatColor.RED) + "Sem permissão.");
                        return true;
                     } else if (args.length < 3) {
                        p.sendMessage(String.valueOf(ChatColor.RED) + "Uso: /vidas set <jogador> <quantidade>");
                        return true;
                     } else {
                        Player targetSet = Bukkit.getPlayerExact(args[1]);
                        if (targetSet == null) {
                           p.sendMessage(String.valueOf(ChatColor.RED) + "Jogador não encontrado.");
                           return true;
                        } else {
                           int qtdSet;
                           try {
                              qtdSet = Integer.parseInt(args[2]);
                           } catch (NumberFormatException var29) {
                              p.sendMessage(String.valueOf(ChatColor.RED) + "Quantidade inválida.");
                              return true;
                           }

                           this.setVida(targetSet, qtdSet);
                           var10001 = String.valueOf(ChatColor.GREEN);
                           p.sendMessage(var10001 + "Você definiu " + targetSet.getName() + " para " + qtdSet + " vidas.");
                           var10001 = String.valueOf(ChatColor.GREEN);
                           targetSet.sendMessage(var10001 + "Suas vidas foram definidas para " + qtdSet + ".");
                           return true;
                        }
                     }
                  case "give":
                     if (!p.hasPermission("vidas.admin")) {
                        p.sendMessage(String.valueOf(ChatColor.RED) + "Sem permissão.");
                        return true;
                     } else if (args.length < 3) {
                        p.sendMessage(String.valueOf(ChatColor.RED) + "Uso: /vidas give <jogador> <quantidade>");
                        return true;
                     } else {
                        Player targetGive = Bukkit.getPlayerExact(args[1]);
                        if (targetGive == null) {
                           p.sendMessage(String.valueOf(ChatColor.RED) + "Jogador não encontrado.");
                           return true;
                        } else {
                           int qtdGive;
                           try {
                              qtdGive = Integer.parseInt(args[2]);
                           } catch (NumberFormatException var28) {
                              p.sendMessage(String.valueOf(ChatColor.RED) + "Quantidade inválida.");
                              return true;
                           }

                           this.addVida(targetGive, qtdGive);
                           p.sendMessage(String.valueOf(ChatColor.GREEN) + "Você deu " + qtdGive + " vidas para " + targetGive.getName());
                           var10001 = String.valueOf(ChatColor.GREEN);
                           targetGive.sendMessage(var10001 + "Você recebeu " + qtdGive + " vidas.");
                           return true;
                        }
                     }
                  case "revive":
                     if (!p.hasPermission("vidas.admin")) {
                        p.sendMessage(String.valueOf(ChatColor.RED) + "Sem permissão.");
                        return true;
                     } else if (args.length < 2) {
                        p.sendMessage(String.valueOf(ChatColor.RED) + "Uso: /vidas revive <jogador>");
                        return true;
                     } else {
                        Player targetRevive = Bukkit.getPlayerExact(args[1]);
                        if (targetRevive == null) {
                           p.sendMessage(String.valueOf(ChatColor.RED) + "Jogador não encontrado.");
                           return true;
                        } else {
                           if (!this.bannedVidasRevive.containsKey(targetRevive.getUniqueId())) {
                              p.sendMessage(String.valueOf(ChatColor.RED) + "Esse jogador não está banido no sistema.");
                              return true;
                           }

                           this.bannedVidasRevive.remove(targetRevive.getUniqueId());
                           this.saveBannedVidas();
                           Bukkit.getBanList(Type.NAME).pardon(targetRevive.getName());
                           this.setVida(targetRevive, 2);
                           var10001 = String.valueOf(ChatColor.GREEN);
                           p.sendMessage(var10001 + targetRevive.getName() + " foi revivido com 2 vidas.");
                           if (targetRevive.isOnline()) {
                              targetRevive.sendMessage(String.valueOf(ChatColor.GREEN) + "Você foi revivido com 2 vidas!");
                              targetRevive.playSound(targetRevive.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);
                           }

                           String var10000 = String.valueOf(ChatColor.GOLD);
                           Bukkit.broadcastMessage(Component.text(var10000 + p.getName() + " reviveu " + targetRevive.getName() + "!").toString());
                           return true;
                        }
                     }
                  case "doar":
                     if (sender instanceof Player) {
                        Player pDoar = (Player)sender;
                        if (args.length < 3) {
                           pDoar.sendMessage(String.valueOf(ChatColor.RED) + "Uso: /vidas doar <jogador> <quantidade>");
                           return true;
                        }

                        Player targetDoar = Bukkit.getPlayerExact(args[1]);
                        if (targetDoar == null) {
                           pDoar.sendMessage(String.valueOf(ChatColor.RED) + "Jogador não encontrado.");
                           return true;
                        }

                        int qtdDoar;
                        try {
                           qtdDoar = Integer.parseInt(args[2]);
                        } catch (NumberFormatException var27) {
                           pDoar.sendMessage(String.valueOf(ChatColor.RED) + "Quantidade inválida.");
                           return true;
                        }

                        int myVida = this.getVida(pDoar);
                        if (myVida < 2) {
                           pDoar.sendMessage(String.valueOf(ChatColor.RED) + "Você precisa ter pelo menos 2 vidas para doar.");
                           return true;
                        }

                        if (qtdDoar < 1) {
                           pDoar.sendMessage(String.valueOf(ChatColor.RED) + "Quantidade mínima para doar é 1.");
                           return true;
                        }

                        if (myVida - qtdDoar < 1) {
                           pDoar.sendMessage(String.valueOf(ChatColor.RED) + "Você não pode doar essa quantidade, ficaria sem vidas.");
                           return true;
                        }

                        this.removeVida(pDoar, qtdDoar);
                        this.addVida(targetDoar, qtdDoar);
                        pDoar.sendMessage(String.valueOf(ChatColor.GREEN) + "Você doou " + qtdDoar + " vida(s) para " + targetDoar.getName());
                        targetDoar.sendMessage(String.valueOf(ChatColor.GREEN) + "Você recebeu " + qtdDoar + " vida(s) de " + pDoar.getName());
                        return true;
                     }

                     sender.sendMessage(String.valueOf(ChatColor.RED) + "Apenas players podem doar vidas.");
                     return true;
                  case "item":
                     if (sender instanceof Player) {
                        Player pItem = (Player)sender;
                        if (args.length < 2) {
                           pItem.sendMessage(String.valueOf(ChatColor.RED) + "Uso: /vidas item <quantidade>");
                           return true;
                        }

                        int qtdItem;
                        try {
                           qtdItem = Integer.parseInt(args[1]);
                        } catch (NumberFormatException var26) {
                           pItem.sendMessage(String.valueOf(ChatColor.RED) + "Quantidade inválida.");
                           return true;
                        }

                        int myVidaItem = this.getVida(pItem);
                        if (myVidaItem <= 1) {
                           pItem.sendMessage(String.valueOf(ChatColor.RED) + "Você precisa ter mais de 1 vida para converter em item.");
                           return true;
                        }

                        if (myVidaItem - qtdItem < 1) {
                           pItem.sendMessage(String.valueOf(ChatColor.RED) + "Você não pode converter essa quantidade, ficaria com menos de 1 vida.");
                           return true;
                        }

                        for(int i = 0; i < qtdItem; ++i) {
                           ItemStack vidaItem = this.createVidaItem();
                           Map sobraram = pItem.getInventory().addItem(new ItemStack[]{vidaItem});
                           if (!sobraram.isEmpty()) {
                              pItem.getWorld().dropItemNaturally(pItem.getLocation(), vidaItem);
                           }
                        }

                        this.removeVida(pItem, qtdItem);
                        var10001 = String.valueOf(ChatColor.GREEN);
                        pItem.sendMessage(var10001 + "Convertido " + qtdItem + " vidas em itens.");
                        return true;
                     }

                     sender.sendMessage(String.valueOf(ChatColor.RED) + "Apenas players podem usar esse comando.");
                     return true;
                  default:
                     p.sendMessage(String.valueOf(ChatColor.RED) + "Comando inválido ou permissão negada.");
                     return true;
               }
            }
         } else {
            sender.sendMessage(String.valueOf(ChatColor.RED) + "Apenas jogadores podem usar esse comando.");
            return true;
         }
      } else {
         return false;
      }
   }

   public List onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      List resultados = new ArrayList();
      if ("vidas".equalsIgnoreCase(command.getName())) {
         Iterator var7;
         Player p;
         if (args.length == 1) {
            List subcommands = Arrays.asList("set", "give", "revive", "doar", "item", "ver");
            var7 = subcommands.iterator();

            while(var7.hasNext()) {
               String s = (String)var7.next();
               if (s.startsWith(args[0].toLowerCase())) {
                  resultados.add(s);
               }
            }

            var7 = Bukkit.getOnlinePlayers().iterator();

            while(var7.hasNext()) {
               p = (Player)var7.next();
               if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                  resultados.add(p.getName());
               }
            }
         } else if (args.length == 2) {
            String cmd = args[0].toLowerCase();
            if (Arrays.asList("set", "give", "revive", "doar", "ver").contains(cmd)) {
               var7 = Bukkit.getOnlinePlayers().iterator();

               while(var7.hasNext()) {
                  p = (Player)var7.next();
                  if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                     resultados.add(p.getName());
                  }
               }
            }
         }
      }

      return resultados;
   }

   static {
      GUI_TITLE = String.valueOf(ChatColor.DARK_PURPLE) + "Totem da Redenção - Jogadores Banidos";
   }
}
