package org.tofu.tofunomics.tools;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 職業別ツールセットクラス
 */
public class JobToolSet {
    
    private final Map<Integer, ItemStack> levelTools;
    
    public JobToolSet() {
        this.levelTools = new HashMap<>();
    }
    
    /**
     * 指定レベルでアンロックされるツールを追加
     */
    public JobToolSet addTool(int requiredLevel, ItemStack tool) {
        levelTools.put(requiredLevel, tool);
        return this;
    }
    
    /**
     * 指定レベルで獲得できるツールを取得
     */
    public ItemStack getToolForLevel(int level) {
        return levelTools.get(level);
    }
    
    /**
     * 指定レベルでツールがアンロックされるかチェック
     */
    public boolean hasToolForLevel(int level) {
        return levelTools.containsKey(level);
    }
    
    /**
     * 指定された最大レベルまでの利用可能ツール一覧を取得
     */
    public List<ItemStack> getAvailableTools(int maxLevel) {
        List<ItemStack> availableTools = new ArrayList<>();
        
        for (Map.Entry<Integer, ItemStack> entry : levelTools.entrySet()) {
            if (entry.getKey() <= maxLevel) {
                availableTools.add(entry.getValue().clone());
            }
        }
        
        return availableTools;
    }
    
    /**
     * 全てのツール解放レベルを取得
     */
    public List<Integer> getUnlockLevels() {
        return new ArrayList<>(levelTools.keySet());
    }
    
    /**
     * 次にアンロックされるツールのレベルを取得
     */
    public int getNextUnlockLevel(int currentLevel) {
        int nextLevel = Integer.MAX_VALUE;
        
        for (int level : levelTools.keySet()) {
            if (level > currentLevel && level < nextLevel) {
                nextLevel = level;
            }
        }
        
        return nextLevel == Integer.MAX_VALUE ? -1 : nextLevel;
    }
}