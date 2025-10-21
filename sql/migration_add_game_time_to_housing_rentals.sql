-- 賃貸システムをゲーム内時間対応にするためのマイグレーション (SQLite版)
-- 実行日: 2025-10-10

-- housing_rentalsテーブルにゲーム内時間（tick数）のカラムを追加
-- SQLiteではALTER TABLEで複数カラムを同時に追加できないため、個別に実行
ALTER TABLE housing_rentals ADD COLUMN start_tick INTEGER DEFAULT 0;
ALTER TABLE housing_rentals ADD COLUMN end_tick INTEGER DEFAULT 0;

-- 既存データの移行は不要（新規契約から適用）
-- 既存の契約は現実時間ベースのstart_date/end_dateで継続

-- インデックスを追加（期限切れ検索の高速化）
CREATE INDEX IF NOT EXISTS idx_housing_rentals_end_tick ON housing_rentals(end_tick);
CREATE INDEX IF NOT EXISTS idx_housing_rentals_status_end_tick ON housing_rentals(status, end_tick);
