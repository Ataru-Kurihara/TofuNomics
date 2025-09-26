package org.tofu.tofunomics.trade;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.models.TradeChest;
import org.tofu.tofunomics.models.PlayerTradeHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * チェスト取引管理システム
 */
public class TradeChestManager {
    
    private final Connection connection;
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final Map<String, TradeChest> locationToChestMap;
    
    public TradeChestManager(Connection connection, ConfigManager configManager, PlayerDAO playerDAO) {
        this.connection = connection;
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.locationToChestMap = new HashMap<>();
        loadTradeChests();
    }
    
    /**
     * データベースから全取引チェストを読み込み
     */
    private void loadTradeChests() {
        String sql = "SELECT * FROM trade_chests WHERE active = 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            locationToChestMap.clear();
            
            while (rs.next()) {
                TradeChest chest = createTradeChestFromResultSet(rs);
                String locationKey = createLocationKey(chest.getWorldName(), chest.getX(), chest.getY(), chest.getZ());
                locationToChestMap.put(locationKey, chest);
            }
            
        } catch (SQLException e) {
            System.err.println("取引チェストの読み込みに失敗しました: " + e.getMessage());
        }
    }
    
    /**
     * 取引チェストを作成
     */
    public boolean createTradeChest(Player creator, Location location, String jobType) {
        if (!isValidJobType(jobType)) {
            creator.sendMessage(ChatColor.RED + "無効な職業タイプです: " + jobType);
            return false;
        }
        
        Block block = location.getBlock();
        if (!(block.getState() instanceof Chest)) {
            creator.sendMessage(ChatColor.RED + "指定された場所にチェストがありません。");
            return false;
        }
        
        String locationKey = createLocationKey(location);
        if (locationToChestMap.containsKey(locationKey)) {
            creator.sendMessage(ChatColor.RED + "この場所には既に取引チェストが設置されています。");
            return false;
        }
        
        TradeChest tradeChest = new TradeChest(
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            jobType,
            creator.getUniqueId().toString()
        );
        
        if (saveTradeChest(tradeChest)) {
            locationToChestMap.put(locationKey, tradeChest);
            creator.sendMessage(ChatColor.GREEN + String.format("職業「%s」の取引チェストを設置しました。", 
                configManager.getJobDisplayName(jobType)));
            return true;
        }
        
        return false;
    }
    
    /**
     * 取引チェストをデータベースに保存
     */
    private boolean saveTradeChest(TradeChest tradeChest) {
        String sql = "INSERT INTO trade_chests (world_name, x, y, z, job_type, created_by) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, tradeChest.getWorldName());
            stmt.setInt(2, tradeChest.getX());
            stmt.setInt(3, tradeChest.getY());
            stmt.setInt(4, tradeChest.getZ());
            stmt.setString(5, tradeChest.getJobType());
            stmt.setString(6, tradeChest.getCreatedBy());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        tradeChest.setId(generatedKeys.getInt(1));
                        return true;
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("取引チェストの保存に失敗しました: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 取引チェストを削除
     */
    public boolean removeTradeChest(Player remover, Location location) {
        String locationKey = createLocationKey(location);
        TradeChest tradeChest = locationToChestMap.get(locationKey);
        
        if (tradeChest == null) {
            remover.sendMessage(ChatColor.RED + "この場所には取引チェストがありません。");
            return false;
        }
        
        String sql = "UPDATE trade_chests SET active = 0 WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, tradeChest.getId());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                locationToChestMap.remove(locationKey);
                remover.sendMessage(ChatColor.GREEN + "取引チェストを削除しました。");
                return true;
            }
            
        } catch (SQLException e) {
            System.err.println("取引チェストの削除に失敗しました: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 指定場所の取引チェストを取得
     */
    public TradeChest getTradeChest(Location location) {
        String locationKey = createLocationKey(location);
        return locationToChestMap.get(locationKey);
    }
    
    /**
     * 指定場所が取引チェストかチェック
     */
    public boolean isTradeChest(Location location) {
        return getTradeChest(location) != null;
    }
    
    /**
     * 全ての取引チェストリストを取得
     */
    public List<TradeChest> getAllTradeChests() {
        return new ArrayList<>(locationToChestMap.values());
    }
    
    /**
     * 特定職業の取引チェストリストを取得
     */
    public List<TradeChest> getTradeChestsByJobType(String jobType) {
        List<TradeChest> result = new ArrayList<>();
        for (TradeChest chest : locationToChestMap.values()) {
            if (chest.getJobType().equals(jobType)) {
                result.add(chest);
            }
        }
        return result;
    }
    
    /**
     * プレイヤーの取引履歴を保存
     */
    public boolean saveTradeHistory(PlayerTradeHistory history) {
        String sql = "INSERT INTO player_trade_history " +
                    "(uuid, trade_chest_id, item_type, item_amount, sale_price, job_bonus, player_job, player_job_level) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, history.getUuid());
            stmt.setInt(2, history.getTradeChestId());
            stmt.setString(3, history.getItemType());
            stmt.setInt(4, history.getItemAmount());
            stmt.setDouble(5, history.getSalePrice());
            stmt.setDouble(6, history.getJobBonus());
            stmt.setString(7, history.getPlayerJob());
            stmt.setInt(8, history.getPlayerJobLevel());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        history.setId(generatedKeys.getInt(1));
                        return true;
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("取引履歴の保存に失敗しました: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * プレイヤーの取引履歴を取得
     */
    public List<PlayerTradeHistory> getPlayerTradeHistory(String uuid, int limit) {
        String sql = "SELECT * FROM player_trade_history WHERE uuid = ? ORDER BY traded_at DESC LIMIT ?";
        List<PlayerTradeHistory> histories = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    histories.add(createTradeHistoryFromResultSet(rs));
                }
            }
            
        } catch (SQLException e) {
            System.err.println("取引履歴の取得に失敗しました: " + e.getMessage());
        }
        
        return histories;
    }
    
    /**
     * ユーティリティメソッド群
     */
    
    private TradeChest createTradeChestFromResultSet(ResultSet rs) throws SQLException {
        TradeChest chest = new TradeChest();
        chest.setId(rs.getInt("id"));
        chest.setWorldName(rs.getString("world_name"));
        chest.setX(rs.getInt("x"));
        chest.setY(rs.getInt("y"));
        chest.setZ(rs.getInt("z"));
        chest.setJobType(rs.getString("job_type"));
        chest.setActive(rs.getBoolean("active"));
        chest.setCreatedBy(rs.getString("created_by"));
        chest.setCreatedAt(rs.getTimestamp("created_at"));
        return chest;
    }
    
    private PlayerTradeHistory createTradeHistoryFromResultSet(ResultSet rs) throws SQLException {
        PlayerTradeHistory history = new PlayerTradeHistory();
        history.setId(rs.getInt("id"));
        history.setUuid(rs.getString("uuid"));
        history.setTradeChestId(rs.getInt("trade_chest_id"));
        history.setItemType(rs.getString("item_type"));
        history.setItemAmount(rs.getInt("item_amount"));
        history.setSalePrice(rs.getDouble("sale_price"));
        history.setJobBonus(rs.getDouble("job_bonus"));
        history.setPlayerJob(rs.getString("player_job"));
        history.setPlayerJobLevel(rs.getInt("player_job_level"));
        history.setTradedAt(rs.getTimestamp("traded_at"));
        return history;
    }
    
    private String createLocationKey(Location location) {
        return createLocationKey(location.getWorld().getName(), 
                               location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    
    private String createLocationKey(String world, int x, int y, int z) {
        return String.format("%s:%d:%d:%d", world, x, y, z);
    }
    
    private boolean isValidJobType(String jobType) {
        String[] validJobs = {"miner", "woodcutter", "farmer", "fisherman", 
                             "blacksmith", "alchemist", "enchanter", "architect"};
        
        for (String validJob : validJobs) {
            if (validJob.equalsIgnoreCase(jobType)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * データを再読み込み
     */
    public void reload() {
        loadTradeChests();
    }
}