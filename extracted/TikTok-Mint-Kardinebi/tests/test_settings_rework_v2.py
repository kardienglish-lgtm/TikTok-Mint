"""Regression checks for the requested Mint settings redesign."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"

class SettingsReworkV2Test(unittest.TestCase):
    def read(self, name):
        return (JAVA / name).read_text(encoding="utf-8")

    def test_mint_row_routes_to_user_telegram_and_payment_prompts_are_absent(self):
        settings = self.read("SettingsActivity.java")
        text = self.read("Text.java")
        self.assertIn('"TikTok Mint", "Открыть Telegram-канал · @TikTokmintt", () -> open(TELEGRAM)', settings)
        self.assertIn('private static final String TELEGRAM = "https://t.me/TikTokmintt"', settings)
        self.assertNotIn('YOOMONEY', text.upper())
        self.assertNotIn('DONATE_TITLE', text.upper())
        self.assertNotIn('CARD_NUMBER', text.upper())

    def test_font_dialog_is_modal_draft_and_saves_for_restart(self):
        settings = self.read("SettingsActivity.java")
        fonts = self.read("Fonts.java")
        self.assertIn('new SheetDialog(this, true)', settings)
        self.assertIn('Я люблю TikTok Mint', settings)
        self.assertIn('randomEmojiPreview()', settings)
        self.assertIn('shuffled.get(0) + " " + shuffled.get(1) + " " + shuffled.get(2)', settings)
        self.assertIn('Fonts.saveForRestart(SettingsActivity.this, letters[0], emoji[0], weight[0])', settings)
        method = fonts[fonts.index('public static void saveForRestart'):fonts.index('public static void choose(String value)')]
        self.assertIn('putString(KEY_EMOJI', method)
        self.assertNotIn('chosen =', method)
        self.assertNotIn('chosenEmoji =', method)

    def test_uploaded_fonts_are_staged_until_the_modal_save_action(self):
        fonts = self.read("Fonts.java")
        settings = self.read("SettingsActivity.java")
        take = fonts[fonts.index('public static boolean take(Context context, android.net.Uri uri, boolean forEmoji)'):fonts.index('static File emojiFile')]
        self.assertIn('temporary.renameTo(out)', take)
        self.assertNotIn('putString(KEY', take)
        self.assertIn('pendingFontSelection = Fonts.FILE', settings)
        self.assertIn('pendingEmojiSelection = Fonts.EMOJI_FILE', settings)
        self.assertIn('Fonts.saveForRestart(SettingsActivity.this, letters[0], emoji[0], weight[0])', settings)

    def test_expressive_weight_slider_and_emoji_updates_have_runtime_hooks(self):
        slider = self.read("ExpressiveWeightSlider.java")
        fonts = self.read("Fonts.java")
        self.assertIn('new OvershootInterpolator', slider)
        self.assertIn('100, Math.min(900', slider)
        self.assertIn('public static void chooseEmoji', fonts)
        emoji_method = fonts[fonts.index('public static void chooseEmoji'):fonts.index('private static void forget')]
        self.assertIn('Themes::repaintVisible', emoji_method)

    def test_region_picker_uses_searchable_two_column_tiles(self):
        settings = self.read("SettingsActivity.java")
        region = settings[settings.index('private View regionPickerContent()'):settings.index('private View iconHead()')]
        self.assertIn('Поиск страны или оператора', region)
        self.assertIn('regionCountryTile(country)', region)
        self.assertIn('(shown % 2) == 0', region)
        self.assertIn('🇩🇪 Германия', region)
        self.assertIn('АКТИВЕН', region)

    def test_nested_setting_controls_open_dialogs_instead_of_inline_expansion(self):
        settings = self.read("SettingsActivity.java")
        self.assertIn('private void showSettingsDialog(String title, View content)', settings)
        self.assertIn('showSettingsDialog("Регион", regionPickerContent())', settings)
        self.assertIn('showSettingsDialog("Цвет акцента", accentSettingsContent())', settings)
        self.assertIn('showSettingsDialog(Text.THEME_COLOURS, themeColoursContent())', settings)
        self.assertIn('showSettingsDialog(Text.ICON, iconChoices())', settings)
        self.assertIn('showMineDialog();', settings)
        self.assertIn('showStickerDialog();', settings)
        self.assertIn('showSettingsDialog(Text.DIARY_TITLE, diaryLines())', settings)
        self.assertNotIn('if (iconOpen)', settings)
        self.assertNotIn('if (mineOpen) badges.addView(appear(mineRows()))', settings)
        self.assertNotIn('if (themeColoursOpen)', settings)
        self.assertNotIn('appear(stickerChoices())', settings)

    def test_custom_texture_cache_retries_a_transient_decode_failure(self):
        md3 = self.read("Md3.java")
        block = md3[md3.index('private static boolean patchNativeDrawable'):md3.index('/**\n     * TikTok 47.4.42')]
        self.assertIn('retryAt', block)
        self.assertIn('SystemClock.uptimeMillis() < retryAt', block)
        self.assertIn('configured.equals(((Texture) current).uri)', block)

    def test_streak_template_can_be_copied_without_fake_automatic_sender(self):
        settings = self.read("SettingsActivity.java")
        streaks = self.read("Streaks.java")
        self.assertIn('"Скопировать заготовку"', settings)
        self.assertIn('private static boolean sendText(Context context, String conversation, String words)', streaks)
        self.assertIn('Diary.note("streak text: not available in this build")', streaks)

    def test_sketchpad_class_is_removed(self):
        self.assertFalse((JAVA / "SketchPad.java").exists())
        self.assertNotIn('SketchPad.open(this, skin)', self.read("SettingsActivity.java"))

if __name__ == "__main__":
    unittest.main()
