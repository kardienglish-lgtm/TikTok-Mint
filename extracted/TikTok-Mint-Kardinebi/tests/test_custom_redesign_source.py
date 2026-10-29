"""Regression checks for the Custom 1.1.3 Material 3 redesign."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'inject/java/cat/kardinebi'

class CustomRedesignSourceTest(unittest.TestCase):
    def test_navigation_has_new_expressive_surface_and_requested_labels(self):
        source = (JAVA / 'NavBar.java').read_text(encoding='utf-8')
        self.assertIn('private static String requestedLabel(int kind)', source)
        for label in ('"Дом"', '"Друзья"', '"Сообщения"', '"Вы"'):
            self.assertIn(label, source)
        self.assertIn('Expressive.shape(expressive, flourish', source)
        self.assertIn('private void create(Canvas canvas', source)
        self.assertIn('targetView.performClick()', source)
        self.assertIn('SWIPE_THRESHOLD_DP', source)

    def test_profile_pages_are_not_filtered_as_recommendations(self):
        feed = (JAVA / 'Feed.java').read_text(encoding='utf-8')
        profile = (JAVA / 'ProfileLayout.java').read_text(encoding='utf-8')
        self.assertIn('if (items == null || profileScope()) return items;', feed)
        self.assertIn('!isEnabled() || profileScope()', feed)
        self.assertIn('static boolean currentProfileVisible()', profile)
        self.assertIn('profilePagers.add(view)', profile)

    def test_custom_texture_enables_icon_replacement(self):
        source = (JAVA / 'Md3.java').read_text(encoding='utf-8')
        method = source[source.index('static boolean setTexture('):source.index('static void clearTexture(')]
        self.assertIn('.putBoolean(KEY_ICONS, true)', method)
        self.assertIn('ON[ICONS] = true', method)


    def test_search_and_share_redesign_is_wired_into_activity_layouts(self):
        themes = (JAVA / 'Themes.java').read_text(encoding='utf-8')
        expressive = (JAVA / 'MintScreenDesign.java').read_text(encoding='utf-8')
        self.assertGreaterEqual(themes.count('MintScreenDesign.apply(root)'), 3)
        for token in ('SEARCH_FIELD', 'SEARCH_CHIP', 'SEARCH_RESULT', 'SHARE_SURFACE',
                      'SHARE_ACTION', 'COMMENT_SURFACE', 'COMMENT_INPUT', 'ACCOUNT_TAB'):
            self.assertIn(token, expressive)
        self.assertIn('shape.setColor(fill)', expressive)
        self.assertIn('shape.setCornerRadii', expressive)
        self.assertNotIn('shape.setColors(new int[]{light, fill', expressive,
                         'surfaces should be solid-tonal, not decorated with gradients')
        self.assertIn('view.getBackground() == snapshot.lastPaintedBackground', expressive)
        self.assertIn('SIG_COMMENT_INPUT', expressive)
        self.assertNotIn('setOnClickListener(', expressive, 'native search/share handlers must stay intact')

    def test_publication_date_avoids_the_old_obfuscated_resource_and_matches_more_date_forms(self):
        overlay = (JAVA / 'DateOverlay.java').read_text(encoding='utf-8')
        self.assertIn('name.equals("yau")', overlay)
        self.assertIn('private static boolean looksLikeDate(CharSequence value)', overlay)
        self.assertIn(r'\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}', overlay)
        patcher = (ROOT / 'kardinebi/dexpatch.py').read_text(encoding='utf-8')
        self.assertIn('Dates;->bindAuthor(Ljava/lang/Object;)V', patcher)
        self.assertIn('Dates;->bindPost(Ljava/lang/Object;Ljava/lang/Object;)V', patcher)
        self.assertIn('pattern = r"(?ms)^\\.method [^\\n]*\\ba2', patcher)

    def test_required_upstream_notice_remains_visible_and_named(self):
        source = (JAVA / 'SettingsActivity.java').read_text(encoding='utf-8')
        self.assertGreaterEqual(source.count('section("Источник компонентов и лицензия")'), 1)
        self.assertIn('Источник компонентов · TikTok Mint', source)
        self.assertIn('сопровождение @kardinebi', source)
        self.assertIn('Форк TikTok Mint · сопровождение @kardinebi', source)

    def test_redundant_upstream_promos_are_removed_but_notice_is_kept(self):
        source = (JAVA / 'SettingsActivity.java').read_text(encoding='utf-8')
        self.assertNotIn('maybeAskForSupport()', source)
        self.assertNotIn('showDonations()', source)
        self.assertNotIn('donateBanner()', source)
        self.assertNotIn('@margeletforum', source)
        self.assertIn('Источник компонентов · TikTok Mint', source)
        self.assertIn('Форк TikTok Mint · сопровождение @kardinebi', source)

if __name__ == '__main__':
    unittest.main()
