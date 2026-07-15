package network.azusake.halo.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;

import java.util.Map;
import java.util.UUID;

/**
 * Parses and executes {@code /halo} sub-commands locally when the client is
 * connected to a dedicated server that does <em>not</em> have the mod installed
 * ({@link HaloPhaseTracker.Phase#LOCAL}).
 *
 * <h3>Design</h3>
 * <p>Uses simple {@code split(" ")} string parsing rather than Brigadier.
 * LOCAL phase only supports a subset of the full command tree (~6 sub-commands),
 * and Brigadier would be over-engineering for this scope.  The string-based
 * parser is easy to understand, maintain, and test.</p>
 *
 * <h3>Safety</h3>
 * <ul>
 *   <li>{@code show} and {@code hide} strictly require {@code @s} as the target
 *       — any other selector or player name is rejected.</li>
 *   <li>{@code config} modifies the shared {@link HaloConfig} which is a
 *       client-side rendering concern — safe to change regardless of phase.</li>
 *   <li>{@code list} and {@code dump} read from {@link HaloJsonLoader} which
 *       holds client-local resource-pack definitions.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class HaloLocalCommandHandler {

    private HaloLocalCommandHandler() {
        // utility class
    }

    /**
     * Parse and execute a local halo command.
     *
     * @param command the raw command string without the leading {@code /}
     *                (e.g. {@code "halo show @s halo:ring_default"})
     * @return a feedback message (with {@code §} colour codes) to display to
     *         the player, or {@code null} if this command should not be
     *         intercepted (e.g. the command does not start with {@code halo})
     */
    public static String handle(String command) {
        String trimmed = command.trim();

        // Normalise: collapse multiple spaces
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 0 || !tokens[0].equals("halo")) {
            return null; // not a /halo command — let it pass through
        }

        if (tokens.length == 1) {
            return usage();
        }

        String sub = tokens[1].toLowerCase();

        return switch (sub) {
            case "list"    -> handleList();
            case "dump"    -> handleDump();
            case "show"    -> handleShow(tokens);
            case "hide"    -> handleHide(tokens);
            case "config"  -> handleConfig(tokens);
            case "reload"  -> handleReload();
            case "active"  -> handleActive();
            case "inspect" -> handleInspect(tokens);
            case "save"    -> "§e本地阶段无法持久化光环数据。光环仅在当前会话中可见。";
            case "debug"   -> "§e调试模式仅在服务端有 mod 时可用。";
            default -> null; // unknown sub-command — let the server reject it
        };
    }

    // ------------------------------------------------------------------
    // Sub-command handlers
    // ------------------------------------------------------------------

    private static String handleList() {
        Map<Identifier, HaloDefinition> defs = HaloJsonLoader.getDefinitions();
        if (defs.isEmpty()) {
            return "§e本地没有加载任何光环定义。请将光环定义 JSON 放入资源包并运行 §f/reload§e。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§a本地光环定义 (§f").append(defs.size()).append("§a):");
        for (Identifier id : defs.keySet()) {
            sb.append("\n  §7- §f").append(id);
        }
        return sb.toString();
    }

    private static String handleDump() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return "§e无法获取玩家信息。";
        }

        UUID playerUuid = client.player.getUuid();
        String serverKey = getServerKey(client);

        Map<Identifier, HaloDefinition> defs = HaloJsonLoader.getDefinitions();

        StringBuilder sb = new StringBuilder();
        sb.append("§a=== 本地光环状态 ===");

        // Player's own halo
        if (serverKey != null) {
            var halo = HaloLocalManager.getInstance().getHalo(serverKey, playerUuid);
            sb.append("\n§7你的光环: ");
            if (halo.isPresent()) {
                Identifier defId = halo.get();
                sb.append("§a").append(defId);
                HaloDefinition def = defs.get(defId);
                if (def != null) {
                    sb.append(" §8layers=§7").append(def.model().layers().size())
                        .append(" §8damping=§7").append(def.damping().linearFactor());
                } else {
                    sb.append(" §c(定义未找到 — 资源包可能缺失)");
                }
            } else {
                sb.append("§7(无)");
            }
        } else {
            sb.append("\n§7服务器信息不可用");
        }

        // Loaded definitions summary
        sb.append("\n§7本地已加载定义: §f").append(defs.size()).append("§7 个");
        if (!defs.isEmpty()) {
            for (Identifier id : defs.keySet()) {
                HaloDefinition def = defs.get(id);
                sb.append("\n  §7- §f").append(id)
                    .append(" §8layers=§7").append(def.model().layers().size());
            }
        }

        return sb.toString();
    }

    private static String handleShow(String[] tokens) {
        // Expected: halo show <target> <definition>
        if (tokens.length < 3) {
            return "§e用法: §f/halo show @s <定义ID>\n§7示例: /halo show @s halo:ring_default";
        }

        String target = tokens[2];

        // ---- strict @s check ----
        if (!target.equals("@s")) {
            return "§c本地阶段仅支持对自身使用光环。请使用 §f@s §c作为目标选择器。\n"
                + "§7示例: /halo show @s halo:ring_default";
        }

        if (tokens.length < 4) {
            return "§e请指定光环定义 ID。\n§7示例: /halo show @s halo:ring_default";
        }

        String defStr = tokens[3];

        // Validate definition ID format (must contain ':')
        if (!defStr.contains(":")) {
            return "§c无效的定义 ID: §f" + defStr + "\n§7定义 ID 必须包含命名空间，例如: halo:ring_default";
        }

        Identifier defId;
        try {
            defId = new Identifier(defStr);
        } catch (Exception e) {
            return "§c无效的定义 ID: §f" + defStr;
        }

        // Validate definition exists locally
        if (!HaloJsonLoader.getDefinition(defId).isPresent()) {
            return "§c本地未找到光环定义 §f" + defId + "§c。\n"
                + "§7请确保包含该定义的资源包已加载。使用 §f/halo list §7查看可用定义。";
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return "§e无法获取玩家信息。";
        }

        String serverKey = getServerKey(client);
        if (serverKey == null) {
            return "§e未连接到服务器。";
        }

        HaloLocalManager.getInstance().showHalo(serverKey, client.player.getUuid(), defId);
        // Put into HaloManager too so the render pipeline picks it up.
        HaloManager.getInstance().putClientHalo(client.player.getUuid(), defId);
        return "§a本地光环: 已将 §f" + defId
            + "§a 设置给自己。\n"
            + "§7(仅在当前服务器当前会话中可见)";
    }

    private static String handleHide(String[] tokens) {
        if (tokens.length < 3) {
            return "§e用法: §f/halo hide @s\n§7示例: /halo hide @s";
        }

        String target = tokens[2];

        // ---- strict @s check ----
        if (!target.equals("@s")) {
            return "§c本地阶段仅支持对自身移除光环。请使用 §f@s §c作为目标选择器。\n"
                + "§7示例: /halo hide @s";
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return "§e无法获取玩家信息。";
        }

        String serverKey = getServerKey(client);
        if (serverKey == null) {
            return "§e未连接到服务器。";
        }

        HaloLocalManager.getInstance().hideHalo(serverKey, client.player.getUuid());
        HaloManager.getInstance().removeClientHalo(client.player.getUuid());
        return "§a已移除自己的本地光环。";
    }

    private static String handleConfig(String[] tokens) {
        // Expected: halo config <param> <value>
        if (tokens.length < 4) {
            return "§e用法: §f/halo config <参数> <值>\n"
                + "§7可用参数:\n"
                + "§7  linear-damping <0~1>    线性阻尼\n"
                + "§7  angular-damping <0~1>   角度阻尼\n"
                + "§7  max-linear-distance <n> 最大线性距离\n"
                + "§7  max-angular-degrees <n> 最大角度(度)\n"
                + "§7  scale <0.1~5.0>        缩放\n"
                + "§7提示: 使用 §f/halo reload §7重新加载资源以恢复默认值";
        }

        String param = tokens[2].toLowerCase();
        double value;
        try {
            value = Double.parseDouble(tokens[3]);
        } catch (NumberFormatException e) {
            return "§c无效的数值: §f" + tokens[3];
        }

        HaloConfig config = HaloManager.getInstance().getConfig();

        switch (param) {
            case "linear-damping"      -> config.setLinearDampingFactor(value);
            case "angular-damping"     -> config.setAngularDampingFactor(value);
            case "max-linear-distance" -> config.setMaxLinearDistance(value);
            case "max-angular-degrees" -> config.setMaxAngularDegrees(value);
            case "scale"               -> config.setHaloScale(value);
            default -> {
                return "§c未知参数: §f" + param + "\n"
                    + "§7可用参数: linear-damping, angular-damping, max-linear-distance, max-angular-degrees, scale";
            }
        }

        return "§a已将 §f" + param + "§a 设为 §f" + value + "\n"
            + "§7(会话级临时参数，使用 §f/halo reload §7可恢复默认值)";
    }

    private static String handleReload() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.reloadResources();
        return "§a正在重新加载客户端资源…";
    }

    private static String handleActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return "§e无法获取玩家信息。";
        }

        String serverKey = getServerKey(client);
        if (serverKey == null) {
            return "§e未连接到服务器。";
        }

        var halo = HaloLocalManager.getInstance().getHalo(serverKey, client.player.getUuid());
        if (halo.isPresent()) {
            return "§a当前光环: §f" + halo.get() + "\n"
                + "§7(本地阶段，仅自己可见)";
        } else {
            return "§e你没有设置本地光环。使用 §f/halo show @s <定义ID> §e来设置。";
        }
    }

    private static String handleInspect(String[] tokens) {
        // In LOCAL phase, only the player's own state exists — delegate to active
        return handleActive();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String usage() {
        return "§a=== Halo (本地阶段) ===\n"
            + "§7/halo list              §f查看本地光环定义\n"
            + "§7/halo dump              §f查看自己的光环详细信息\n"
            + "§7/halo show @s <定义>    §f为自己设置光环\n"
            + "§7/halo hide @s           §f移除自己的光环\n"
            + "§7/halo config <参数> <值> §f调整渲染/物理参数\n"
            + "§7/halo reload            §f重新加载客户端资源\n"
            + "§7/halo active            §f查看自己的光环状态\n"
            + "§7提示: 服务器未安装 Halo mod，光环仅在本地可见。";
    }

    /**
     * Derive a server key from the current connection.
     *
     * @return {@code "host:port"} string, or {@code null} if not connected
     */
    static String getServerKey(MinecraftClient client) {
        if (client.getNetworkHandler() != null && client.getNetworkHandler().getConnection() != null) {
            return HaloLocalManager.serverKeyFromAddress(
                client.getNetworkHandler().getConnection().getAddress());
        }
        return null;
    }
}
