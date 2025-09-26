package org.tofu.tofunomics.skills;

/**
 * 職業別スキルタイプの定義
 */
public enum SkillType {
    
    // 採掘系スキル (Miner)
    MINING_EFFICIENCY("採掘効率", "採掘速度が向上"),
    ORE_FORTUNE("鉱石幸運", "レア鉱石の発見確率向上"),
    MINING_STAMINA("採掘持久力", "ツールの耐久値消費軽減"),
    
    // 伐採系スキル (Woodcutter) 
    LOGGING_EFFICIENCY("伐採効率", "伐採速度が向上"),
    TREE_FELLING("一括伐採", "木を一度に伐採"),
    LOG_FORTUNE("木材幸運", "ログドロップ量増加"),
    
    // 農業系スキル (Farmer)
    CROP_GROWTH("作物成長", "作物の成長速度向上"),
    HARVEST_FORTUNE("収穫幸運", "作物の収穫量増加"),
    ANIMAL_BREEDING("畜産効率", "動物の繁殖効率向上"),
    
    // 釣り系スキル (Fisherman)
    FISHING_LUCK("釣り幸運", "レアアイテムの釣り確率向上"),
    FISHING_SPEED("釣り速度", "魚が釣れるまでの時間短縮"),
    TREASURE_HUNTER("宝探し", "宝物の発見確率大幅向上"),
    
    // 鍛冶系スキル (Blacksmith)
    CRAFTING_EFFICIENCY("製作効率", "製作時間短縮"),
    CRAFTING_MATERIAL_EFFICIENCY("材料効率", "製作時の材料節約"),
    QUALITY_CRAFTING("高品質製作", "高品質アイテムの製作確率向上"),
    
    // 錬金術系スキル (Alchemist)
    POTION_EFFICIENCY("ポーション効率", "ポーション製作時間短縮"),
    EFFECT_DURATION("効果延長", "ポーション効果時間延長"),
    INGREDIENT_EFFICIENCY("材料効率", "ポーション材料の節約"),
    
    // エンチャント系スキル (Enchanter)
    ENCHANT_EFFICIENCY("エンチャント効率", "エンチャント成功確率向上"),
    LEVEL_BOOST("レベルブースト", "エンチャントレベル向上"),
    XP_EFFICIENCY("経験値効率", "エンチャント時の経験値消費軽減"),
    
    // 建築系スキル (Architect)
    BUILD_SPEED("建築速度", "ブロック設置速度向上"),
    BUILD_MATERIAL_EFFICIENCY("材料効率", "建築時の材料節約"),
    BLUEPRINT_SYSTEM("設計図システム", "建築設計図の作成・使用");
    
    private final String displayName;
    private final String description;
    
    SkillType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 職業名からそのスキル群を取得
     */
    public static SkillType[] getSkillsByJob(String jobName) {
        switch (jobName.toLowerCase()) {
            case "miner":
                return new SkillType[]{MINING_EFFICIENCY, ORE_FORTUNE, MINING_STAMINA};
            case "woodcutter":
                return new SkillType[]{LOGGING_EFFICIENCY, TREE_FELLING, LOG_FORTUNE};
            case "farmer":
                return new SkillType[]{CROP_GROWTH, HARVEST_FORTUNE, ANIMAL_BREEDING};
            case "fisherman":
                return new SkillType[]{FISHING_LUCK, FISHING_SPEED, TREASURE_HUNTER};
            case "blacksmith":
                return new SkillType[]{CRAFTING_EFFICIENCY, CRAFTING_MATERIAL_EFFICIENCY, QUALITY_CRAFTING};
            case "alchemist":
                return new SkillType[]{POTION_EFFICIENCY, EFFECT_DURATION, INGREDIENT_EFFICIENCY};
            case "enchanter":
                return new SkillType[]{ENCHANT_EFFICIENCY, LEVEL_BOOST, XP_EFFICIENCY};
            case "architect":
                return new SkillType[]{BUILD_SPEED, BUILD_MATERIAL_EFFICIENCY, BLUEPRINT_SYSTEM};
            default:
                return new SkillType[0];
        }
    }
    
    /**
     * スキルが指定された職業に属するかチェック
     */
    public boolean belongsToJob(String jobName) {
        SkillType[] jobSkills = getSkillsByJob(jobName);
        for (SkillType skill : jobSkills) {
            if (skill == this) {
                return true;
            }
        }
        return false;
    }
}